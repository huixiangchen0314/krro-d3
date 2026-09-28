package top.kzre.krro.d3.core.geometry.bvh;

/**
 * SBVH 构建上下文——打包递归构建所需的共享数据。
 *
 * <p><b>objRefs 动态增长</b>：初始 2N 容量——满了翻倍。
 * 阶段 1：每个图元只写一次——refCount 最终等于 primitiveCount。
 * 阶段 2：空间分裂——同一图元可能写多次——refCount 可大于 N。
 *
 * <p><b>非线程安全</b>：仅限单线程构建使用。
 */
public final class SBVHBuildCtx {

    public final float[] data;
    public final int[]   child;
    public final float[] aabbs;
    public final long[]  prims;

    private int[] objRefs;
    private int   refCount;
    private long  nextIdx;

    public SBVHBuildCtx(float[] data, int[] child,
                        float[] aabbs, long[] prims) {
        this.data     = data;
        this.child    = child;
        this.aabbs    = aabbs;
        this.prims    = prims;
        this.objRefs  = new int[prims.length * 2];
        this.refCount = 0;
        this.nextIdx  = 0L;
    }

    /** 分配下一个节点索引——前序分配。 */
    public long allocIdx() {
        long n = nextIdx;
        nextIdx = n + 1L;
        return n;
    }

    /**
     * 将 prims[start, end) 的图元 id 复制到 objRefs。
     *
     * @param start prims 起始索引（含）
     * @param end   prims 结束索引（不含）
     * @return objRefs 中的起始索引
     */
    public int allocRefsFromPrims(int start, int end) {
        int count = end - start;
        if (count <= 0) {
            return refCount;
        }
        ensureCapacity(refCount + count);
        int startRef = refCount;
        for (int i = start; i < end; i++) {
            objRefs[refCount++] = (int) prims[i];
        }
        return startRef;
    }

    private void ensureCapacity(int minCap) {
        if (minCap > objRefs.length) {
            int newCap = objRefs.length;
            while (newCap < minCap) {
                newCap = newCap < 1024 ? newCap * 2 : newCap + (newCap >> 1);
            }
            int[] bigger = new int[newCap];
            System.arraycopy(objRefs, 0, bigger, 0, refCount);
            objRefs = bigger;
        }
    }

    public int[] objRefs()  { return objRefs; }
    public int   refCount() { return refCount; }
    public long  nextIdx()  { return nextIdx; }
}