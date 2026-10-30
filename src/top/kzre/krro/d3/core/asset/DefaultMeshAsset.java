package top.kzre.krro.d3.core.asset;

import top.kzre.krro.d3.core.geometry.ExtendedMesh;
import top.kzre.krro.d3.core.geometry.Position;
import top.kzre.krro.d3.core.geometry.ShapeKeys;
import top.kzre.krro.d3.core.util.cow.COWUtils;

/**
 * 网格资产默认实现。
 */
public final class DefaultMeshAsset extends MeshAsset {


    DefaultMeshAsset(long assetId, ExtendedMesh mesh, ShapeKeys shapeKeys) {
        super(assetId, mesh,shapeKeys);
    }

    public static DefaultMeshAsset create(long assetId,
                                          ExtendedMesh mesh,
                                          ShapeKeys shapeKeys) {
        return new DefaultMeshAsset(assetId, mesh, shapeKeys);
    }

    @Override
    public DefaultMeshAsset shared() {
        return new DefaultMeshAsset(assetId,
                mesh.shared(),
                COWUtils.sharedNullable(shapeKeys));
    }
}