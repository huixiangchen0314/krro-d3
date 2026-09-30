package top.kzre.krro.d3.core.util;

import top.kzre.krro.d3.core.util.IntList;

/**
 * int 队列——FIFO。
 *
 * <p><b>结构</b>：基于 {@link IntList}——只追加——头部指针
 * 标记已消费位置。头部到达尾部时——清空重置。
 *
 * <p><b>语义</b>：入队尾——出队头——FIFO。
 *
 * <p><b>用途</b>：BMesh 元素空闲列表 / 任何需要 int 队列的场景。
 *
 * <p><b>线程契约</b>：非线程安全。
 */
public final class IntQueue {

    private final IntList buffer = new IntList();
    private int head;

    // ═══════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════

    public boolean isEmpty() { return head >= buffer.size(); }

    public int size() { return buffer.size() - head; }

    // ═══════════════════════════════════════════════
    // 入队
    // ═══════════════════════════════════════════════

    /**
     * 入队——加入尾部。
     *
     * @param value 任意 int
     */
    public void enqueue(int value) {
        buffer.add(value);
    }

    // ═══════════════════════════════════════════════
    // 出队
    // ═══════════════════════════════════════════════

    /**
     * 出队——从头部取。
     *
     * @return 队头值
     * @throws IllegalStateException 队列为空
     */
    public int dequeue() {
        if (isEmpty()) {
            throw new IllegalStateException("IntQueue is empty");
        }
        int value = buffer.get(head++);
        if (head == buffer.size()) {
            buffer.clear();
            head = 0;
        }
        return value;
    }

    // ═══════════════════════════════════════════════
    // 清空
    // ═══════════════════════════════════════════════

    public void clear() {
        buffer.clear();
        head = 0;
    }

    /**
     * 从另一个 IntQueue 复制状态——用于深拷贝。
     */
    public void copyFrom(IntQueue src) {
        this.buffer.clear();
        this.head = src.head;

        int n = src.buffer.size();
        this.buffer.addAll(src.buffer.rawArray(), 0, n);
    }
}