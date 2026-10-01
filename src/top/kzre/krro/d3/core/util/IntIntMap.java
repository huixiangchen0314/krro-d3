package top.kzre.krro.d3.core.util;

import java.util.Arrays;

/**
 * int → int 原始类型哈希表——开放寻址——线性探测。
 *
 * <p><b>空槽约定</b>：value = -1 表示空槽。
 *
 * <p><b>复用</b>：{@link #ensureCapacity(int)} 保留数组容量——
 * 避免大数组反复分配进入老年代。配合 ThreadLocal 使用。
 *
 * <p><b>线程契约</b>：非线程安全。
 */
public final class IntIntMap {

    private static final int EMPTY_VALUE = -1;

    private int[] keys;
    private int[] values;
    private int   mask;
    private int   size;
    private int   threshold;

    public IntIntMap() { this(16); }

    public IntIntMap(int expectedSize) {
        int cap = 1;
        while (cap < expectedSize * 2) cap <<= 1;   // 负载因子 0.5
        this.keys      = new int[cap];
        this.values    = new int[cap];
        this.mask      = cap - 1;
        this.threshold = cap / 2;
        this.size      = 0;
    }

    public void reset() { clear(); }

    public void ensureCapacity(int expected) {
        int needed = 1;
        while (needed < expected * 2) needed <<= 1;
        if (keys.length < needed) {
            this.keys      = new int[needed];
            this.values    = new int[needed];
            this.mask      = needed - 1;
            this.threshold = needed / 2;
        }
        clear();
    }

    public int get(int key) {
        int idx = hash(key) & mask;
        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) return values[idx];
            idx = (idx + 1) & mask;
        }
        return -1;
    }

    public boolean containsKey(int key) {
        int idx = hash(key) & mask;
        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) return true;
            idx = (idx + 1) & mask;
        }
        return false;
    }

    public int put(int key, int value) {
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

    public int     size()    { return size; }
    public boolean isEmpty() { return size == 0; }
    public int     capacity() { return keys.length; }

    public void clear() {
        Arrays.fill(values, EMPTY_VALUE);
        size = 0;
    }

    private void grow() {
        int[] oldKeys   = keys;
        int[] oldValues = values;

        int newCap = keys.length << 1;
        keys      = new int[newCap];
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

    private static int hash(int x) {
        x = (x ^ (x >>> 16)) * 0x7feb352d;
        x = (x ^ (x >>> 15)) * 0x846ca68b;
        x = x ^ (x >>> 16);
        return x;
    }
}