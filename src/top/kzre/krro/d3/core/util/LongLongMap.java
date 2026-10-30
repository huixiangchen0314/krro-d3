package top.kzre.krro.d3.core.util;

import java.util.Arrays;

/**
 * long → long 原始类型哈希表——开放寻址——线性探测。
 *
 * <p><b>空槽约定</b>：value = {@link Long#MIN_VALUE} 表示空槽。
 * 因此不能存储 {@code Long.MIN_VALUE} 作为合法值。
 *
 * <p><b>复用</b>：{@link #ensureCapacity(int)} 保留数组容量——
 * 避免大数组反复分配进入老年代。配合 ThreadLocal 使用。
 *
 *
 * <p><b>线程契约</b>：非线程安全。
 */
public final class LongLongMap {

    private static final long EMPTY_VALUE = Long.MIN_VALUE;

    private long[] keys;
    private long[] values;
    private int    mask;
    private int    size;
    private int    threshold;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public LongLongMap() { this(16); }

    public LongLongMap(int expectedSize) {
        int cap = 1;
        while (cap < expectedSize * 2) cap <<= 1;   // 负载因子 0.5
        this.keys      = new long[cap];
        this.values    = new long[cap];
        // new long[] 默认全 0——必须初始化为 EMPTY_VALUE，否则探测循环死循环
        Arrays.fill(this.values, EMPTY_VALUE);
        this.mask      = cap - 1;
        this.threshold = cap / 2;
        this.size      = 0;
    }

    // ═══════════════════════════════════════════════
    // 复用——不清数组——只重置状态
    // ═══════════════════════════════════════════════

    /**
     * 重置为空——保留当前容量。
     */
    public void reset() {
        clear();
    }

    /**
     * 确保容量至少能容纳 {@code expected} 个条目——不足则扩容。
     * 现有内容被清除。
     */
    public void ensureCapacity(int expected) {
        int needed = 1;
        while (needed < expected * 2) needed <<= 1;

        if (keys.length < needed) {
            this.keys      = new long[needed];
            this.values    = new long[needed];
            this.mask      = needed - 1;
            this.threshold = needed / 2;
        }
        clear();
    }

    // ═══════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════

    public long get(long key) {
        int idx = hash(key) & mask;
        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) return values[idx];
            idx = (idx + 1) & mask;
        }
        return EMPTY_VALUE;
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

    public long put(long key, long value) {
        if (value == EMPTY_VALUE) {
            throw new IllegalArgumentException("value must not be Long.MIN_VALUE");
        }
        if (size >= threshold) grow();

        int idx = hash(key) & mask;
        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) {
                long old = values[idx];
                values[idx] = value;
                return old;
            }
            idx = (idx + 1) & mask;
        }

        keys[idx]   = key;
        values[idx] = value;
        size++;
        return EMPTY_VALUE;
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
        long[] oldValues = values;

        int newCap = keys.length << 1;
        keys      = new long[newCap];
        values    = new long[newCap];
        // 新数组必须初始化为 EMPTY_VALUE——否则 put 旧元素时死循环
        Arrays.fill(values, EMPTY_VALUE);
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

    private LongLongMap(long[] keys, long[] values, int mask, int threshold, int size) {
        this.keys      = keys;
        this.values    = values;
        this.mask      = mask;
        this.threshold = threshold;
        this.size      = size;
    }

    public LongLongMap copy() {
        int n = this.keys.length;
        long[] newKeys   = new long[n];
        long[] newValues = new long[n];
        System.arraycopy(this.keys,   0, newKeys,   0, n);
        System.arraycopy(this.values, 0, newValues, 0, n);
        return new LongLongMap(newKeys, newValues, this.mask, this.threshold, this.size);
    }

    /**
     * 删除 key。存在返回旧值，不存在返回 {@link Long#MIN_VALUE}。
     *
     * <p><b>backward-shift 删除</b>：删除后把后面连续的元素往前挪，
     * 保持探测链连续——不能简单置空，否则后面同哈希的元素不可达。
     *
     * <p><b>复杂度</b>：O(删除位置之后的链长)。典型情况下是常数级。
     */
    public long remove(long key) {
        int idx = hash(key) & mask;
        while (values[idx] != EMPTY_VALUE) {
            if (keys[idx] == key) {
                long oldValue = values[idx];
                backwardShiftDelete(idx);
                size--;
                return oldValue;
            }
            idx = (idx + 1) & mask;
        }
        return EMPTY_VALUE;   // 不存在
    }

    /**
     * 删除 {@code idx} 位置的元素，并把后面能前移的元素挪过来。
     *
     * <p><b>前移判据</b>：位置 {@code next} 的元素能移到 {@code hole}，
     * 当且仅当它从 {@code hole} 开始查找仍然可达——即它的理想位置
     * {@code ideal = hash(key) & mask} 不在 {@code (hole, next)} 的
     * 环形区间内。
     */
    private void backwardShiftDelete(int idx) {
        values[idx] = EMPTY_VALUE;   // 先清空删除位置

        int hole = idx;
        int next = (idx + 1) & mask;

        while (values[next] != EMPTY_VALUE) {
            int ideal = hash(keys[next]) & mask;

            // 环形区间判断：ideal 是否在 (hole, next) 内
            boolean canMove = (hole < next)
                    ? (ideal <= hole || ideal > next)   // 不环绕
                    : (ideal <= hole && ideal > next);  // 环绕

            if (canMove) {
                keys[hole]   = keys[next];
                values[hole] = values[next];
                values[next] = EMPTY_VALUE;
                hole = next;
            }
            next = (next + 1) & mask;
        }
    }

}