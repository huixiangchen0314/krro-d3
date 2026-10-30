package top.kzre.krro.d3.core.asset;

import top.kzre.krro.d3.core.geometry.Pose;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;

/**
 * 姿态资产。
 *
 * <p><b>领域定位</b>：一组骨骼变换的快照。部分覆盖——只包含需要修改的
 * 骨骼。未覆盖的骨骼在应用时用骨架的静置姿态。
 *
 * <p><b>与骨架的关系</b>：姿态引用一个骨架。按骨骼名称匹配——
 * {@link Pose} 里的骨骼名在目标骨架中查找索引。
 *
 * <p><b>依赖</b>：Pose 的数据结构本身不含骨架引用。
 * 引用在资产层——便于 Pose 保持纯几何。
 *
 * <p><b>复用</b>：姿态可用于不同骨架——只要骨骼名称兼容。
 * hostSkeletonId 是"来源记录"，不是"唯一绑定"。
 */
public abstract class PoseAsset implements Asset, CopyOnWrite<PoseAsset> {

    protected final long assetId;
    protected final Pose pose;

    /**
     * 来源骨架——姿态从哪个骨架编辑生成。
     *
     * <p>用途：记录出处、编辑器显示、名称匹配的默认目标。
     * 不限制姿态只能用于这个骨架——骨骼名称兼容即可。
     */
    protected final long hostSkeletonId;

    protected PoseAsset(long assetId, Pose pose, long hostSkeletonId) {
        if (pose == null) throw new NullPointerException("pose");
        this.assetId        = assetId;
        this.pose           = pose;
        this.hostSkeletonId = hostSkeletonId;
    }

    @Override public long assetId() { return assetId; }

    public Pose pose()             { return pose; }
    public long hostSkeletonId()   { return hostSkeletonId; }

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(pose);
        if (ex != null) throw ex;
    }
}