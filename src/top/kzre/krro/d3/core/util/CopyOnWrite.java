package top.kzre.krro.d3.core.util;

/**
 * <p><b>线程契约</b>：
 * <ul>
 *   <li>{@code shared()} 产生的多个句柄可分散到多线程——
 *       ForWrite 时 COW 自动隔离。</li>
 *   <li>同一句柄不在多线程并发使用。</li>
 * </ul>
 * @param <T>
 */
public interface CopyOnWrite<T extends CopyOnWrite<T>> extends AutoCloseable{
    // 生命周期
    T shared();
}