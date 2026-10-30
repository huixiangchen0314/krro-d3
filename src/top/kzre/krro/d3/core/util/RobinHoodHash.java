package top.kzre.krro.d3.core.util;

/**
 * Robin Hood 哈希领域工具。
 *
 * <p>只放与 value 类型无关的纯函数：哈希、理想位置、DIB、
 * 容量/阈值计算、扩容容量、删除前移判据。
 *
 * <p>value 数组类型和空槽约定由各变体自己维护：
 * <ul>
 *   <li>{@code LongLongMap}：{@code long[]}，空槽 {@link Long#MIN_VALUE}</li>
 *   <li>{@code LongIntMap}：{@code int[]}，空槽 {@link Integer#MIN_VALUE}</li>
 *   <li>{@code LongObjectMap}：{@code Object[]}，空槽 {@code null}</li>
 * </ul>
 */
public final class RobinHoodHash {

    private RobinHoodHash() {}

    /** 默认负载因子 3/4。 */
    public static final int DEFAULT_LOAD_NUM = 3;
    public static final int DEFAULT_LOAD_DEN = 4;

    /** 最小容量，必须是 2 的幂。 */
    public static final int MIN_CAPACITY = 2;

    // ═══════════════════════════════════════════════
    // 哈希与探测
    // ═══════════════════════════════════════════════

    /** Murmur3 finalizer。 */
    public static int hash(long x) {
        x = (x ^ (x >>> 33)) * 0xff51afd7ed558ccdL;
        x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
        x = x ^ (x >>> 33);
        return (int) x;
    }

    /** key 的理想槽位。 */
    public static int idealIndex(long key, int mask) {
        return hash(key) & mask;
    }

    /** 环形 DIB：index 到 idealIndex 的距离，范围 [0, mask]。 */
    public static int dib(int index, int idealIndex, int mask) {
        return (index - idealIndex) & mask;
    }

    /** 线性探测的下一槽位。 */
    public static int nextIndex(int index, int mask) {
        return (index + 1) & mask;
    }

    /**
     * 删除时 {@code next} 位置的元素能否前移到 {@code hole}。
     * DIB 为 0 表示已在理想位置，不能再前移。
     */
    public static boolean canBackwardShift(long key, int next, int mask) {
        return dib(next, idealIndex(key, mask), mask) != 0;
    }

    // ═══════════════════════════════════════════════
    // 容量与阈值
    // ═══════════════════════════════════════════════

    public static int capacityFor(int expectedSize) {
        return capacityFor(expectedSize, DEFAULT_LOAD_NUM, DEFAULT_LOAD_DEN);
    }

    /**
     * 返回满足 {@code capacity * loadNum / loadDen >= expectedSize}
     * 的最小 2 的幂容量。
     */
    public static int capacityFor(int expectedSize, int loadNum, int loadDen) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be >= 0: " + expectedSize);
        }
        if (loadNum <= 0 || loadDen <= 0 || loadNum > loadDen) {
            throw new IllegalArgumentException(
                    "invalid load factor: " + loadNum + "/" + loadDen);
        }
        if (expectedSize == 0) return MIN_CAPACITY;

        long min = ((long) expectedSize * loadDen + loadNum - 1) / loadNum;
        int cap = MIN_CAPACITY;
        while (cap < min) {
            cap <<= 1;
            if (cap <= 0) {
                throw new IllegalStateException(
                        "capacity overflow for expectedSize=" + expectedSize);
            }
        }
        return cap;
    }

    public static int thresholdFor(int capacity) {
        return thresholdFor(capacity, DEFAULT_LOAD_NUM, DEFAULT_LOAD_DEN);
    }

    public static int thresholdFor(int capacity, int loadNum, int loadDen) {
        return (int) ((long) capacity * loadNum / loadDen);
    }

    /** 扩容后的容量，溢出时抛异常。 */
    public static int growCapacity(int oldCapacity) {
        int newCap = oldCapacity << 1;
        if (newCap <= 0) {
            throw new IllegalStateException("map too large, capacity=" + oldCapacity);
        }
        return newCap;
    }
}