package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.geometry.algo.SkeletonBindMatrices;
import top.kzre.krro.d3.core.geometry.algo.SkeletonWorldRest;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.DefaultCopyOnWriteFloats;
import top.kzre.krro.util.arena.AllocateResult;
import top.kzre.krro.util.arena.AutoGrowFloatArrayArenaTemplate;
import top.kzre.krro.util.arena.FloatArenaView;

import java.util.List;

/**
 * 扩展骨架——拥有 {@link Skeleton} 所有权 + 管理派生数据缓存。
 *
 * <p><b>角色</b>：消费者面向这个类。代理 {@link Skeleton} 的本质数据，
 * 管理派生数据的按需计算与缓存。
 *
 * <p><b>派生数据</b>（全部走 COW + Arena）：
 * <pre>
 *   worldRestTransforms   Mat4[nbones]   ← SkeletonWorldRest
 *   bindMatrices          Mat4[nbones]   ← SkeletonBindMatrices
 * </pre>
 *
 * <p><b>算法层</b>：{@code top.kzre.krro.d3.core.geometry.algo} 包——纯函数，
 * 不感知缓存。本类负责缓存。
 *
 * <p><b>缓存失效</b>：Skeleton 是 COW 不可变的。ExtendedSkeleton 持有某个
 * 具体版本后，缓存不会失效——改骨架 = 新的 Skeleton 版本 = 新的
 * ExtendedSkeleton（通过 {@link #shared()}）。
 *
 * <p><b>名称查找</b>：不缓存 name → index 映射。典型骨骼数（几十到几百），
 * 线性搜索足够。频繁查找时，调用方自行建立映射。
 *
 * <p><b>线程契约</b>：单 ExtendedSkeleton 实例 = 单线程。
 * 多线程——每个线程通过 {@link #shared()} 获取副本。
 */
public final class ExtendedSkeleton implements CopyOnWrite<ExtendedSkeleton> {
    // ═══════════════════════════════════════════════
    // 字段
    // ═══════════════════════════════════════════════

    private final Skeleton skeleton;
    private final AutoGrowFloatArrayArenaTemplate floatTemplate;

    /** 世界静置变换缓存。Mat4[nbones]。null 表示未计算。 */
    private CopyOnWriteFloats cachedWorldRest;

    /** 绑定矩阵缓存。Mat4[nbones]。null 表示未计算。 */
    private CopyOnWriteFloats cachedBindMatrices;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public ExtendedSkeleton(Skeleton skeleton, AutoGrowFloatArrayArenaTemplate floatTemplate) {
        if (skeleton == null)      throw new NullPointerException("skeleton");
        if (floatTemplate == null) throw new NullPointerException("floatTemplate");

        this.skeleton      = skeleton;
        this.floatTemplate = floatTemplate;
    }

    // ═══════════════════════════════════════════════
    // 本质访问
    // ═══════════════════════════════════════════════

    public Skeleton skeleton() { return skeleton; }
    public int boneCount()     { return skeleton.boneCount(); }

    // ═══════════════════════════════════════════════
    // 派生数据——读
    // ═══════════════════════════════════════════════

    /**
     * 世界静置变换。Mat4[nbones]——stride 16。
     *
     * <p>沿骨骼层级累乘得到：{@code worldRest[i] = worldRest[parent] × rest[i]}。
     * 根骨骼的 worldRest = rest。
     *
     * <p>用途：蒙皮的绑定矩阵、姿态求值的基准。
     */
    public CopyOnWriteFloats getWorldRestTransforms() {
        CopyOnWriteFloats c = cachedWorldRest;
        if (c != null) return c;

        int floats = skeleton.boneCount() * 16;
        FloatArenaView view = allocateFloatView(floats);
        SkeletonWorldRest.compute(skeleton, view);

        cachedWorldRest = new DefaultCopyOnWriteFloats(view);
        return cachedWorldRest;
    }

    /**
     * 绑定矩阵。Mat4[nbones]——stride 16。
     *
     * <p>世界静置变换的逆：{@code bind[i] = inverse(worldRest[i])}。
     *
     * <p>用途：蒙皮时把顶点从模型空间变换到骨骼空间。
     */
    public CopyOnWriteFloats getBindMatrices() {
        CopyOnWriteFloats c = cachedBindMatrices;
        if (c != null) return c;

        CopyOnWriteFloats worldRest = getWorldRestTransforms();

        int floats = skeleton.boneCount() * 16;
        FloatArenaView view = allocateFloatView(floats);
        SkeletonBindMatrices.compute(worldRest, view);

        cachedBindMatrices = new DefaultCopyOnWriteFloats(view);
        return cachedBindMatrices;
    }

    // ═══════════════════════════════════════════════
    // 名称查找——不缓存，线性搜索
    // ═══════════════════════════════════════════════

    /**
     * 按名称查找骨骼索引——未找到返回 -1。
     *
     * <p>线性搜索。典型骨骼数（几十到几百）足够快。
     */
    public int indexOfBone(String name) {
        if (name == null) return -1;
        List<String> names = skeleton.boneNames().getSnapshot().getList();
        for (int i = 0; i < names.size(); i++) {
            if (name.equals(names.get(i))) return i;
        }
        return -1;
    }

    // ═══════════════════════════════════════════════
    // 分配
    // ═══════════════════════════════════════════════

    private FloatArenaView allocateFloatView(int floatCount) {
        int byteSize = floatCount * Float.BYTES;

        AllocateResult<FloatArenaView> r = floatTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow float allocation failed: " + byteSize + " bytes");
        }
        return r.getView();
    }

    // ═══════════════════════════════════════════════
    // 丢弃派生
    // ═══════════════════════════════════════════════

    private void discardWorldRest() {
        if (cachedWorldRest != null) {
            try {
                cachedWorldRest.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedWorldRest = null; }
    }

    private void discardBindMatrices() {
        if (cachedBindMatrices != null) {
            try {
                cachedBindMatrices.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedBindMatrices = null; }
    }

    // ═══════════════════════════════════════════════
    // 共享
    // ═══════════════════════════════════════════════

    @Override
    public ExtendedSkeleton shared() {
        ExtendedSkeleton s = new ExtendedSkeleton(skeleton.shared(), floatTemplate);

        s.cachedWorldRest    = cachedWorldRest    == null ? null : cachedWorldRest.shared();
        s.cachedBindMatrices = cachedBindMatrices == null ? null : cachedBindMatrices.shared();

        return s;
    }

    // ═══════════════════════════════════════════════
    // 关闭
    // ═══════════════════════════════════════════════

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(
                cachedWorldRest, cachedBindMatrices, skeleton);
        if (ex != null) throw ex;
    }
}