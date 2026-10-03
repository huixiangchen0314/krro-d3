package top.kzre.krro.d3.core.scene;

import top.kzre.krro.d3.core.asset.AssetLib;
import top.kzre.krro.d3.core.geometry.Mesh;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;

public final class AssetMeshRef implements SceneObject, MeshRef{
    private final long id;
    private final AssetLib lib;
    private final long assetId;
    public AssetMeshRef(long id, AssetLib lib, long assetId) {
        this.id = id;
        this.lib = lib;
        this.assetId = assetId;
    }

    @Override
    public CopyOnWrite<Mesh> getRef() {
        return lib.getMeshAsset(assetId).getMesh();
    }

    @Override
    public long getId() {
        return id;
    }
}
