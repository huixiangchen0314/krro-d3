package top.kzre.krro.d3.core.scene;

/**
 * 网格引用者, 引用烘焙过的，可直接渲染的网格.
 * <br/>
 * 注意网格不属于该接口
 */
@FunctionalInterface
public interface MeshRef {
    long assetId();
}
