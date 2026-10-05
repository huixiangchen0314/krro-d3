package top.kzre.krro.d3.core.util.cow;

import java.util.ArrayList;
import java.util.List;

/**
 * 浅 List 资源——只 COW 列表结构——不复制元素。
 *
 * <p><b>浅 COW</b>：{@link #copy()} 只复制列表本身——
 * 元素引用直接共享。新旧资源的元素是同一批对象。
 *
 * <p>与 {@link ListResource} 的差别：
 * <ul>
 *   <li>ListResource：深度 COW——对每个元素调用 {@code shared()}</li>
 *   <li>ShallowListResource：浅 COW——元素引用原样传递</li>
 * </ul>
 *
 * <p><b>所有权</b>：本资源不拥有元素——元素的生命周期由外部管理。
 * onDispose 只清空列表——不 close 元素。
 *
 * <p><b>null 支持</b>：列表允许 null 元素——copy 时保留。
 *
 * @param <T> 元素类型——不要求 CopyOnWrite
 */
public final class ShallowListResource<T>
        extends AbstractResource<ShallowListResource<T>> {

    private final List<T> list;

    public ShallowListResource() {
        this.list = new ArrayList<>();
    }

    public ShallowListResource(List<T> initial) {
        this.list = new ArrayList<>(initial);
    }

    /** 底层 List——可读可写——写前须经 getForWrite。 */
    public List<T> getList() { return list; }

    @Override
    public ShallowListResource<T> copy() {
        int n = list.size();
        List<T> newList = new ArrayList<>(n);
        newList.addAll(list);
        return new ShallowListResource<>(newList);
    }

    @Override
    protected void onDispose() {
        // 不 close 元素——元素不归本资源所有
        list.clear();
    }
}