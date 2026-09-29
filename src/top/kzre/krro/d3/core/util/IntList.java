package top.kzre.krro.d3.core.util;

/**
 * 自动扩容的 int 累加器。
 *
 * <p><b>非线程安全</b>。
 */
public final class IntList extends DynamicList {

    private static final int DEFAULT_CAPACITY = 64;

    private int[] data;

    public IntList() { this(DEFAULT_CAPACITY); }

    public IntList(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException();
        this.data = new int[Math.max(initialCapacity, 1)];
    }

    @Override protected int capacity() { return data.length; }

    @Override
    protected void reallocate(int newCapacity) {
        int[] bigger = new int[newCapacity];
        System.arraycopy(data, 0, bigger, 0, sizeValue());
        data = bigger;
    }

    // ─── 类型操作 ───

    public void add(int v) {
        prepareForAppend(1);
        data[sizeValue()] = v;
        advanceSize(1);
    }

    public void addAll(int[] src, int offset, int count) {
        if (count <= 0) return;
        prepareForAppend(count);
        System.arraycopy(src, offset, data, sizeValue(), count);
        advanceSize(count);
    }

    public int get(int i) {
        checkIndex(i);
        return data[i];
    }

    public void set(int i, int v) {
        checkIndex(i);
        data[i] = v;
    }

    public int[] rawArray() { return data; }

    public int[] toArray() {
        int[] out = new int[sizeValue()];
        System.arraycopy(data, 0, out, 0, sizeValue());
        return out;
    }
}