package top.kzre.krro.d3.core.util.cow;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 引用计数资源基类。
 *
 * <p><b>契约</b>：
 * <ul>
 *   <li>构造时引用计数 = 1</li>
 *   <li>{@link #acquire()} —— +1</li>
 *   <li>{@link #dispose()} —— -1——归零时调 {@link #onDispose()}</li>
 *   <li>{@link #isExclusive()} —— 计数 == 1</li>
 * </ul>
 *
 * <p><b>非幂等</b>：计数归零后再 dispose 是 bug——抛
 * {@link IllegalStateException}。
 *
 * <p><b>线程契约</b>：acquire / dispose / isExclusive 均通过
 * {@link AtomicInteger} 保证原子。
 *
 * <p>子类实现 {@link #onDispose()} —— 归零时释放底层资源。
 *
 * @param <T> 具体资源类型——用于 {@link Resource#copy()} 的返回类型
 */
public abstract class AbstractResource<T extends Resource<T>> implements Resource<T> {

    private final AtomicInteger refCount = new AtomicInteger(1);

    @Override
    public void acquire() {
        int n = refCount.incrementAndGet();
        if (n <= 1) {
            // 说明对象已被销毁——refCount 从 0 变 1
            // 或溢出——理论上不会发生
            refCount.decrementAndGet();
            throw new IllegalStateException(
                    "acquire after dispose: refCount=" + n);
        }
    }

    @Override
    public void dispose() {
        int n = refCount.decrementAndGet();
        if (n < 0) {
            // 过度 dispose——bug
            refCount.incrementAndGet();
            throw new IllegalStateException(
                    "dispose called too many times: refCount=" + n);
        }
        if (n == 0) {
            onDispose();
        }
    }

    @Override
    public boolean isExclusive() {
        return refCount.get() == 1;
    }

    /**
     * 引用计数归零时调用——释放底层资源。
     *
     * <p>只调一次——由 {@link #dispose()} 保证。
     */
    protected abstract void onDispose();
}