package top.kzre.krro.d3.core.util;

import java.util.NoSuchElementException;
import java.util.function.LongConsumer;

public final class LongList extends DynamicList {

    private static final int DEFAULT_CAPACITY = 64;

    private long[] data;

    public LongList() { this(DEFAULT_CAPACITY); }

    public LongList(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException();
        this.data = new long[Math.max(initialCapacity, 1)];
    }

    @Override protected int capacity() { return data.length; }

    @Override
    protected void reallocate(int newCapacity) {
        long[] bigger = new long[newCapacity];
        System.arraycopy(data, 0, bigger, 0, sizeValue());
        data = bigger;
    }

    // ─── 类型操作 ───

    public void add(long v) {
        prepareForAppend(1);
        data[sizeValue()] = v;
        advanceSize(1);
    }

    public void addAll(long[] src, int offset, int count) {
        if (count <= 0) return;
        prepareForAppend(count);
        System.arraycopy(src, offset, data, sizeValue(), count);
        advanceSize(count);
    }

    public long get(int i) {
        checkIndex(i);
        return data[i];
    }

    public void set(int i, long v) {
        checkIndex(i);
        data[i] = v;
    }

    public long[] rawArray() { return data; }

    public long[] toArray() {
        long[] out = new long[sizeValue()];
        System.arraycopy(data, 0, out, 0, sizeValue());
        return out;
    }

    /**
     * 删除指定位置的元素——后续元素前移一位。
     *
     * @param i 索引，{@code [0, size)}
     * @return 被删除的值
     * @throws IndexOutOfBoundsException i 越界
     */
    public long removeAt(int i) {
        checkIndex(i);
        long removed = data[i];
        int n = sizeValue();
        int move = n - i - 1;
        if (move > 0) {
            System.arraycopy(data, i + 1, data, i, move);
        }
        setSize(n - 1);
        return removed;
    }

    // ═══════════════════════════════════════════════
    // 遍历——无装箱
    // ═══════════════════════════════════════════════

    /**
     * 遍历所有元素——无装箱。
     *
     * <p>用 {@link LongConsumer} 而不是 {@code Iterable<Long>}——
     * for-each 需要装箱，和项目“long 不装箱”原则冲突。
     *
     * <p>示例：
     * <pre>
     *   list.forEach(id -> process(id));
     * </pre>
     *
     * @param action 每个元素调一次，非 null
     */
    public void forEach(LongConsumer action) {
        if (action == null) throw new NullPointerException("action");
        int n = sizeValue();
        for (int i = 0; i < n; i++) {
            action.accept(data[i]);
        }
    }

    /**
     * 返回游标——支持 {@code while} 循环遍历，无装箱。
     *
     * <p>示例：
     * <pre>
     *   LongList.LongCursor c = list.cursor();
     *   while (c.hasNext()) {
     *       long v = c.next();
     *       ...
     *   }
     * </pre>
     *
     * <p><b>非线程安全</b>：游标读取的是当前列表状态，列表在遍历中
     * 修改时行为未定义。
     */
    public LongCursor cursor() {
        return new LongCursor(sizeValue());
    }

    /**
     * 在指定位置插入元素——原位置及之后的元素整体后移一位。
     *
     * <p>索引允许等于当前 size，此时等价于 {@link #add(long)} 追加到末尾。
     *
     * <p>示例：
     * <pre>
     *   LongList list = new LongList();
     *   list.add(10);          // [10]
     *   list.add(30);          // [10, 30]
     *   list.addAt(1, 20);     // [10, 20, 30]
     *   list.addAt(0, 5);      // [5, 10, 20, 30]
     *   list.addAt(list.size(), 40); // [5, 10, 20, 30, 40]
     * </pre>
     *
     * <p>复杂度：O(n - index)，最坏 O(n)。
     *
     * @param index 插入位置，{@code [0, size]}（含 size）
     * @param v     要插入的值
     * @throws IndexOutOfBoundsException index 不在 {@code [0, size]} 内
     */
    public void addAt(int index, long v) {
        int n = sizeValue();
        if (index < 0 || index > n) {
            throw new IndexOutOfBoundsException(
                    "index: " + index + ", size: " + n);
        }

        prepareForAppend(1);   // 可能触发 reallocate，必须在 arraycopy 之前

        int move = n - index;
        if (move > 0) {
            // 把 [index, n) 整体后移一位，给 index 腾出位置
            System.arraycopy(data, index, data, index + 1, move);
        }
        data[index] = v;
        advanceSize(1);
    }


    /** 无装箱游标。 */
    public final class LongCursor {

        private final int limit;
        private int idx = 0;

        private LongCursor(int limit) {
            this.limit = limit;
        }

        public boolean hasNext() { return idx < limit; }

        public long next() {
            if (idx >= limit) {
                throw new NoSuchElementException("cursor exhausted");
            }
            return data[idx++];
        }
    }
}