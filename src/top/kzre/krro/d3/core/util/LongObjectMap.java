package top.kzre.krro.d3.core.util;

import java.util.Arrays;

/**
 * long → Object 原始类型哈希表——开放寻址——线性探测。
 *
 * <p><b>空槽约定</b>：value = {@code null} 表示空槽。
 * 因此不能存储 {@code null} 作为合法值。
 *
 * <p><b>复用</b>：{@link #ensureCapacity(int)} 保留数组容量——
 * 避免大数组反复分配进入老年代。配合 ThreadLocal 使用。
 *
 * <p><b>线程契约</b>：非线程安全。
 *
 * @param <V> 值类型
 */
public final class LongObjectMap<V> {

    private long[]   keys;
    private Object[] values;
    private int      mask;
    private int      size;
    private int      threshold;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public LongObjectMap() { this(16); }

    public LongObjectMap(int expectedSize) {
        int cap = 1;
        while (cap < expectedSize * 2) cap <<= 1;   // 负载因子 0.5
        this.keys      = new long[cap];
        this.values    = new Object[cap];
        this.mask      = cap - 1;
        this.threshold = cap / 2;
        this.size      = 0;
    }

    // ═══════════════════════════════════════════════
    // 复用
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
            this.values    = new Object[needed];
            this.mask      = needed - 1;
            this.threshold = needed / 2;
        }
        clear();
    }

    // ═══════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public V get(long key) {
        int idx = hash(key) & mask;
        while (values[idx] != null) {
            if (keys[idx] == key) return (V) values[idx];
            idx = (idx + 1) & mask;
        }
        return null;
    }

    /** 返回默认值——key 不存在时。 */
    public V getOrDefault(long key, V defaultValue) {
        V v = get(key);
        return v != null ? v : defaultValue;
    }

    public boolean containsKey(long key) {
        int idx = hash(key) & mask;
        while (values[idx] != null) {
            if (keys[idx] == key) return true;
            idx = (idx + 1) & mask;
        }
        return false;
    }

    // ═══════════════════════════════════════════════
    // 插入
    // ═══════════════════════════════════════════════

    /** 返回旧值——key 原本不存在返回 null。 */
    @SuppressWarnings("unchecked")
    public V put(long key, V value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (size >= threshold) grow();

        int idx = hash(key) & mask;
        while (values[idx] != null) {
            if (keys[idx] == key) {
                V old = (V) values[idx];
                values[idx] = value;
                return old;
            }
            idx = (idx + 1) & mask;
        }

        keys[idx]   = key;
        values[idx] = value;
        size++;
        return null;
    }

    /** 删除——返回旧值——不存在返回 null。 */
    @SuppressWarnings("unchecked")
    public V remove(long key) {
        int idx = hash(key) & mask;
        while (values[idx] != null) {
            if (keys[idx] == key) {
                V old = (V) values[idx];
                values[idx] = null;
                size--;

                // 重建后续连续段——避免探测链断裂
                int next = (idx + 1) & mask;
                while (values[next] != null) {
                    long k = keys[next];
                    Object v = values[next];
                    values[next] = null;
                    size--;
                    put(k, (V) v);
                    next = (next + 1) & mask;
                }
                return old;
            }
            idx = (idx + 1) & mask;
        }
        return null;
    }

    // ═══════════════════════════════════════════════
    // 遍历
    // ═══════════════════════════════════════════════

    @FunctionalInterface
    public interface LongObjectVisitor<V> {
        void visit(long key, V value);
    }

    public void forEach(LongObjectVisitor<V> visitor) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                @SuppressWarnings("unchecked")
                V v = (V) values[i];
                visitor.visit(keys[i], v);
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 状态
    // ═══════════════════════════════════════════════

    public int     size()    { return size; }
    public boolean isEmpty() { return size == 0; }

    public int capacity() { return keys.length; }

    public void clear() {
        Arrays.fill(values, null);
        size = 0;
    }

    // ═══════════════════════════════════════════════
    // 扩容
    // ═══════════════════════════════════════════════

    private void grow() {
        long[]   oldKeys   = keys;
        Object[] oldValues = values;

        int newCap = keys.length << 1;
        keys      = new long[newCap];
        values    = new Object[newCap];
        mask      = newCap - 1;
        threshold = newCap / 2;
        size      = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldValues[i] != null) {
                @SuppressWarnings("unchecked")
                V v = (V) oldValues[i];
                put(oldKeys[i], v);
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 哈希
    // ═══════════════════════════════════════════════

    private static int hash(long x) {
        x = (x ^ (x >>> 33)) * 0xff51afd7ed558ccdL;
        x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
        x = (x ^ (x >>> 33));
        return (int) x;
    }

    /**
     * 复制——返回一个独立的新实例。
     *
     * <p>只复制层级结构——不复制 value 对象。
     * 新旧 map 共享同一批 value。
     */
    public LongObjectMap<V> copy() {
        LongObjectMap<V> dst = new LongObjectMap<>();
        dst.keys      = new long[this.keys.length];
        dst.values    = new Object[this.values.length];
        dst.mask      = this.mask;
        dst.threshold = this.threshold;
        dst.size      = this.size;

        System.arraycopy(this.keys,   0, dst.keys,   0, this.keys.length);
        System.arraycopy(this.values, 0, dst.values, 0, this.values.length);
        return dst;
    }
}