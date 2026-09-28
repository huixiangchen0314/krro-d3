package top.kzre.krro.d3.core.geometry.bvh;

/**
 * Binned BVH 构建上下文——打包递归构建所需的共享数据。
 *
 * <p><b>非线程安全</b>：{@link #allocIdx()} 是普通递增——
 * 仅限单线程构建使用。
 */
public final class BinnedBvhBuildCtx {

    public final float[] data;
    public final int[]   child;
    public final float[] aabbs;
    public final long[]  prims;

    private long nextIdx;

    public BinnedBvhBuildCtx(float[] data, int[] child,
                             float[] aabbs, long[] prims) {
        this.data    = data;
        this.child   = child;
        this.aabbs   = aabbs;
        this.prims   = prims;
        this.nextIdx = 0L;
    }

    /** 分配下一个节点索引——前序分配。 */
    public long allocIdx() {
        long n = nextIdx;
        nextIdx = n + 1L;
        return n;
    }

    public long allocatedCount() {
        return nextIdx;
    }
}