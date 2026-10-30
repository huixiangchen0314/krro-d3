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