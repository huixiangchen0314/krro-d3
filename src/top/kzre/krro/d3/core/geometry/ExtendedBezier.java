package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.geometry.algo.BezierArcLength;
import top.kzre.krro.d3.core.geometry.algo.BezierBoundsScan;
import top.kzre.krro.d3.core.geometry.algo.BezierSample;
import top.kzre.krro.d3.core.geometry.algo.BezierTangents;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.DefaultCopyOnWriteFloats;
import top.kzre.krro.util.arena.AllocateResult;
import top.kzre.krro.util.arena.AutoGrowFloatArrayArenaTemplate;
import top.kzre.krro.util.arena.FloatArenaView;

import java.nio.FloatBuffer;

/**
 * 扩展贝塞尔曲线——拥有 {@link Bezier} 所有权 + 管理派生数据缓存。
 *
 * <p><b>角色</b>：消费者面向这个类。
 * <ul>
 *   <li>代理 {@link Bezier} 的本质数据读写</li>
 *   <li>管理派生数据的按需计算与缓存</li>
 * </ul>
 *
 * <p><b>派生数据</b>（全部走 COW + Arena）：
 * <pre>
 *   samplePositions  float3[n]         ← BezierSample
 *   sampleTangents   float3[n]         ← BezierTangents
 *   bounds           float[6]          ← BezierBoundsScan
 *   arcLengths       float[n+1]        ← BezierArcLength
 * </pre>
 *
 * <p><b>算法层</b>：{@code top.kzre.krro.d3.core.geometry.algo} 包——
 * 纯函数——不感知缓存。本类负责缓存 + 失效。
 *
 * <p><b>失效规则</b>：
 * <pre>
 *   pointsForWrite()       → 全部派生
 *   handlesForWrite()      → 全部派生
 *   continuitiesForWrite() → 全部派生
 *   setSampleResolution()  → 全部派生
 *   closed 变化            → 通过新实例——缓存自然清空
 * </pre>
 *
 * <p><b>采样分辨率</b>：每段曲线的采样点数。影响派生数据规模。
 * 来自编辑器配置——不是硬编码默认。
 *
 * <p><b>线程契约</b>：单 ExtendedBezier 实例 = 单线程。
 * 多线程——每个线程通过 {@link #shared()} 获取副本。
 */
public final class ExtendedBezier implements CopyOnWrite<ExtendedBezier> {

    private final Bezier curve;
    private final AutoGrowFloatArrayArenaTemplate floatTemplate;

    /**
     * 每段曲线的采样点数——用于所有派生计算。
     */
    private int sampleResolution;

    /** 采样点位置缓存。float3[n]。null 表示未计算。 */
    private CopyOnWriteFloats cachedSamplePositions;

    /** 采样点切线缓存。float3[n]。null 表示未计算。 */
    private CopyOnWriteFloats cachedSampleTangents;

    /** 包围盒缓存。float[6]。null 表示未计算。 */
    private CopyOnWriteFloats cachedBounds;

    /** 累计弧长缓存。float[n+1]。null 表示未计算。 */
    private CopyOnWriteFloats cachedArcLengths;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public ExtendedBezier(
            Bezier                          curve,
            AutoGrowFloatArrayArenaTemplate floatTemplate,
            int                             sampleResolution) {
        if (curve == null)         throw new NullPointerException("curve");
        if (floatTemplate == null) throw new NullPointerException("floatTemplate");
        if (sampleResolution <= 0) throw new IllegalArgumentException("sampleResolution must be > 0");

        this.curve            = curve;
        this.floatTemplate    = floatTemplate;
        this.sampleResolution = sampleResolution;
    }

    // ═══════════════════════════════════════════════
    // 配置——采样分辨率
    // ═══════════════════════════════════════════════

    public int getSampleResolution() {
        return sampleResolution;
    }

    /**
     * 设置采样分辨率。
     *
     * <p><b>副作用</b>：丢弃全部派生缓存。
     */
    public void setSampleResolution(int sampleResolution) {
        if (sampleResolution <= 0) throw new IllegalArgumentException("sampleResolution must be > 0");
        if (this.sampleResolution == sampleResolution) return;
        this.sampleResolution = sampleResolution;
        discardAll();
    }

    // ═══════════════════════════════════════════════
    // Bezier 访问——本质数据——读
    // ═══════════════════════════════════════════════

    public Bezier sharedCurve() { return curve.shared(); }

    public float[]     getPointsSnapshot()      { return curve.points().getFloatsSnapshot(); }
    public FloatBuffer getPointBufferSnapshot() { return curve.points().getFloatBufferSnapshot(); }

    public float[]     getHandlesSnapshot()      { return curve.handles().getFloatsSnapshot(); }
    public FloatBuffer getHandleBufferSnapshot() { return curve.handles().getFloatBufferSnapshot(); }

    public int[]     getContinuitiesSnapshot()      { return curve.continuities().getIntsSnapshot(); }
    public boolean   isClosed()                     { return curve.closed(); }

    public int controlPointCount() {
        return curve.points().getFloatsSnapshot().length / 3;
    }

    // ═══════════════════════════════════════════════
    // Bezier 访问——本质数据——写
    // ═══════════════════════════════════════════════

    /**
     * 声明写控制点。
     *
     * <p><b>副作用</b>：丢弃全部派生。
     */
    public FloatBuffer pointsForWrite() {
        discardAll();
        return curve.points().getFloatBufferForWrite();
    }

    /**
     * 声明写手柄。
     *
     * <p><b>副作用</b>：丢弃全部派生。
     */
    public FloatBuffer handlesForWrite() {
        discardAll();
        return curve.handles().getFloatBufferForWrite();
    }

    /**
     * 声明写连续性约束。
     *
     * <p><b>副作用</b>：丢弃全部派生。
     */
    public java.nio.IntBuffer continuitiesForWrite() {
        discardAll();
        return curve.continuities().getIntBufferForWrite();
    }

    // ═══════════════════════════════════════════════
    // 派生数据——读
    // ═══════════════════════════════════════════════

    /**
     * 采样点位置。float3[n]。
     *
     * <p>依赖：points + handles + continuities + closed + sampleResolution。
     */
    public float[] getSamplePositions() {
        CopyOnWriteFloats c = cachedSamplePositions;
        if (c != null) return c.getFloatsSnapshot();

        float[] p = BezierSample.compute(curve, sampleResolution);
        cachedSamplePositions = allocateFloats(p);
        return p;
    }

    /**
     * 采样点切线。float3[n]。
     *
     * <p>依赖：points + handles + continuities + closed + sampleResolution。
     */
    public float[] getSampleTangents() {
        CopyOnWriteFloats c = cachedSampleTangents;
        if (c != null) return c.getFloatsSnapshot();

        float[] t = BezierTangents.compute(curve, sampleResolution);
        cachedSampleTangents = allocateFloats(t);
        return t;
    }

    /**
     * 包围盒。float[6] = [minX, minY, minZ, maxX, maxY, maxZ]。
     *
     * <p>依赖：points + handles + closed（用控制点 + 手柄的保守包围盒）。
     */
    public float[] getBounds() {
        CopyOnWriteFloats c = cachedBounds;
        if (c != null) return c.getFloatsSnapshot();

        float[] b = BezierBoundsScan.compute(curve);
        cachedBounds = allocateFloats(b);
        return b;
    }

    /**
     * 累计弧长表。float[n+1]——第 i 项是前 i 段的总弧长。
     *
     * <p>依赖：points + handles + continuities + closed + sampleResolution。
     *
     * <p>用途：按弧长均匀采样——路径动画。
     */
    public float[] getArcLengths() {
        CopyOnWriteFloats c = cachedArcLengths;
        if (c != null) return c.getFloatsSnapshot();

        float[] a = BezierArcLength.compute(curve, sampleResolution);
        cachedArcLengths = allocateFloats(a);
        return a;
    }

    // ═══════════════════════════════════════════════
    // 分配——走 AutoGrow Template
    // ═══════════════════════════════════════════════

    private CopyOnWriteFloats allocateFloats(float[] data) {
        int byteSize = data.length * Float.BYTES;

        AllocateResult<FloatArenaView> r = floatTemplate.allocate(byteSize);

        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow float allocation failed unexpectedly: "
                            + byteSize + " bytes");
        }

        FloatArenaView view = r.getView();
        view.floatBuffer().put(data);
        return new DefaultCopyOnWriteFloats(view);
    }

    // ═══════════════════════════════════════════════
    // 丢弃派生
    // ═══════════════════════════════════════════════

    private void discardSamplePositions() {
        if (cachedSamplePositions != null) {
            try {
                cachedSamplePositions.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedSamplePositions = null; }
    }

    private void discardSampleTangents() {
        if (cachedSampleTangents != null) {
            try {
                cachedSampleTangents.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedSampleTangents = null; }
    }

    private void discardBounds() {
        if (cachedBounds != null) {
            try {
                cachedBounds.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedBounds = null; }
    }

    private void discardArcLengths() {
        if (cachedArcLengths != null) {
            try {
                cachedArcLengths.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedArcLengths = null; }
    }

    private void discardAll() {
        discardSamplePositions();
        discardSampleTangents();
        discardBounds();
        discardArcLengths();
    }

    // ═══════════════════════════════════════════════
    // 共享
    // ═══════════════════════════════════════════════

    @Override
    public ExtendedBezier shared() {
        ExtendedBezier b = new ExtendedBezier(
                curve.shared(), floatTemplate, sampleResolution);

        b.cachedSamplePositions = cachedSamplePositions == null ? null : cachedSamplePositions.shared();
        b.cachedSampleTangents  = cachedSampleTangents  == null ? null : cachedSampleTangents.shared();
        b.cachedBounds          = cachedBounds          == null ? null : cachedBounds.shared();
        b.cachedArcLengths      = cachedArcLengths      == null ? null : cachedArcLengths.shared();

        return b;
    }

    // ═══════════════════════════════════════════════
    // 关闭
    // ═══════════════════════════════════════════════

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(
                cachedSamplePositions, cachedSampleTangents,
                cachedBounds, cachedArcLengths,
                curve);
        if (ex != null) throw ex;
    }
}