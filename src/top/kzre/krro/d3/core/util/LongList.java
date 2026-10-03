package top.kzre.krro.d3.core.util;

/**
 * 自动扩容的 long 累加器。
 *
 * <p><b>非线程安全</b>。
 */
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
}