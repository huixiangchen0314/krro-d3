package top.kzre.krro.d3.core.util;

/**
 * 自动扩容的 float 累加器。
 *
 * <p><b>非线程安全</b>。
 */
public final class FloatList extends DynamicList {

    private static final int DEFAULT_CAPACITY = 64;

    private float[] data;

    public FloatList() { this(DEFAULT_CAPACITY); }

    public FloatList(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException();
        this.data = new float[Math.max(initialCapacity, 1)];
    }

    // ═══════════════════════════════════════════════
    // 模板步骤
    // ═══════════════════════════════════════════════

    @Override
    protected int capacity() { return data.length; }

    @Override
    protected void reallocate(int newCapacity) {
        float[] bigger = new float[newCapacity];
        System.arraycopy(data, 0, bigger, 0, sizeValue());
        data = bigger;
    }

    // ═══════════════════════════════════════════════
    // 追加
    // ═══════════════════════════════════════════════

    public void add(float v) {
        prepareForAppend(1);
        data[sizeValue()] = v;
        advanceSize(1);
    }

    public void addAll(float[] src, int offset, int count) {
        if (count <= 0) return;
        prepareForAppend(count);
        System.arraycopy(src, offset, data, sizeValue(), count);
        advanceSize(count);
    }

    // ═══════════════════════════════════════════════
    // 索引
    // ═══════════════════════════════════════════════

    public float get(int i) {
        checkIndex(i);
        return data[i];
    }

    public void set(int i, float v) {
        checkIndex(i);
        data[i] = v;
    }

    // ═══════════════════════════════════════════════
    // 导出
    // ═══════════════════════════════════════════════

    public float[] rawArray() { return data; }

    public float[] toArray() {
        float[] out = new float[sizeValue()];
        System.arraycopy(data, 0, out, 0, sizeValue());
        return out;
    }
}