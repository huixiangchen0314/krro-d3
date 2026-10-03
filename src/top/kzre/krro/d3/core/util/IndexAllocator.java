package top.kzre.krro.d3.core.util;

/**
 * 索引分配器——FIFO 空闲队列。
 *
 * <p><b>索引分配</b>：
 * <ul>
 *   <li>空闲队列非空 → 出队复用</li>
 *   <li>否则 → 追加新索引（nextIndex++）</li>
 * </ul>
 *
 * <p>与 {@link SegmentizedIndexAllocator} 的差别：
 * 不做分段跟踪——无 segmentActive——更轻量。
 * 适用于不需要按段管理的场景。
 *
 * <p><b>线程契约</b>：非线程安全。
 */
public final class IndexAllocator {

    // ═══════════════════════════════════════════════
    // 字段
    // ═══════════════════════════════════════════════

    /** 空闲索引队列——FIFO。 */
    private final IntQueue freeQueue = new IntQueue();

    /** 下一个未使用过的索引——单调递增。 */
    private int nextIndex;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public IndexAllocator() {
        this.nextIndex = 0;
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
        if (!freeQueue.isEmpty()) {
            return freeQueue.dequeue();
        }
        return nextIndex++;
    }

    /**
     * 释放索引——进入空闲队列。
     *
     * @param index 待释放索引——须 &gt;= 0 且 &lt; nextIndex
     */
    public void free(int index) {
        if (index < 0 || index >= nextIndex) {
            throw new IllegalArgumentException(
                    "index out of range: " + index + ", nextIndex=" + nextIndex);
        }
        freeQueue.enqueue(index);
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

    /** 是否为空——无活跃元素。 */
    public boolean isEmpty() {
        return getActiveCount() == 0;
    }

    // ═══════════════════════════════════════════════
    // 重置
    // ═══════════════════════════════════════════════

    /**
     * 重置为空——保留底层数组容量。
     */
    public void reset() {
        freeQueue.clear();
        nextIndex = 0;
    }

    // ═══════════════════════════════════════════════
    // 深拷贝
    // ═══════════════════════════════════════════════

    /**
     * 深拷贝——nextIndex / freeQueue 全部复制。
     *
     * <p>用于 COW——两个分配器各自独立演化。
     */
    public IndexAllocator copy() {
        IndexAllocator c = new IndexAllocator();
        c.nextIndex = this.nextIndex;
        c.freeQueue.copyFrom(this.freeQueue);
        return c;
    }
}