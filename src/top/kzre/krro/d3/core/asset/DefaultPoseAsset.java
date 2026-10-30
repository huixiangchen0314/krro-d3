package top.kzre.krro.d3.core.asset;

import top.kzre.krro.d3.core.geometry.Pose;

/**
 * 姿态资产默认实现。
 */
public final class DefaultPoseAsset extends PoseAsset {

    public DefaultPoseAsset(long assetId, Pose pose, long hostSkeletonId) {
        super(assetId, pose, hostSkeletonId);
    }

    @Override
    public DefaultPoseAsset shared() {
        return new DefaultPoseAsset(assetId, pose.shared(), hostSkeletonId);
    }
}