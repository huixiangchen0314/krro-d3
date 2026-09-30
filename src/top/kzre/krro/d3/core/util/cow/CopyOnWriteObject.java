package top.kzre.krro.d3.core.util.cow;

/**
 * 属性级 COW 单元——包装任意引用计数资源。
 *
 * <p><b>Resource 契约</b>：
 * <ul>
 *   <li>{@code copy()} —— 创建独立副本——引用计数 1</li>
 *   <li>{@code acquire()} —— 引用 +1</li>
 *   <li>{@code dispose()} —— 引用 -1——归零时销毁</li>
 *   <li>{@code isExclusive()} —— 引用计数 == 1</li>
 * </ul>
 *
 * <p><b>线程契约</b>：写路径 + 生命周期 synchronized——
 * 读路径同样 synchronized（返回 data 引用）。
 */
public final class CopyOnWriteObject<T extends Resource<T>>
        implements CopyOnWrite<CopyOnWriteObject<T>> {

    private final Object lock = new Object();
    private T data;

    public CopyOnWriteObject(T data) {
        if (data == null) throw new NullPointerException("data");
        this.data = data;
    }

    /** 只读快照——调用方不得修改。 */
    public T getSnapshot() {
        synchronized (lock) {
            return data;
        }
    }

    /**
     * 写访问——独占时直接返回——共享时复制。
     */
    public T getForWrite() {
        synchronized (lock) {
            if (data.isExclusive()) {
                return data;
            }
            T old = data;
            this.data = old.copy();
            old.dispose();
            return data;
        }
    }

    @Override
    public CopyOnWriteObject<T> shared() {
        synchronized (lock) {
            data.acquire();
            return new CopyOnWriteObject<>(data);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (data == null) return;
            data.dispose();
            data = null;
        }
    }
}