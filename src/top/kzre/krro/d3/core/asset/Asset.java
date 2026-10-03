package top.kzre.krro.d3.core.asset;

/**
 * 资产。
 *
 * <p>数据属于资产库，其他地方只持有引用。
 */
public interface Asset {
    /**
     * 资产 id，全局唯一。
     */
    long assetId();
}