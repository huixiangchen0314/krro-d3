package top.kzre.krro.d3.core.asset;

import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;
import top.kzre.krro.d3.core.util.cow.ResourceTable;

/**
 * 资产库——所有资产的容器。
 *
 * <p>每种资产类型一个 {@link ResourceTable}。
 * <p>类型写死——领域有界，无扩展场景。
 */
public final class AssetLib implements CopyOnWrite<AssetLib> {

    private final ResourceTable<MeshAsset> meshes;

    private AssetLib(ResourceTable<MeshAsset> meshes) {
        this.meshes = meshes;
    }

    public static AssetLib create() {
        return new AssetLib(ResourceTable.create());
    }

    // ═══════════════════════════════════════════════
    // 访问
    // ═══════════════════════════════════════════════

    public ResourceTable<MeshAsset> meshes() { return meshes; }

    public MeshAsset getMeshAsset(long assetId) {
        return meshes.get(assetId);
    }

    // ═══════════════════════════════════════════════
    // COW
    // ═══════════════════════════════════════════════

    @Override
    public AssetLib shared() {
        return new AssetLib(meshes.shared());
    }

    @Override
    public void close() throws Exception {
        RuntimeException ex = ResourceUtils.closeDelayError(meshes);
        if (ex != null) throw ex;
    }
}