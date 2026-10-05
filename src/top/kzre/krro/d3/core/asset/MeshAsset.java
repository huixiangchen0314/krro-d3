package top.kzre.krro.d3.core.asset;

import top.kzre.krro.d3.core.geometry.ExtendedMesh;
import top.kzre.krro.d3.core.geometry.Position;
import top.kzre.krro.d3.core.geometry.ShapeKeys;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;

/**
 * 网格资产。
 *
 * <p>origin 是本地原点。
 * <p>变换隐含在 Mesh 的顶点坐标中。
 *
 * <p><b>形态键</b>：可选。{@code null} 表示无形态键。
 */
public abstract class MeshAsset implements Asset, CopyOnWrite<MeshAsset> {

    protected final long assetId;
    protected final ExtendedMesh mesh;
    protected final Position origin;
    protected final ShapeKeys shapeKeys;

    protected MeshAsset(long assetId,
                        ExtendedMesh mesh,
                        Position origin,
                        ShapeKeys shapeKeys) {
        if (mesh == null)   throw new NullPointerException("mesh");
        if (origin == null) throw new NullPointerException("origin");

        this.assetId   = assetId;
        this.mesh      = mesh;
        this.origin    = origin;
        this.shapeKeys = shapeKeys;
    }

    @Override public long assetId()      { return assetId; }
    public ExtendedMesh mesh()           { return mesh; }
    public Position origin()             { return origin; }
    public ShapeKeys shapeKeys()         { return shapeKeys; }

    @Override
    public void close() throws Exception {
        RuntimeException ex = ResourceUtils.closeDelayError(mesh, shapeKeys);
        if (ex != null) throw ex;
    }
}