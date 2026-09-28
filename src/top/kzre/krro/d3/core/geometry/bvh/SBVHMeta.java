package top.kzre.krro.d3.core.geometry.bvh;

/**
 * SBVH 元数据——多图元叶 + objRefs。
 *
 * <p>{@code objRefs} 存放叶节点引用的对象 id——
 * 同一个对象可被多个叶引用（空间分裂）。
 */
public final class SBVHMeta implements IBvhMeta {

    private final long  primitiveCount;
    private final int[] objRefs;
    private final int   objRefCount;

    public SBVHMeta(long primitiveCount, int[] objRefs, int objRefCount) {
        this.primitiveCount = primitiveCount;
        this.objRefs        = objRefs;
        this.objRefCount    = objRefCount;
    }

    @Override public long  primitiveCount() { return primitiveCount; }
    @Override public int[] objRefs()        { return objRefs; }
    @Override public int   objRefCount()    { return objRefCount; }
}