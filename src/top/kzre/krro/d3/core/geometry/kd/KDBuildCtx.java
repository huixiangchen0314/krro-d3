package top.kzre.krro.d3.core.geometry.kd;

/**
 * KD 构建上下文——打包递归构建所需的共享数据。
 *
 * <p><b>非线程安全</b>：{@link #allocIdx()} 是普通递增——
 *
 * <p>字段全部为 primitive 数组引用——访问零开销。
 * {@code nextIdx} 是普通 long——非原子——无 CAS 开销。
 */
public final class KDBuildCtx {

    public final float[] aabbs;
    public final long[]  prims;
    public final float[] split;
    public final byte[]  axis;
    public final int[]   child;

    private long nextIdx;

    public KDBuildCtx(float[] aabbs,
                      long[]  prims,
                      float[] split,
                      byte[]  axis,
                      int[]   child) {
        this.aabbs   = aabbs;
        this.prims   = prims;
        this.split   = split;
        this.axis    = axis;
        this.child   = child;
        this.nextIdx = 0L;
    }

    /**
     * 分配下一个节点索引——前序分配。
     *
     * @return 当前 nextIdx——然后自增
     */
    public long allocIdx() {
        long n = nextIdx;
        nextIdx = n + 1L;
        return n;
    }

    /** 已分配的节点数——构建结束后用于断言。 */
    public long allocatedCount() {
        return nextIdx;
    }
}