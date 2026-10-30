package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.geometry.algo.AngleClusteredNormals;
import top.kzre.krro.d3.core.geometry.algo.CornerEdgeLookup;
import top.kzre.krro.d3.core.geometry.algo.FaceAabbScan;
import top.kzre.krro.d3.core.geometry.algo.MikkTSpaceTangents;
import top.kzre.krro.d3.core.geometry.algo.MinMaxScan;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.*;
import top.kzre.krro.util.arena.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * 扩展网格——拥有 {@link Mesh} 所有权 + 管理派生数据缓存。
 *
 * <p><b>角色</b>：消费者面向这个类。
 * <ul>
 *   <li>代理 {@link Mesh} 的本质数据读写</li>
 *   <li>管理派生数据的按需计算与缓存</li>
 * </ul>
 *
 * <p><b>派生数据</b>（全部走 COW + Arena）：
 * <pre>
 *   cornerNormals   float3[ncorners]  ← AngleClusteredNormals
 *   edges           int2[nedges]      ← HashDedupEdges
 *   cornerEdges     int[ncorners]     ← CornerEdgeLookup
 *   tangents        float4[ncorners]  ← MikkTSpaceTangents
 *   bounds          float[6]          ← MinMaxScan
 *   faceAabbs       float6[nfaces]    ← FaceAabbScan
 * </pre>
 *
 * <p><b>算法层</b>：{@code top.kzre.krro.d3.core.geometry.algo} 包——
 * 纯函数——不感知缓存。本类负责缓存 + 失效。
 *
 * <p><b>失效规则</b>：
 * <pre>
 *   positionForWrite()     → cornerNormals / tangents / bounds / faceAabbs
 *   cornerUvForWrite()     → tangents
 *   cornerVertsForWrite()  → 全部派生
 *   faceOffsetsForWrite()  → 全部派生
 *   setSmoothAngle()       → cornerNormals / tangents
 * </pre>
 *
 * <p><b>分配——Storage 容量要求</b>：见类文档"Storage 容量要求"。
 * AutoGrow 只扩一次——仍失败则抛 {@link IllegalStateException}。
 *
 * <p><b>线程契约</b>：单 ExtendedMesh 实例 = 单线程。
 * 多线程——每个线程通过 {@link #shared()} 获取副本。
 */
public final class ExtendedMesh implements CopyOnWrite<ExtendedMesh> {

    // ═══════════════════════════════════════════════
    // 字段
    // ═══════════════════════════════════════════════

    private final Mesh mesh;
    private final ArenaTemplate<?, FloatArenaView> floatTemplate;
    private final ArenaTemplate<?, IntArenaView> intTemplate;

    /**
     * 平滑角度阈值——弧度——用于面角法线计算。
     *
     * <p>来自编辑器配置——不是硬编码默认。
     */
    private float smoothAngleRadians;

    /**
     * 面角法线缓存。float3[ncorners]。null 表示未计算。
     */
    private CopyOnWriteFloats cachedCornerNormals;


    /**
     * 面角 → 边映射缓存。int[ncorners]。null 表示未计算。
     */
    private CopyOnWriteInts cachedCornerEdges;

    /**
     * 切线缓存。float4[ncorners]。null 表示未计算。
     */
    private CopyOnWriteFloats cachedTangents;

    /**
     * 包围盒缓存。float[6]。null 表示未计算。
     */
    private CopyOnWriteFloats cachedBounds;

    /**
     * 图元 AABB 缓存。float6[nfaces]。null 表示未计算。
     */
    private CopyOnWriteFloats cachedFaceAabbs;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public ExtendedMesh(
            Mesh mesh,
            ArenaTemplate<?, FloatArenaView> floatTemplate,
            ArenaTemplate<?, IntArenaView> intTemplate,
            float smoothAngleRadians) {
        if (mesh == null) throw new NullPointerException("mesh");
        if (floatTemplate == null) throw new NullPointerException("floatTemplate");
        if (intTemplate == null) throw new NullPointerException("intTemplate");

        this.mesh = mesh;
        this.floatTemplate = floatTemplate;
        this.intTemplate = intTemplate;
        this.smoothAngleRadians = smoothAngleRadians;
    }

    // ═══════════════════════════════════════════════
    // 配置——平滑角度
    // ═══════════════════════════════════════════════

    public float getSmoothAngle() {
        return smoothAngleRadians;
    }

    /**
     * 设置平滑角度阈值——弧度。
     *
     * <p><b>副作用</b>：丢弃 cornerNormals / tangents 缓存。
     */
    public void setSmoothAngle(float smoothAngleRadians) {
        if (this.smoothAngleRadians == smoothAngleRadians) return;
        this.smoothAngleRadians = smoothAngleRadians;
        discardCornerNormals();
        discardTangents();
    }

    // ═══════════════════════════════════════════════
    // Mesh 访问
    // ═══════════════════════════════════════════════

    /**
     * 直接暴露，不需要share
     */
    public Mesh mesh(){
        return mesh;
    }

    public long vertCount() {
        return mesh.vertCount();
    }

    public long cornerCount() {
        return mesh.cornerCount();
    }

    public long faceCount() {
        return mesh.faceCount();
    }

    // ═══════════════════════════════════════════════
    // 本质数据——读
    // ═══════════════════════════════════════════════
    public CopyOnWriteFloats positions() {
        return mesh.positions();
    }

    public float[] getPositionsSnapshot() {
        return mesh.positions().getFloatsSnapshot();
    }

    public FloatBuffer getPositionBufferSnapshot() {
        return mesh.positions().getFloatBufferSnapshot();
    }

    public int[] getCornerVertsSnapshot() {
        return mesh.cornerVerts().getIntsSnapshot();
    }

    public IntBuffer getCornerVertBufferSnapshot() {
        return mesh.cornerVerts().getIntBufferSnapshot();
    }

    public int[] getFaceOffsetsSnapshot() {
        return mesh.faceOffsets().getIntsSnapshot();
    }

    public IntBuffer getFaceOffsetBufferSnapshot() {
        return mesh.faceOffsets().getIntBufferSnapshot();
    }


    // ═══════════════════════════════════════════════
    // 本质数据——写
    // ═══════════════════════════════════════════════

    /**
     * 声明写位置。
     *
     * <p><b>副作用</b>：丢弃 cornerNormals / tangents / bounds / faceAabbs。
     */
    public FloatBuffer positionForWrite() {
        discardCornerNormals();
        discardTangents();
        discardBounds();
        discardFaceAabbs();
        return mesh.positions().getFloatBufferForWrite();
    }

    /**
     * 声明写面角 → 顶点——拓扑变化——丢弃全部派生。
     */
    public IntBuffer cornerVertsForWrite() {
        discardAll();
        return mesh.cornerVerts().getIntBufferForWrite();
    }

    /**
     * 声明写面 → 角范围偏移——拓扑变化——丢弃全部派生。
     */
    public IntBuffer faceOffsetsForWrite() {
        discardAll();
        return mesh.faceOffsets().getIntBufferForWrite();
    }


    // ═══════════════════════════════════════════════
    // 派生数据——读
    // ═══════════════════════════════════════════════

    /**
     * 面角法线。float3[ncorners]。
     *
     * <p>依赖：positions + topology + smoothAngleRadians。
     */
    public float[] getCornerNormals() {
        CopyOnWriteFloats c = cachedCornerNormals;
        if (c != null) return c.getFloatsSnapshot();

        float[] n = AngleClusteredNormals.compute(mesh, smoothAngleRadians);
        cachedCornerNormals = allocateFloats(n);
        return n;
    }


    /**
     * 面角 → 边映射。int[ncorners]。
     *
     * <p>依赖：topology。内部会确保 edges 已计算。
     */
    public int[] getCornerEdges() {
        CopyOnWriteInts c = cachedCornerEdges;
        if (c != null) return c.getIntsSnapshot();

        int[] edges = null;
        int[] ce = CornerEdgeLookup.compute(mesh, edges);
        cachedCornerEdges = allocateInts(ce);
        return ce;
    }

    /**
     * 切线。float4[ncorners]——xyz + 手性 w。
     *
     * <p>依赖：positions + cornerUvs + topology + cornerNormals。
     */
    public float[] getTangents() {
        CopyOnWriteFloats c = cachedTangents;
        if (c != null) return c.getFloatsSnapshot();

        float[] t = MikkTSpaceTangents.compute(mesh, getCornerNormals());
        cachedTangents = allocateFloats(t);
        return t;
    }

    /**
     * 包围盒。float[6] = [minX, minY, minZ, maxX, maxY, maxZ]。
     *
     * <p>依赖：positions。
     */
    public float[] getBounds() {
        CopyOnWriteFloats c = cachedBounds;
        if (c != null) return c.getFloatsSnapshot();

        float[] b = MinMaxScan.compute(mesh);
        cachedBounds = allocateFloats(b);
        return b;
    }

    /**
     * 图元 AABB。float6[nfaces]——[minX, minY, minZ, maxX, maxY, maxZ] × nfaces。
     *
     * <p>依赖：positions + topology。
     *
     * <p>用途：BVH / KD 构建的输入。
     */
    public float[] getFaceAabbs() {
        CopyOnWriteFloats c = cachedFaceAabbs;
        if (c != null) return c.getFloatsSnapshot();

        float[] aabbs = FaceAabbScan.compute(mesh);
        cachedFaceAabbs = allocateFloats(aabbs);
        return aabbs;
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

    private CopyOnWriteInts allocateInts(int[] data) {
        int byteSize = data.length * Integer.BYTES;

        AllocateResult<IntArenaView> r = intTemplate.allocate(byteSize);

        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow int allocation failed unexpectedly: "
                            + byteSize + " bytes");
        }

        IntArenaView view = r.getView();
        view.intBuffer().put(data);
        return new DefaultCopyOnWriteInts(view);
    }

    // ═══════════════════════════════════════════════
    // 丢弃派生
    // ═══════════════════════════════════════════════

    private void discardCornerNormals() {
        if (cachedCornerNormals != null) {
            try {
                cachedCornerNormals.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedCornerNormals = null;
        }
    }


    private void discardCornerEdges() {
        if (cachedCornerEdges != null) {
            try {
                cachedCornerEdges.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedCornerEdges = null;
        }
    }

    private void discardTangents() {
        if (cachedTangents != null) {
            try {
                cachedTangents.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedTangents = null;
        }
    }

    private void discardBounds() {
        if (cachedBounds != null) {
            try {
                cachedBounds.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedBounds = null;
        }
    }

    private void discardFaceAabbs() {
        if (cachedFaceAabbs != null) {
            try {
                cachedFaceAabbs.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedFaceAabbs = null;
        }
    }

    private void discardAll() {
        discardCornerNormals();

        discardCornerEdges();
        discardTangents();
        discardBounds();
        discardFaceAabbs();
    }

    // ═══════════════════════════════════════════════
    // 共享
    // ═══════════════════════════════════════════════

    @Override
    public ExtendedMesh shared() {
        ExtendedMesh m = new ExtendedMesh(
                mesh.shared(), floatTemplate, intTemplate, smoothAngleRadians);

        m.cachedCornerNormals = cachedCornerNormals == null ? null : cachedCornerNormals.shared();

        m.cachedCornerEdges = cachedCornerEdges == null ? null : cachedCornerEdges.shared();
        m.cachedTangents = cachedTangents == null ? null : cachedTangents.shared();
        m.cachedBounds = cachedBounds == null ? null : cachedBounds.shared();
        m.cachedFaceAabbs = cachedFaceAabbs == null ? null : cachedFaceAabbs.shared();

        return m;
    }

    // ═══════════════════════════════════════════════
    // 关闭
    // ═══════════════════════════════════════════════

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(
                cachedCornerNormals, cachedCornerEdges,
                cachedTangents, cachedBounds, cachedFaceAabbs,
                mesh);
        if (ex != null) throw ex;
    }
}