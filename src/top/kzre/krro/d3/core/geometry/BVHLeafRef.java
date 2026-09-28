package top.kzre.krro.d3.core.geometry;

/**
 * BVH 叶节点引用——指向图元索引数组的一段区间。
 *
 * <p><b>内部类型</b>——只在 BVH 遍历时短暂存在——不对外暴露。
 * 多图元叶节点用 (start, count) 描述——单图元叶是 count = 1 的特例。
 */
public final class BVHLeafRef {

    public final int start;   // 图元索引数组的起始下标
    public final int count;   // 图元数量——单图元叶为 1

    public BVHLeafRef(int start, int count) {
        this.start = start;
        this.count = count;
    }

    public boolean isSingle() {
        return count == 1;
    }

    @Override
    public String toString() {
        return "BVHLeafRef[start=" + start + ", count=" + count + "]";
    }
}