package top.kzre.krro.d3.core.geometry.bmesh;

import top.kzre.krro.d3.core.util.IntList;
import top.kzre.krro.d3.core.util.IntQueue;

/**
 * 分段索引分配器——固定段大小——FIFO 空闲队列。
 *
 * <p><b>索引分配</b>：
 * <ul>
 *   <li>空闲队列非空 → 出队复用</li>
 *   <li>否则 → 追加新索引（nextIndex++）</li>
 * </ul>
 *
 * <p><b>段活跃计数</b>：每段维护活跃元素计数。
 * <ul>
 *   <li>0 → 1：段从空变为非空</li>
 *   <li>1 → 0：段从非空变为空</li>
 * </ul>
 * 本类只维护计数——段底层分配 / 释放由编辑层拉模式处理——
 * 无监听器——与 COW 隔离兼容。
 *
 * <p><b>段索引</b>：segmentIndex = index / segmentSize——
 * 段列表只追加——segmentIndex 永久稳定。
 *
 * <p><b>线程契约</b>：非线程安全。
 */
public final class SegmentizedIndexAllocator {

    // ═══════════════════════════════════════════════
    // 字段
    // ═══════════════════════════════════════════════

    /** 每段的元素数。 */
    private final int segmentSize;

    /** 空闲索引队列——FIFO。 */
    private final IntQueue freeQueue = new IntQueue();

    /** 下一个未使用过的索引——单调递增。 */
    private int nextIndex;

    /** 每段活跃元素计数——IntList 自动扩容。 */
    private final IntList segmentActive = new IntList();

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public SegmentizedIndexAllocator(int segmentSize) {
        if (segmentSize <= 0) {
            throw new IllegalArgumentException(
                    "segmentSize must be > 0: " + segmentSize);
        }
        this.segmentSize = segmentSize;
        this.nextIndex   = 0;
    }

    // ═══════════════════════════════════════════════
    // 分配 / 释放
    // ═══════════════════════════════════════════════

    /**
     * 分配索引——优先复用空闲——否则追加新索引。
     *
     * @return 索引——非负
     */
    public int allocate() {
        int index;

        if (!freeQueue.isEmpty()) {
            index = freeQueue.dequeue();
        } else {
            index = nextIndex++;
        }

        int segId = index / segmentSize;
        ensureSegment(segId);

        int active = segmentActive.get(segId);
        segmentActive.set(segId, active + 1);

        return index;
    }

    /**
     * 释放索引——进入空闲队列。
     *
     * @param index 待释放索引——须 &gt;= 0
     */
    public void free(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0: " + index);
        }

        int segId = index / segmentSize;
        if (segId >= segmentActive.size()) {
            throw new IllegalArgumentException(
                    "index never allocated: " + index);
        }

        int active = segmentActive.get(segId);
        if (active <= 0) {
            throw new IllegalStateException(
                    "segment has no active element: segId=" + segId);
        }

        segmentActive.set(segId, active - 1);

        freeQueue.enqueue(index);
    }

    // ═══════════════════════════════════════════════
    // 索引 → 段 / 局部
    // ═══════════════════════════════════════════════

    /** 索引所在的段索引。 */
    public int segmentOf(int index) {
        return index / segmentSize;
    }

    /** 索引在段内的局部偏移。 */
    public int localOf(int index) {
        return index % segmentSize;
    }

    /** 段大小。 */
    public int getSegmentSize() {
        return segmentSize;
    }

    // ═══════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════

    /** 已分配的索引总数（含空闲）。 */
    public int getAllocatedCount() {
        return nextIndex;
    }

    /** 活跃元素数。 */
    public int getActiveCount() {
        return nextIndex - freeQueue.size();
    }

    /** 空闲元素数。 */
    public int getFreeCount() {
        return freeQueue.size();
    }

    /** 段总数（含曾经分配过的空段）。 */
    public int getSegmentCount() {
        return segmentActive.size();
    }

    /** 给定段索引——是否活跃（有活跃元素）。 */
    public boolean isSegmentActive(int segmentIndex) {
        if (segmentIndex < 0 || segmentIndex >= segmentActive.size()) {
            return false;
        }
        return segmentActive.get(segmentIndex) > 0;
    }

    /** 给定段索引——活跃元素数。 */
    public int getSegmentActiveCount(int segmentIndex) {
        if (segmentIndex < 0 || segmentIndex >= segmentActive.size()) {
            return 0;
        }
        return segmentActive.get(segmentIndex);
    }

    // ═══════════════════════════════════════════════
    // 内部——段容量
    // ═══════════════════════════════════════════════

    /**
     * 确保 segmentActive 覆盖到 segmentIndex——
     * 不足则用 0 填充——IntList 自动扩容。
     */
    private void ensureSegment(int segmentIndex) {
        while (segmentActive.size() <= segmentIndex) {
            segmentActive.add(0);
        }
    }

    /**
     * 深拷贝——nextIndex / segmentActive / freeQueue 全部复制。
     *
     * <p>用于 BMesh.shared() —— 两个 BMesh 各自独立演化。
     */
    public SegmentizedIndexAllocator copy() {
        SegmentizedIndexAllocator c = new SegmentizedIndexAllocator(segmentSize);
        c.nextIndex = this.nextIndex;

        // segmentActive —— IntList 复制
        int n = this.segmentActive.size();
        c.segmentActive.addAll(this.segmentActive.rawArray(), 0, n);

        // freeQueue —— IntQueue 复制
        c.freeQueue.copyFrom(this.freeQueue);

        return c;
    }
}