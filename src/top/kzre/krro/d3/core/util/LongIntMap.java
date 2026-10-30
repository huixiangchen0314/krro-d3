package top.kzre.krro.d3.core.util;

import java.util.Arrays;

/**
 * long → int 原始类型哈希表——开放寻址——线性探测——Robin Hood。
 *
 * <p><b>空槽约定</b>：value = {@link Integer#MIN_VALUE} 表示空槽，
 * 不能存储 {@code Integer.MIN_VALUE} 作为合法值。
 *
 * <p><b>线程契约</b>：非线程安全。
 */
public final class LongIntMap {

    private static final int EMPTY_VALUE = Integer.MIN_VALUE;

    private long[] keys;
    private int[]  values;
    private int    mask;
    private int    size;
    private int    threshold;

    public LongIntMap() { this(16); }

    public LongIntMap(int expectedSize) {
        int cap = RobinHoodHash.capacityFor(expectedSize);
        this.keys      = new long[cap];
        this.values    = new int[cap];
        Arrays.fill(this.values, EMPTY_VALUE);
        this.mask      = cap - 1;
        this.threshold = RobinHoodHash.thresholdFor(cap);
        this.size      = 0;
    }

    private LongIntMap(long[] keys, int[] values, int mask, int threshold, int size) {
        this.keys      = keys;
        this.values    = values;
        this.mask      = mask;
        this.threshold = threshold;
        this.size      = size;
    }

    // ═══════════════════════════════════════════════
    // 复用
    // ═══════════════════════════════════════════════

    public void reset() {
        clear();
    }

    public void ensureCapacity(int expected) {
        int needed = RobinHoodHash.capacityFor(expected);
        if (keys.length < needed) {
            this.keys      = new long[needed];
            this.values    = new int[needed];
            Arrays.fill(this.values, EMPTY_VALUE);
            this.mask      = needed - 1;
            this.threshold = RobinHoodHash.thresholdFor(needed);
        }
        clear();
    }

    // ═══════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════

    public int get(long key) {
        int mask = this.mask;
        int idx = RobinHoodHash.idealIndex(key, mask);
        int dib = 0;

        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) return values[idx];

            int existingDib = RobinHoodHash.dib(
                    idx, RobinHoodHash.idealIndex(keys[idx], mask), mask);
            if (existingDib < dib) return EMPTY_VALUE;

            idx = RobinHoodHash.nextIndex(idx, mask);
            dib++;
        }
        return EMPTY_VALUE;
    }

    public boolean containsKey(long key) {
        int mask = this.mask;
        int idx = RobinHoodHash.idealIndex(key, mask);
        int dib = 0;

        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) return true;

            int existingDib = RobinHoodHash.dib(
                    idx, RobinHoodHash.idealIndex(keys[idx], mask), mask);
            if (existingDib < dib) return false;

            idx = RobinHoodHash.nextIndex(idx, mask);
            dib++;
        }
        return false;
    }

    // ═══════════════════════════════════════════════
    // 插入
    // ═══════════════════════════════════════════════

    public int put(long key, int value) {
        if (value == EMPTY_VALUE) {
            throw new IllegalArgumentException("value must not be Integer.MIN_VALUE");
        }
        if (size >= threshold) grow();
        return insert(key, value);
    }

    private int insert(long key, int value) {
        int mask = this.mask;
        int idx = RobinHoodHash.idealIndex(key, mask);
        long curKey = key;
        int  curVal = value;
        int  dib = 0;

        while (true) {
            if (values[idx] == EMPTY_VALUE) {
                keys[idx]   = curKey;
                values[idx] = curVal;
                size++;
                return EMPTY_VALUE;
            }

            if (keys[idx] == curKey) {
                int old = values[idx];
                values[idx] = curVal;
                return old;
            }

            int existingDib = RobinHoodHash.dib(
                    idx, RobinHoodHash.idealIndex(keys[idx], mask), mask);
            if (existingDib < dib) {
                long tk = keys[idx];
                keys[idx] = curKey;
                curKey = tk;

                int tv = values[idx];
                values[idx] = curVal;
                curVal = tv;

                dib = existingDib;
            }

            idx = RobinHoodHash.nextIndex(idx, mask);
            dib++;
        }
    }

    // ═══════════════════════════════════════════════
    // 状态
    // ═══════════════════════════════════════════════

    public int     size()    { return size; }
    public boolean isEmpty() { return size == 0; }
    public int     capacity() { return keys.length; }

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

        int newCap = RobinHoodHash.growCapacity(keys.length);
        keys      = new long[newCap];
        values    = new int[newCap];
        Arrays.fill(values, EMPTY_VALUE);
        mask      = newCap - 1;
        threshold = RobinHoodHash.thresholdFor(newCap);
        size      = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldValues[i] != EMPTY_VALUE) {
                insert(oldKeys[i], oldValues[i]);
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 复制
    // ═══════════════════════════════════════════════

    public LongIntMap copy() {
        int n = this.keys.length;
        long[] newKeys   = new long[n];
        int[]  newValues = new int[n];
        System.arraycopy(this.keys,   0, newKeys,   0, n);
        System.arraycopy(this.values, 0, newValues, 0, n);
        return new LongIntMap(newKeys, newValues, this.mask, this.threshold, this.size);
    }

    // ═══════════════════════════════════════════════
    // 删除
    // ═══════════════════════════════════════════════

    public int remove(long key) {
        int mask = this.mask;
        int idx = RobinHoodHash.idealIndex(key, mask);
        int dib = 0;

        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) {
                int oldValue = values[idx];
                backwardShiftDelete(idx);
                size--;
                return oldValue;
            }

            int existingDib = RobinHoodHash.dib(
                    idx, RobinHoodHash.idealIndex(keys[idx], mask), mask);
            if (existingDib < dib) return EMPTY_VALUE;

            idx = RobinHoodHash.nextIndex(idx, mask);
            dib++;
        }
        return EMPTY_VALUE;
    }

    private void backwardShiftDelete(int idx) {
        int mask = this.mask;
        values[idx] = EMPTY_VALUE;

        int hole = idx;
        int next = RobinHoodHash.nextIndex(idx, mask);

        while (values[next] != EMPTY_VALUE) {
            if (!RobinHoodHash.canBackwardShift(keys[next], next, mask)) break;

            keys[hole]   = keys[next];
            values[hole] = values[next];
            values[next] = EMPTY_VALUE;

            hole = next;
            next = RobinHoodHash.nextIndex(next, mask);
        }
    }
}