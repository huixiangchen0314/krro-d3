package top.kzre.krro.d3.core.geometry.bvh;

/**
 * SBVH 构建上下文——节点数组 + 引用列表 + objRefs。
 *
 * <p>两套引用数组：
 * <ul>
 *   <li>{@code refPrims} / {@code refAabbs}——分区用——会增长</li>
 *   <li>{@code objRefs}——叶节点输出——只增</li>
 * </ul>
 *
 * <p>分区采用"追加"模式——每次分区从 refs 尾部追加左右两组。
 *
 * <p><b>非线程安全</b>。
 */
public final class SBVHBuildCtx {

    public final float[] data;
    public final int[]   child;

    // ─── refs——分区用 ───

    private int[]   refPrims;
    private float[] refAabbs;
    private int     refCapacity;
    private int     refCount;

    // ─── objRefs——叶输出 ───

    private int[] objRefs;
    private int   objRefCapacity;
    private int   objRefCount;

    private long nextIdx;

    public SBVHBuildCtx(float[] data, int[] child, int initialCapacity) {
        this.data           = data;
        this.child          = child;
        this.refPrims       = new int[initialCapacity];
        this.refAabbs       = new float[initialCapacity * 6];
        this.refCapacity    = initialCapacity;
        this.refCount       = 0;
        this.objRefs        = new int[initialCapacity];
        this.objRefCapacity = initialCapacity;
        this.objRefCount    = 0;
        this.nextIdx        = 0L;
    }

    // ═══════════════════════════════════════════════
    // 节点索引
    // ═══════════════════════════════════════════════

    public long allocIdx() {
        long n = nextIdx;
        nextIdx = n + 1L;
        return n;
    }

    // ═══════════════════════════════════════════════
    // refs 访问——供 SbvhSelect 用
    // ═══════════════════════════════════════════════

    public int[]   refPrims()    { return refPrims; }
    public float[] refAabbs()    { return refAabbs; }
    public int     refCount()    { return refCount; }
    public int     refCapacity() { return refCapacity; }

    /** 供 SbvhSelect 分区后调整 refCount。 */
    public void setRefCount(int n) { this.refCount = n; }

    /** 确保 refs 容量。可能重新分配数组——调用后要重新获取数组引用。 */
    public void ensureRefCapacity(int min) {
        if (min <= refCapacity) return;
        int newCap = refCapacity;
        while (newCap < min) newCap *= 2;
        int[]   np = new int[newCap];
        float[] na = new float[newCap * 6];
        System.arraycopy(refPrims, 0, np, 0, refCount);
        System.arraycopy(refAabbs, 0, na, 0, refCount * 6);
        refPrims    = np;
        refAabbs    = na;
        refCapacity = newCap;
    }

    // ═══════════════════════════════════════════════
    // refs 初始化——从 AabbArray 布局
    // ═══════════════════════════════════════════════

    /**
     * 从 AabbArray 布局的源数组初始化 refs。
     *
     * @param src       AabbArray 布局的 AABB 数组
     * @param startPrim 起始图元 id
     * @param count     图元数
     */
    public void initRefsFromAabbArray(float[] src, int startPrim, int count) {
        ensureRefCapacity(count);
        for (int i = 0; i < count; i++) {
            int srcBase = (startPrim + i) * 6;
            int dstBase = i * 6;
            refPrims[i] = startPrim + i;
            refAabbs[dstBase]     = src[srcBase];
            refAabbs[dstBase + 1] = src[srcBase + 1];
            refAabbs[dstBase + 2] = src[srcBase + 2];
            refAabbs[dstBase + 3] = src[srcBase + 3];
            refAabbs[dstBase + 4] = src[srcBase + 4];
            refAabbs[dstBase + 5] = src[srcBase + 5];
        }
        refCount = count;
    }

    // ═══════════════════════════════════════════════
    // objRefs 访问
    // ═══════════════════════════════════════════════

    public int[] objRefs()     { return objRefs; }
    public int   objRefCount() { return objRefCount; }

    /**
     * 把 refs[start, end) 的 prim id 追加到 objRefs。
     *
     * @return objRefs 中的起始索引
     */
    public int appendObjRefsFromRefs(int start, int end) {
        int count = end - start;
        ensureObjRefCapacity(objRefCount + count);
        int objStart = objRefCount;
        System.arraycopy(refPrims, start, objRefs, objStart, count);
        objRefCount += count;
        return objStart;
    }

    private void ensureObjRefCapacity(int min) {
        if (min <= objRefCapacity) return;
        int newCap = objRefCapacity;
        while (newCap < min) newCap *= 2;
        int[] no = new int[newCap];
        System.arraycopy(objRefs, 0, no, 0, objRefCount);
        objRefs        = no;
        objRefCapacity = newCap;
    }
}