package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteObject;
import top.kzre.krro.d3.core.util.cow.ShallowListResource;

/**
 * 姿态——一组骨骼变换的快照。
 *
 * <p><b>领域定位</b>：某一时刻的骨骼姿态。部分覆盖——只包含需要修改的
 * 骨骼。未覆盖的骨骼在应用时用骨架的静置姿态。
 *
 * <p><b>身份与匹配</b>：按骨骼名称引用骨架。名称是稳定身份。加载姿态时，
 * 按名称在骨架中查找索引。
 *
 * <p><b>变换语义</b>：poseTransform 是相对父骨骼的绝对局部变换，
 * 和 Skeleton 的 restTransform 结构一致。应用时直接替代 rest。
 *
 * <p><b>数据约束</b>：
 * <ul>
 *   <li>骨骼名称唯一</li>
 *   <li>数组长度一致：{@code boneNames.size() == posePositions.length / 3 == ...}</li>
 *   <li>四元数已归一化</li>
 * </ul>
 *
 * <p><b>布局</b>：
 * <pre>
 *   boneNames       String   —        [npose]
 *   posePositions   float3   stride 3 [npose]
 *   poseRotations   float4   stride 4 [npose]   四元数 (x,y,z,w)
 *   poseScales      float3   stride 3 [npose]
 * </pre>
 *
 * <p><b>不感知派生数据</b>：世界姿态变换、最终骨骼变换——由
 * {@code ExtendedPose} 缓存。
 *
 * <p><b>共享</b>：{@link #shared()} 返回共享底层数据的副本——
 * 写时由 {@link CopyOnWrite} 自动隔离。
 */
public final class Pose implements CopyOnWrite<Pose> {

    /** 覆盖的骨骼名称。String[npose]。稳定身份。 */
    private final CopyOnWriteObject<ShallowListResource<String>> boneNames;

    /** 姿态平移——相对父骨骼。float3[npose]——stride 3。 */
    private final CopyOnWriteFloats posePositions;

    /** 姿态旋转——相对父骨骼。float4[npose]——stride 4。四元数 (x,y,z,w)。 */
    private final CopyOnWriteFloats poseRotations;

    /** 姿态缩放——相对父骨骼。float3[npose]——stride 3。 */
    private final CopyOnWriteFloats poseScales;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public Pose(
            CopyOnWriteObject<ShallowListResource<String>> boneNames,
            CopyOnWriteFloats posePositions,
            CopyOnWriteFloats poseRotations,
            CopyOnWriteFloats poseScales) {

        if (boneNames == null || posePositions == null
                || poseRotations == null || poseScales == null) {
            throw new NullPointerException("Pose fields must not be null");
        }

        this.boneNames     = boneNames;
        this.posePositions = posePositions;
        this.poseRotations = poseRotations;
        this.poseScales    = poseScales;
    }

    // ═══════════════════════════════════════════════
    // 计数
    // ═══════════════════════════════════════════════

    public int poseCount() {
        return boneNames.getSnapshot().getList().size();
    }

    // ═══════════════════════════════════════════════
    // 本质访问
    // ═══════════════════════════════════════════════

    public CopyOnWriteObject<ShallowListResource<String>> boneNames() { return boneNames; }
    public CopyOnWriteFloats posePositions() { return posePositions; }
    public CopyOnWriteFloats poseRotations() { return poseRotations; }
    public CopyOnWriteFloats poseScales()    { return poseScales; }

    // ═══════════════════════════════════════════════
    // 便捷访问
    // ═══════════════════════════════════════════════

    /** 获取骨骼名称——按姿态索引。 */
    public String boneName(int index) {
        return boneNames.getSnapshot().getList().get(index);
    }

    // ═══════════════════════════════════════════════
    // 共享
    // ═══════════════════════════════════════════════

    @Override
    public Pose shared() {
        return new Pose(
                boneNames.shared(),
                posePositions.shared(),
                poseRotations.shared(),
                poseScales.shared());
    }

    // ═══════════════════════════════════════════════
    // 关闭
    // ═══════════════════════════════════════════════

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(
                boneNames, posePositions, poseRotations, poseScales);
        if (ex != null) throw ex;
    }
}