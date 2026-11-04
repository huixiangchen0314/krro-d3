package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteObject;
import top.kzre.krro.d3.core.util.cow.ShallowListResource;

/**
 * 骨架——骨骼层级 + 静置姿态。
 *
 * <p><b>领域定位</b>：纯骨骼结构。不含姿态、动画、约束、蒙皮权重、
 * 编辑器显示数据。骨架只表达"有哪些骨骼，长什么样"。
 *
 * <p><b>身份与匹配</b>：骨骼名称是稳定身份。Pose、Animation、Constraint
 * 全部按名称引用骨骼。索引是运行时加速。
 *
 * <p><b>数据约束</b>：
 * <ul>
 *   <li>骨骼名称唯一（同一骨架内）</li>
 *   <li>数组长度一致</li>
 *   <li>{@code parentIndices[i] < i}——拓扑序</li>
 *   <li>{@code parentIndices[i] >= -1}——-1 表示根</li>
 *   <li>至少一根根骨骼</li>
 * </ul>
 *
 * <p><b>布局</b>：
 * <pre>
 *   boneNames       String   —        [nbones]
 *   parentIndices   int      stride 1 [nbones]
 *   restPositions   float3   stride 3 [nbones]
 *   restRotations   float4   stride 4 [nbones]   四元数 (x,y,z,w)
 *   restScales      float3   stride 3 [nbones]
 * </pre>
 *
 * <p><b>编辑器扩展</b>：骨骼长度、颜色、分组、可见性等——不是核心数据。
 * 上层利用 {@code boneNames} 的下标作为身份，用 COW 横向挂这些字段。
 *
 * <p><b>不感知派生数据</b>：世界静置变换、绑定矩阵、名称到索引映射——
 * 全部由使用者缓存。
 */
public final class Skeleton implements CopyOnWrite<Skeleton> {

    /** 骨骼名称。String[nbones]。稳定身份。 */
    private final CopyOnWriteObject<ShallowListResource<String>> boneNames;

    /** 父骨骼索引。int[nbones]。-1 表示根。约定 {@code parentIndices[i] < i}。 */
    private final CopyOnWriteInts parentIndices;

    /** 静置平移——相对父骨骼。float3[nbones]——stride 3。 */
    private final CopyOnWriteFloats restPositions;

    /** 静置旋转——相对父骨骼。float4[nbones]——stride 4。四元数 (x,y,z,w)。 */
    private final CopyOnWriteFloats restRotations;

    /** 静置缩放——相对父骨骼。float3[nbones]——stride 3。 */
    private final CopyOnWriteFloats restScales;

    public Skeleton(
            CopyOnWriteObject<ShallowListResource<String>> boneNames,
            CopyOnWriteInts parentIndices,
            CopyOnWriteFloats restPositions,
            CopyOnWriteFloats restRotations,
            CopyOnWriteFloats restScales) {

        if (boneNames == null || parentIndices == null
                || restPositions == null || restRotations == null
                || restScales == null) {
            throw new NullPointerException("Skeleton fields must not be null");
        }

        this.boneNames     = boneNames;
        this.parentIndices = parentIndices;
        this.restPositions = restPositions;
        this.restRotations = restRotations;
        this.restScales    = restScales;
    }

    // ═══════════════════════════════════════════════
    // 计数
    // ═══════════════════════════════════════════════

    public int boneCount() {
        return boneNames.getSnapshot().getList().size();
    }

    // ═══════════════════════════════════════════════
    // 本质访问
    // ═══════════════════════════════════════════════

    public CopyOnWriteObject<ShallowListResource<String>> boneNames()     { return boneNames; }
    public CopyOnWriteInts parentIndices() { return parentIndices; }
    public CopyOnWriteFloats restPositions() { return restPositions; }
    public CopyOnWriteFloats restRotations() { return restRotations; }
    public CopyOnWriteFloats restScales()    { return restScales; }

    // ═══════════════════════════════════════════════
    // 便捷访问
    // ═══════════════════════════════════════════════

    /** 获取骨骼名称——按索引。 */
    public String boneName(int index) {
        return boneNames.getSnapshot().getList().get(index);
    }

    /** 获取父骨骼索引——-1 表示根。 */
//    public int parentIndex(int index) {
//        return parentIndices.getIntsSnapshot()[index];
//    }

    // ═══════════════════════════════════════════════
    // 共享
    // ═══════════════════════════════════════════════

    @Override
    public Skeleton shared() {
        return new Skeleton(
                boneNames.shared(),
                parentIndices.shared(),
                restPositions.shared(),
                restRotations.shared(),
                restScales.shared());
    }

    // ═══════════════════════════════════════════════
    // 关闭
    // ═══════════════════════════════════════════════

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(
                boneNames, parentIndices, restPositions,
                restRotations, restScales);
        if (ex != null) throw ex;
    }
}