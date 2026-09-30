package top.kzre.krro.d3.core.util.cow;

/**
 * COW 资源契约——引用计数 + 复制 + 销毁。
 *
 * <p><b>契约</b>：
 * <ul>
 *   <li>{@code copy()} —— 创建独立副本——引用计数 1</li>
 *   <li>{@code acquire()} —— 引用 +1</li>
 *   <li>{@code dispose()} —— 引用 -1——归零时销毁</li>
 *   <li>{@code isExclusive()} —— 引用计数 == 1</li>
 * </ul>
 *
 * <p>实现方负责维护引用计数——通常用 {@code AtomicInteger}。
 */
public interface Resource<V> {
    V copy();
    void acquire();
    void dispose();
    boolean isExclusive();
}