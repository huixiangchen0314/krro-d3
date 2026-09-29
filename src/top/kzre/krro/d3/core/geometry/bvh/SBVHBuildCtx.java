package top.kzre.krro.d3.core.geometry.bvh;

import top.kzre.krro.d3.core.util.FloatList;
import top.kzre.krro.d3.core.util.IntList;

/**
 * SBVH 构建上下文——节点数组 + 引用列表 + objRefs。
 *
 * <p><b>节点数组动态增长</b>：SBVH 的 ref 膨胀导致节点数不确定——
 * 无法用 2n-1 预分配。改用列表——按需扩容。
 *
 * <p>节点 data 长度 = 节点数 × 6——child 长度 = 节点数 × 2。
 * 列表 size 始终反映"已分配节点数"——不是"已写入节点数"。
 *
 * <p><b>非线程安全</b>。
 */
public final class SBVHBuildCtx {

    private final FloatList data;      // 节点 AABB
    private final IntList   child;     // 节点子

    private final IntList   refPrims;
    private final FloatList refAabbs;
    private final IntList   objRefs;

    private long nextIdx;

    public SBVHBuildCtx(int initialNodeCapacity, int initialRefCapacity) {
        this.data     = new FloatList(initialNodeCapacity * 6);
        this.child    = new IntList(initialNodeCapacity * 2);
        this.refPrims = new IntList(initialRefCapacity);
        this.refAabbs = new FloatList(initialRefCapacity * 6);
        this.objRefs  = new IntList(initialRefCapacity);
        this.nextIdx  = 0L;
    }

    // ═══════════════════════════════════════════════
    // 节点索引——动态增长
    // ═══════════════════════════════════════════════

    /**
     * 分配下一个节点索引。
     *
     * <p>保证 data / child 列表的 size 至少覆盖该节点——但不初始化。
     * 调用方负责写入 data[idx*6..idx*6+5] 和 child[idx*2..idx*2+1]。
     */
    public long allocIdx() {
        long n = nextIdx;
        nextIdx = n + 1L;
        int neededData  = (int) ((n + 1) * 6);
        int neededChild = (int) ((n + 1) * 2);
        data.ensureCapacity(neededData);
        child.ensureCapacity(neededChild);
        data.setSize(neededData);
        child.setSize(neededChild);
        return n;
    }

    // ═══════════════════════════════════════════════
    // 节点数组访问
    // ═══════════════════════════════════════════════

    /** data 原始数组——扩容后失效——每次递归后重新获取。 */
    public float[] dataArray() { return data.rawArray(); }

    /** child 原始数组——扩容后失效——每次递归后重新获取。 */
    public int[] childArray() { return child.rawArray(); }

    // ═══════════════════════════════════════════════
    // refs 访问——供 SbvhSelect 用
    // ═══════════════════════════════════════════════

    public int[]   refPrims() { return refPrims.rawArray(); }
    public float[] refAabbs() { return refAabbs.rawArray(); }
    public int     refCount() { return refPrims.size(); }

    public void setRefCount(int n) {
        refPrims.setSize(n);
        refAabbs.setSize(n * 6);
    }

    public void ensureRefCapacity(int min) {
        refPrims.ensureCapacity(min);
        refAabbs.ensureCapacity(min * 6);
    }

    // ═══════════════════════════════════════════════
    // refs 初始化
    // ═══════════════════════════════════════════════

    public void initRefsFromAabbArray(float[] src, int startPrim, int count) {
        refPrims.clear();
        refAabbs.clear();
        refPrims.ensureCapacity(count);
        refAabbs.ensureCapacity(count * 6);
        for (int i = 0; i < count; i++) {
            int srcBase = (startPrim + i) * 6;
            refPrims.add(startPrim + i);
            refAabbs.add(src[srcBase]);
            refAabbs.add(src[srcBase + 1]);
            refAabbs.add(src[srcBase + 2]);
            refAabbs.add(src[srcBase + 3]);
            refAabbs.add(src[srcBase + 4]);
            refAabbs.add(src[srcBase + 5]);
        }
    }

    // ═══════════════════════════════════════════════
    // objRefs 访问
    // ═══════════════════════════════════════════════

    public int[] objRefs() { return objRefs.rawArray(); }
    public int   objRefCount() { return objRefs.size(); }

    public int appendObjRefsFromRefs(int start, int end) {
        int count = end - start;
        int objStart = objRefs.size();
        objRefs.addAll(refPrims.rawArray(), start, count);
        return objStart;
    }
}