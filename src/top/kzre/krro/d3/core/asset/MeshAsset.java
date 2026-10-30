package top.kzre.krro.d3.core.asset;

import top.kzre.krro.d3.core.geometry.ExtendedMesh;
import top.kzre.krro.d3.core.geometry.ShapeKeys;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;

/**
 * 网格资产。
 *
 * <p><b>领域定位</b>：几何数据的资产身份 + 派生缓存容器。
 * 本地空间隐含在顶点坐标里——不存 origin。
 *
 * <p><b>数据</b>：
 * <ul>
 *   <li>{@link #mesh()}      —— 几何本质数据 + 派生缓存</li>
 *   <li>{@link #shapeKeys()} —— 形态键（可空）</li>
 * </ul>
 */
public abstract class MeshAsset implements Asset, CopyOnWrite<MeshAsset> {

    protected final long assetId;
    protected final ExtendedMesh mesh;
    protected final ShapeKeys shapeKeys;

    protected MeshAsset(long assetId, ExtendedMesh mesh, ShapeKeys shapeKeys) {
        if (mesh == null) throw new NullPointerException("mesh");
        this.assetId = assetId;
        this.mesh = mesh;
        this.shapeKeys = shapeKeys;
    }

    @Override
    public long assetId() {
        return assetId;
    }

    public ExtendedMesh mesh() {
        return mesh;
    }

    public ShapeKeys shapeKeys() {
        return shapeKeys;
    }

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(mesh, shapeKeys);
        if (ex != null) throw ex;
    }
}