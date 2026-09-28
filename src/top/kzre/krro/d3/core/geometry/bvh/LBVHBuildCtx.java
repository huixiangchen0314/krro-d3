package top.kzre.krro.d3.core.geometry.bvh;

/**
 * LBVH 构建上下文——打包递归构建所需的共享数据。
 *
 * <p><b>非线程安全</b>：{@link #allocIdx()} 是普通递增——
 * 无原子、无同步。仅限单线程构建使用。
 */
public final class LBVHBuildCtx {

    public final float[] data;
    public final int[]   child;
    public final float[] aabbs;
    public final long[]  mortons;

    private long nextIdx;

    public LBVHBuildCtx(float[] data,
                        int[]   child,
                        float[] aabbs,
                        long[]  mortons) {
        this.data     = data;
        this.child    = child;
        this.aabbs    = aabbs;
        this.mortons  = mortons;
        this.nextIdx  = 0L;
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