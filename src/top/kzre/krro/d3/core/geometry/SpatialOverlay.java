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

    /**
     * 精确去重——保持原有顺序。
     *
     * <p>用 {@link java.util.HashSet} 记录已见——第一次出现保留。
     * O(n) 时间 + O(n) 空间——结果顺序与输入一致。
     *
     * <p>不去重时返回原 overlay——无额外分配。
     */
    public static SpatialOverlay distinct(SpatialOverlay overlay) {
        int n = overlay.size;
        if (n <= 1) return overlay;

        java.util.HashSet<Integer> seen = new java.util.HashSet<>(n + n / 2);
        int[] buf = new int[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            int v = overlay.ids[i];
            if (seen.add(v)) {
                buf[count++] = v;
            }
        }

        if (count == n) return overlay;   // 无重复——返回原对象
        return new SpatialOverlay(buf, count);
    }

    /**
     * 快速去重——不保证保持原有顺序。
     *
     * <p>排序 + 单次扫描去重。O(n log n) 时间——原地排序副本——
     * 结果顺序为升序——不是输入顺序。
     *
     * <p>比 {@link #distinct} 快——适合"顺序无关"的候选集合。
     */
    public static SpatialOverlay distinctFast(SpatialOverlay overlay) {
        int n = overlay.size;
        if (n <= 1) return overlay;

        int[] sorted = new int[n];
        System.arraycopy(overlay.ids, 0, sorted, 0, n);
        java.util.Arrays.sort(sorted);

        int count = 1;
        for (int i = 1; i < n; i++) {
            if (sorted[i] != sorted[count - 1]) {
                sorted[count++] = sorted[i];
            }
        }

        if (count == n) return overlay;   // 无重复——返回原对象
        return new SpatialOverlay(sorted, count);
    }
}