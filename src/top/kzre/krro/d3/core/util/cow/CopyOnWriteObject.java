package top.kzre.krro.d3.core.util.cow;

/**
 * 对象级 COW 单元接口——包装任意引用计数资源 {@link Resource}。
 *
 * <p><b>只有资源类型参数 {@code R}</b>，实现类型固定为
 * {@code CopyOnWriteObject<R>}，继承
 * {@link CopyOnWrite}{@code <CopyOnWriteObject<R>>}。
 *
 * <p>{@code getSnapshot()} / {@code getForWrite()} 返回具体资源类型
 * {@code R}；{@code shared()} 返回 {@code CopyOnWriteObject<R>}。
 */
public interface CopyOnWriteObject<R extends Resource<R>>
        extends CopyOnWrite<CopyOnWriteObject<R>> {

    /**
     * 只读快照——返回当前底层资源。调用方不得修改。
     */
    R getSnapshot();

    /**
     * 写访问——独占时直接返回，共享时复制后返回。
     *
     * @return 已隔离的可写资源
     */
    R getForWrite();
}