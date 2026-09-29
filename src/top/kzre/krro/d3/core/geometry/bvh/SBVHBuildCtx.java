package top.kzre.krro.d3.core.geometry.bvh;

import top.kzre.krro.d3.core.util.FloatList;
import top.kzre.krro.d3.core.util.IntList;

/**
 * SBVH 构建上下文——节点数组 + 引用列表 + objRefs。
 *
 * <p>两套列表：
 * <ul>
 *   <li>{@code refPrims} / {@code refAabbs}——分区用——会增长</li>
 *   <li>{@code objRefs}——叶节点输出——只增</li>
 * </ul>
 *
 * <p>列表负责扩容。外部通过 {@link #refAabbs()} / {@link #refPrims()} /
 * {@link #objRefs()} 拿原始数组——不要缓存——列表扩容后失效。
 *
 * <p><b>非线程安全</b>。
 */
public final class SBVHBuildCtx {

    public final float[] data;
    public final int[]   child;

    private final IntList   refPrims;
    private final FloatList refAabbs;
    private final IntList   objRefs;

    private long nextIdx;

    public SBVHBuildCtx(float[] data, int[] child, int initialCapacity) {
        this.data     = data;
        this.child    = child;
        this.refPrims = new IntList(initialCapacity);
        this.refAabbs = new FloatList(initialCapacity * 6);
        this.objRefs  = new IntList(initialCapacity);
        this.nextIdx  = 0L;
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

    /** 当前 refs 的 prim id 数组——列表扩容后失效。 */
    public int[] refPrims() { return refPrims.rawArray(); }

    /** 当前 refs 的 AABB 数组——6 float / ref——列表扩容后失效。 */
    public float[] refAabbs() { return refAabbs.rawArray(); }

    public int refCount() { return refPrims.size(); }

    /** 分区后调整 refCount——外部已通过 rawArray 写入。 */
    public void setRefCount(int n) {
        refPrims.setSize(n);
        refAabbs.setSize(n * 6);
    }

    /** 确保 refs 容量。可能触发列表扩容——外部缓存的数组引用失效。 */
    public void ensureRefCapacity(int min) {
        refPrims.ensureCapacity(min);
        refAabbs.ensureCapacity(min * 6);
    }

    // ═══════════════════════════════════════════════
    // refs 初始化——从 AabbArray 布局
    // ═══════════════════════════════════════════════

    /**
     * 从 AabbArray 布局的源数组初始化 refs。
     * 清空后重新填充——refCount = count。
     */
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

    /** objRefs 数组——列表扩容后失效。 */
    public int[] objRefs() { return objRefs.rawArray(); }

    public int objRefCount() { return objRefs.size(); }

    /**
     * 把 refs[start, end) 的 prim id 追加到 objRefs。
     *
     * @return objRefs 中的起始索引
     */
    public int appendObjRefsFromRefs(int start, int end) {
        int count = end - start;
        int objStart = objRefs.size();
        objRefs.addAll(refPrims.rawArray(), start, count);
        return objStart;
    }
}