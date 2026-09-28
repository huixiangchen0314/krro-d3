package top.kzre.krro.d3.core.geometry.bvh;

/**
 * 标准 BVH 元数据——单图元叶。
 *
 * <p>BVH / LBVH / Binned BVH 共用此元数据。
 */
public final class BVHMeta implements IBvhMeta {

    private final long primitiveCount;

    public BVHMeta(long primitiveCount) {
        this.primitiveCount = primitiveCount;
    }

    @Override public long  primitiveCount() { return primitiveCount; }
    @Override public int[] objRefs()        { return null; }
    @Override public int   objRefCount()    { return 0; }
}