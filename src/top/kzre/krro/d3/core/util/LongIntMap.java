package top.kzre.krro.d3.core.util;

import java.util.Arrays;

/**
 * long → int 原始类型哈希表——开放寻址——线性探测。
 *
 * <p><b>空槽约定</b>：value = -1 表示空槽。
 *
 * <p><b>复用</b>：{@link #ensureCapacity(int)} 保留数组容量——
 * 避免大数组反复分配进入老年代。配合 ThreadLocal 使用。
 *
 * <p><b>线程契约</b>：非线程安全。
 */
public final class LongIntMap {

    private static final int EMPTY_VALUE = -1;

    private long[] keys;
    private int[]  values;
    private int    mask;
    private int    size;
    private int    threshold;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public LongIntMap() { this(16); }

    public LongIntMap(int expectedSize) {
        int cap = 1;
        while (cap < expectedSize * 2) cap <<= 1;   // 负载因子 0.5
        this.keys      = new long[cap];
        this.values    = new int[cap];
        this.mask      = cap - 1;
        this.threshold = cap / 2;
        this.size      = 0;
    }

    // ═══════════════════════════════════════════════
    // 复用——不清数组——只重置状态
    // ═══════════════════════════════════════════════

    /**
     * 重置为空——保留当前容量。
     *
     * <p>比 {@code clear()} 语义更明确——用于"下次使用前重置"。
     */
    public void reset() {
        clear();
    }

    /**
     * 确保容量至少能容纳 {@code expected} 个条目——不足则扩容。
     * 现有内容被清除。
     *
     * <p>用于 ThreadLocal 复用场景——容量随最大使用量增长——
     * 之后不再分配。
     */
    public void ensureCapacity(int expected) {
        int needed = 1;
        while (needed < expected * 2) needed <<= 1;

        if (keys.length < needed) {
            this.keys      = new long[needed];
            this.values    = new int[needed];
            this.mask      = needed - 1;
            this.threshold = needed / 2;
        }
        clear();
    }

    // ═══════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════

    public int get(long key) {
        int idx = hash(key) & mask;
        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) return values[idx];
            idx = (idx + 1) & mask;
        }
        return -1;
    }

    public boolean containsKey(long key) {
        int idx = hash(key) & mask;
        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) return true;
            idx = (idx + 1) & mask;
        }
        return false;
    }

    // ═══════════════════════════════════════════════
    // 插入
    // ═══════════════════════════════════════════════

    public int put(long key, int value) {
        if (value == EMPTY_VALUE) {
            throw new IllegalArgumentException("value must not be -1");
        }
        if (size >= threshold) grow();

        int idx = hash(key) & mask;
        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) {
                int old = values[idx];
                values[idx] = value;
                return old;
            }
            idx = (idx + 1) & mask;
        }

        keys[idx]   = key;
        values[idx] = value;
        size++;
        return -1;
    }

    // ═══════════════════════════════════════════════
    // 状态
    // ═══════════════════════════════════════════════

    public int     size()    { return size; }
    public boolean isEmpty() { return size == 0; }

    public int capacity() { return keys.length; }

    public void clear() {
        Arrays.fill(values, EMPTY_VALUE);
        size = 0;
    }

    // ═══════════════════════════════════════════════
    // 扩容
    // ═══════════════════════════════════════════════

    private void grow() {
        long[] oldKeys   = keys;
        int[]  oldValues = values;

        int newCap = keys.length << 1;
        keys      = new long[newCap];
        values    = new int[newCap];
        mask      = newCap - 1;
        threshold = newCap / 2;
        size      = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldValues[i] != EMPTY_VALUE) {
                put(oldKeys[i], oldValues[i]);
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 哈希
    // ═══════════════════════════════════════════════

    private static int hash(long x) {
        x = (x ^ (x >>> 33)) * 0xff51afd7ed558ccdL;
        x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
        x = x ^ (x >>> 33);
        return (int) x;
    }
}