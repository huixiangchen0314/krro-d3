package top.kzre.krro.d3.core.geometry.bvh;

/**
 * BVH 元数据接口——所有 BVH 变体的元数据实现此接口。
 *
 * <p>统一访问路径——查询方不感知具体元数据类型。
 */
public interface IBvhMeta {

    /** 原始图元数。 */
    long primitiveCount();

    /** objRefs 数组——单图元叶返回 null。 */
    int[] objRefs();

    /** objRefs 的有效长度——单图元叶返回 0。 */
    int objRefCount();
}