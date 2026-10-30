package top.kzre.krro.d3.core.asset;

import top.kzre.krro.d3.core.util.cow.ResourceTables;

public final class MeshAssets {
    private MeshAssets() {}

    public static void add(AssetLib lib, MeshAsset mesh) {
        ResourceTables.add(lib.meshes(), mesh.assetId(), mesh);
    }
}