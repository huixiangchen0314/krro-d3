package top.kzre.krro.d3.core.util;

/**
 * 自动扩容的 int 累加器。
 *
 * <p>用于查询结果收集——避免预先分配固定大小数组导致的溢出或浪费。
 * 初始容量 64——满了翻倍——均摊 O(1)。
 *
 * <p><b>非线程安全</b>：查询是单线程的——无需同步。
 *
 * <p><b>rawArray 契约</b>：{@link #rawArray()} 暴露内部数组——
 * 调用方不得修改有效范围 {@code [0, size())} 内的元素。
 * 数组可能比 {@code size()} 长——尾部是未使用容量。
 */
public final class IntList {

    private static final int DEFAULT_CAPACITY = 64;

    private int[] data;
    private int   size;

    public IntList() {
        this(DEFAULT_CAPACITY);
    }

    public IntList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity must be >= 0");
        }
        this.data = new int[Math.max(initialCapacity, 1)];
        this.size = 0;
    }

    // ═══════════════════════════════════════════════
    // 添加
    // ═══════════════════════════════════════════════

    /** 追加一个 int。 */
    public void add(int v) {
        if (size == data.length) {
            grow();
        }
        data[size++] = v;
    }

    /**
     * 批量追加——从 src[offset, offset+count) 复制到尾部。
     *
     * @param src    源数组
     * @param offset 源起始索引
     * @param count  追加数量
     */
    public void addAll(int[] src, int offset, int count) {
        if (count <= 0) return;
        ensureCapacity(size + count);
        System.arraycopy(src, offset, data, size, count);
        size += count;
    }

    // ═══════════════════════════════════════════════
    // 读取
    // ═══════════════════════════════════════════════

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
        return data[i];
    }

    /** 最后一个元素——size 为 0 时抛异常。 */
    public int last() {
        if (size == 0) {
            throw new IndexOutOfBoundsException("empty");
        }
        return data[size - 1];
    }

    // ═══════════════════════════════════════════════
    // 清理
    // ═══════════════════════════════════════════════

    /** 重置——保留容量——复用实例。 */
    public void clear() {
        size = 0;
    }

    // ═══════════════════════════════════════════════
    // 内部数组访问
    // ═══════════════════════════════════════════════

    /**
     * 暴露内部数组——避免查询结果复制。
     *
     * <p><b>契约</b>：返回的数组可能比 {@code size()} 长——
     * 有效范围是 {@code [0, size())}。调用方不得修改有效范围。
     */
    public int[] rawArray() {
        return data;
    }

    /** 导出副本——有效范围精确长度。 */
    public int[] toArray() {
        int[] out = new int[size];
        System.arraycopy(data, 0, out, 0, size);
        return out;
    }

    // ═══════════════════════════════════════════════
    // 容量管理
    // ═══════════════════════════════════════════════

    /** 保证至少能容纳 {@code minCapacity} 个元素。 */
    public void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCap = data.length;
            while (newCap < minCapacity) {
                newCap = newCap < 1024 ? newCap * 2 : newCap + (newCap >> 1);
            }
            int[] bigger = new int[newCap];
            System.arraycopy(data, 0, bigger, 0, size);
            data = bigger;
        }
    }

    private void grow() {
        int newCap = data.length < 1024 ? data.length * 2 : data.length + (data.length >> 1);
        int[] bigger = new int[newCap];
        System.arraycopy(data, 0, bigger, 0, size);
        data = bigger;
    }

    // ═══════════════════════════════════════════════
    // Object
    // ═══════════════════════════════════════════════

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("IntList[");
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(", ");
            sb.append(data[i]);
        }
        return sb.append(']').toString();
    }
}