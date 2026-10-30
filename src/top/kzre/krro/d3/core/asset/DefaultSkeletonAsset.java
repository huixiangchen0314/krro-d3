package top.kzre.krro.d3.core.asset;

import top.kzre.krro.d3.core.geometry.ExtendedSkeleton;

/**
 * 骨架资产默认实现。
 */
public final class DefaultSkeletonAsset extends SkeletonAsset {

    public DefaultSkeletonAsset(long assetId, ExtendedSkeleton skeleton) {
        super(assetId, skeleton);
    }

    @Override
    public DefaultSkeletonAsset shared() {
        return new DefaultSkeletonAsset(assetId, skeleton.shared());
    }
}