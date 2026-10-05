package top.kzre.krro.d3.core.scene;

public final class AssetMeshRef implements SceneObject, MeshRef{
    private final long id;
    private final long assetId;
    public AssetMeshRef(long id, long assetId) {
        this.id = id;
        this.assetId = assetId;
    }

    @Override
    public long assetId() {
        return assetId;
    }

    @Override
    public long getId() {
        return id;
    }
}
