package top.kzre.krro.d3.core.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * long → Object 哈希表——开放寻址——线性探测——Robin Hood。
 *
 * <p><b>空槽约定</b>：value = {@code null} 表示空槽，
 * 不能存储 {@code null} 作为合法值。
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
        int cap = RobinHoodHash.capacityFor(expectedSize);
        this.keys      = new long[cap];
        this.values    = new Object[cap];   // 默认 null 即空槽
        this.mask      = cap - 1;
        this.threshold = RobinHoodHash.thresholdFor(cap);
        this.size      = 0;
    }

    private LongObjectMap(long[] keys, Object[] values,
                          int mask, int threshold, int size) {
        this.keys      = keys;
        this.values    = values;
        this.mask      = mask;
        this.threshold = threshold;
        this.size      = size;
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
        int needed = RobinHoodHash.capacityFor(expected);
        if (keys.length < needed) {
            this.keys      = new long[needed];
            this.values    = new Object[needed];
            this.mask      = needed - 1;
            this.threshold = RobinHoodHash.thresholdFor(needed);
        }
        clear();
    }

    // ═══════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public V get(long key) {
        int mask = this.mask;
        int idx = RobinHoodHash.idealIndex(key, mask);
        int dib = 0;

        while (values[idx] != null) {
            if (keys[idx] == key) return (V) values[idx];

            // Robin Hood 提前终止
            int existingDib = RobinHoodHash.dib(
                    idx, RobinHoodHash.idealIndex(keys[idx], mask), mask);
            if (existingDib < dib) return null;

            idx = RobinHoodHash.nextIndex(idx, mask);
            dib++;
        }
        return null;
    }

    /** 返回默认值——key 不存在时。 */
    public V getOrDefault(long key, V defaultValue) {
        V v = get(key);
        return v != null ? v : defaultValue;
    }

    public boolean containsKey(long key) {
        int mask = this.mask;
        int idx = RobinHoodHash.idealIndex(key, mask);
        int dib = 0;

        while (values[idx] != null) {
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

    /** 返回旧值——key 原本不存在返回 null。 */
    public V put(long key, V value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (size >= threshold) grow();
        return insert(key, value);
    }

    /**
     * 不做扩容检查的插入——供 put 和 grow 复用。
     */
    @SuppressWarnings("unchecked")
    private V insert(long key, V value) {
        int mask = this.mask;
        int idx = RobinHoodHash.idealIndex(key, mask);
        long curKey = key;
        V    curVal = value;
        int  dib = 0;

        while (true) {
            if (values[idx] == null) {
                keys[idx]   = curKey;
                values[idx] = curVal;
                size++;
                return null;
            }

            if (keys[idx] == curKey) {
                V old = (V) values[idx];
                values[idx] = curVal;
                return old;
            }

            int existingDib = RobinHoodHash.dib(
                    idx, RobinHoodHash.idealIndex(keys[idx], mask), mask);
            if (existingDib < dib) {
                // 抢富济贫：当前元素 DIB 更大，交换后继续向后推
                long tk = keys[idx];
                keys[idx] = curKey;
                curKey = tk;

                V tv = (V) values[idx];
                values[idx] = curVal;
                curVal = tv;

                dib = existingDib;
            }

            idx = RobinHoodHash.nextIndex(idx, mask);
            dib++;
        }
    }

    // ═══════════════════════════════════════════════
    // 删除
    // ═══════════════════════════════════════════════

    /** 删除——返回旧值——不存在返回 null。 */
    @SuppressWarnings("unchecked")
    public V remove(long key) {
        int mask = this.mask;
        int idx = RobinHoodHash.idealIndex(key, mask);
        int dib = 0;

        while (values[idx] != null) {
            if (keys[idx] == key) {
                V oldValue = (V) values[idx];
                backwardShiftDelete(idx);
                size--;
                return oldValue;
            }

            int existingDib = RobinHoodHash.dib(
                    idx, RobinHoodHash.idealIndex(keys[idx], mask), mask);
            if (existingDib < dib) return null;

            idx = RobinHoodHash.nextIndex(idx, mask);
            dib++;
        }
        return null;   // 不存在
    }

    private void backwardShiftDelete(int idx) {
        int mask = this.mask;
        values[idx] = null;   // 先清空删除位置

        int hole = idx;
        int next = RobinHoodHash.nextIndex(idx, mask);

        while (values[next] != null) {
            if (!RobinHoodHash.canBackwardShift(keys[next], next, mask)) break;

            keys[hole]   = keys[next];
            values[hole] = values[next];
            values[next] = null;

            hole = next;
            next = RobinHoodHash.nextIndex(next, mask);
        }
    }

    // ═══════════════════════════════════════════════
    // 遍历
    // ═══════════════════════════════════════════════

    @FunctionalInterface
    public interface LongObjectVisitor<V> {
        void visit(long key, V value);
    }

    @SuppressWarnings("unchecked")
    public void forEach(LongObjectVisitor<V> visitor) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                visitor.visit(keys[i], (V) values[i]);
            }
        }
    }

    public final class MapEntry {
        private final long key;
        private final V obj;

        public MapEntry(long key, V obj) {
            this.key = key;
            this.obj = obj;
        }

        public long getKey() {
            return key;
        }

        public V getValue() {
            return obj;
        }
    }

    public Iterator<MapEntry> iterator() {
        return new Iterator<MapEntry>() {

            /** 下一个非空槽下标；等于 values.length 表示已到末尾。 */
            private int next = nextFilled(0);

            private int nextFilled(int from) {
                while (from < values.length
                        && values[from] == null) {
                    from++;
                }
                return from;
            }

            @Override
            public boolean hasNext() {
                return next < values.length;
            }

            @Override
            @SuppressWarnings("unchecked")
            public MapEntry next() {
                if (next >= values.length) {
                    throw new NoSuchElementException("LongObjectMap iterator exhausted");
                }
                int i = next;
                MapEntry entry = new MapEntry(keys[i], (V) values[i]);
                next = nextFilled(i + 1);
                return entry;
            }
        };
    }

    // ═══════════════════════════════════════════════
    // 状态
    // ═══════════════════════════════════════════════

    public int     size()    { return size; }
    public boolean isEmpty() { return size == 0; }
    public int     capacity() { return keys.length; }

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

        int newCap = RobinHoodHash.growCapacity(keys.length);
        keys      = new long[newCap];
        values    = new Object[newCap];
        mask      = newCap - 1;
        threshold = RobinHoodHash.thresholdFor(newCap);
        size      = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldValues[i] != null) {
                @SuppressWarnings("unchecked")
                V v = (V) oldValues[i];
                insert(oldKeys[i], v);
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 复制
    // ═══════════════════════════════════════════════

    /**
     * 复制——返回一个独立的新实例。
     *
     * <p>只复制层级结构——不复制 value 对象。
     * 新旧 map 共享同一批 value。
     */
    public LongObjectMap<V> copy() {
        int n = this.keys.length;
        long[]   newKeys   = new long[n];
        Object[] newValues = new Object[n];
        System.arraycopy(this.keys,   0, newKeys,   0, n);
        System.arraycopy(this.values, 0, newValues, 0, n);
        return new LongObjectMap<>(newKeys, newValues, this.mask, this.threshold, this.size);
    }
}