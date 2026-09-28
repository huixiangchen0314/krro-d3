package top.kzre.krro.d3.core.geometry;

/**
 * 空间索引的区域查询结果——覆盖在某区域内的图元 id 集合。
 *
 * <p><b>调用约定</b>：不绑定具体结构——BVH / KD / 其他空间索引
 * 都可以返回这个类型。
 *
 * <p><b>值类</b>——封装命中的图元 id 数组 + 有效长度。
 * 调用方通过 {@link #size()} / {@link #get(int)} 访问——
 * 不直接接触内部数组。
 */
public final class SpatialOverlay {

    /** 空结果单例。 */
    public static final SpatialOverlay EMPTY = new SpatialOverlay(new int[0], 0);

    private final int[] ids;
    private final int   size;

    public SpatialOverlay(int[] ids, int size) {
        this.ids  = ids;
        this.size = size;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int get(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException("i=" + i + ", size=" + size);
        }
        return ids[i];
    }

    /** 导出副本——调用方需要持有数组时用。 */
    public int[] toArray() {
        int[] out = new int[size];
        System.arraycopy(ids, 0, out, 0, size);
        return out;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SpatialOverlay[");
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(", ");
            sb.append(ids[i]);
        }
        return sb.append(']').toString();
    }
}