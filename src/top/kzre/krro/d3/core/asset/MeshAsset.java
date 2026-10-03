package top.kzre.krro.d3.core.asset;

import top.kzre.krro.d3.core.geometry.Mesh;
import top.kzre.krro.d3.core.geometry.Position;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;

/**
 * 网格资产.
 * origin 是本地原点
 * 变换隐含在Mesh的顶点坐标中了
 */
public final class MeshAsset implements Asset, CopyOnWrite<MeshAsset> {
    private final long assetId;
    private final Mesh mesh;
    private final Position origin;
    public MeshAsset(long assetId, Mesh mesh, Position origin) {
        this.assetId = assetId;
        this.mesh = mesh;
        this.origin = origin;
    }

    public Mesh getMesh() {
        return mesh;
    }

    public Position getOrigin() {
        return origin;
    }

    @Override
    public MeshAsset shared() {
        return new MeshAsset(assetId, mesh.shared(), origin);
    }

    @Override
    public void close() throws Exception {
        mesh.close();
    }


    @Override
    public long assetId() {
        return assetId;
    }
}
