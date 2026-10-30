package top.kzre.krro.d3.core.asset;

import top.kzre.krro.d3.core.geometry.ExtendedSkeleton;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;

/**
 * 骨架资产。
 *
 * <p><b>领域定位</b>：骨骼结构的资产身份 + 派生缓存容器。
 * 不持有姿态、动画、约束、蒙皮权重——那些是独立资产或实例数据。
 *
 * <p>与其他资产一致：抽象类保留扩展性，DefaultSkeletonAsset 是默认实现。
 */
public abstract class SkeletonAsset implements Asset, CopyOnWrite<SkeletonAsset> {

    protected final long assetId;
    protected final ExtendedSkeleton skeleton;

    protected SkeletonAsset(long assetId, ExtendedSkeleton skeleton) {
        if (skeleton == null) throw new NullPointerException("skeleton");
        this.assetId  = assetId;
        this.skeleton = skeleton;
    }

    @Override public long assetId() { return assetId; }

    public ExtendedSkeleton skeleton() { return skeleton; }

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(skeleton);
        if (ex != null) throw ex;
    }
}