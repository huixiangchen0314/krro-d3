package top.kzre.krro.d3.core.util;

/**
 * 自动扩容列表——模板方法模式。
 *
 * <p>父类管理 size 字段——子类通过 protected 方法访问。
 */
public abstract class DynamicList {

    protected static final int SMALL_THRESHOLD  = 1024;
    protected static final int MEDIUM_THRESHOLD = 16384;
    protected static final int LARGE_STEP       = 4096;

    /** 逻辑大小——父类私有——子类通过方法访问。 */
    private int size;

    // ═══════════════════════════════════════════════
    // 子类实现——变化的步骤
    // ═══════════════════════════════════════════════

    /** 当前容量——子类返回 data.length。 */
    protected abstract int capacity();

    /** 按 newCapacity 分配新数组——拷贝 [0, size)——替换内部数组。 */
    protected abstract void reallocate(int newCapacity);

    // ═══════════════════════════════════════════════
    // size 访问——父类控制
    // ═══════════════════════════════════════════════

    /** 读当前大小——子类用。 */
    protected final int sizeValue() { return size; }

    /** 推进大小——子类在写完 data[size] 后调用。 */
    protected final void advanceSize(int n) {
        if (n < 0) throw new IllegalArgumentException("n < 0");
        this.size += n;
    }

    // ═══════════════════════════════════════════════
    // 公共只读
    // ═══════════════════════════════════════════════

    public final int size() { return size; }
    public final boolean isEmpty() { return size == 0; }

    public final void setSize(int newSize) {
        if (newSize < 0) throw new IllegalArgumentException("newSize < 0");
        if (newSize > capacity()) throw new IllegalStateException("newSize > capacity");
        this.size = newSize;
    }

    public final void clear() { this.size = 0; }

    // ═══════════════════════════════════════════════
    // 索引检查——父类控制
    // ═══════════════════════════════════════════════

    protected final void checkIndex(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException("i=" + i + ", size=" + size);
        }
    }

    // ═══════════════════════════════════════════════
    // 扩容模板——父类控制
    // ═══════════════════════════════════════════════

    public final void ensureCapacity(int minCapacity) {
        if (minCapacity <= capacity()) return;
        int newCap = growTo(capacity(), minCapacity);
        reallocate(newCap);
    }

    protected final void prepareForAppend(int needed) {
        if (size + needed > capacity()) {
            ensureCapacity(size + needed);
        }
    }

    protected static int growTo(int current, int minCapacity) {
        int newCap = Math.max(current, 1);
        while (newCap < minCapacity) {
            if (newCap < SMALL_THRESHOLD) {
                newCap *= 2;
            } else if (newCap < MEDIUM_THRESHOLD) {
                newCap += newCap >> 1;
            } else {
                newCap += LARGE_STEP;
            }
        }
        return newCap;
    }
}