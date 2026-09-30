package top.kzre.krro.d3.core.util.cow;

import java.util.ArrayList;
import java.util.List;

/**
 * List 资源——元素约束为 {@link CopyOnWrite}。
 *
 * <p><b>深度 COW</b>：{@link #copy()} 对每个元素调用 {@code shared()}——
 * 保证段对象隔离——两层 COW 一起工作。
 *
 * <p><b>null 支持</b>：段列表允许 null（空段）——copy 时保留 null。
 *
 * <p><b>onDispose</b>：每个元素独立 try-close——
 * 避免一个失败导致其余泄漏——收集所有异常——最后抛。
 */
public final class ListResource<T extends CopyOnWrite<T>>
        extends AbstractResource<ListResource<T>> {

    private final List<T> list;

    public ListResource() {
        this.list = new ArrayList<>();
    }

    public ListResource(List<T> initial) {
        this.list = new ArrayList<>(initial);
    }

    /** 底层 List——可读可写——写前须经 getForWrite。 */
    public List<T> getList() { return list; }

    @Override
    public ListResource<T> copy() {
        int n = list.size();
        List<T> newList = new ArrayList<>(n);
        for (T item : list) {
            newList.add(item == null ? null : item.shared());
        }
        return new ListResource<>(newList);
    }

    @Override
    protected void onDispose() {
        Throwable first = null;
        int n = list.size();
        for (T item : list) {
            if (item == null) continue;
            try {
                item.close();
            } catch (Throwable t) {
                if (first == null) {
                    first = t;
                } else {
                    first.addSuppressed(t);
                }
            }
        }
        list.clear();

        if (first != null) {
            throw new RuntimeException("ListResource.onDispose() failed", first);
        }
    }
}