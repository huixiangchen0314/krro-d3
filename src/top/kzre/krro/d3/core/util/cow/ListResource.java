package top.kzre.krro.d3.core.util.cow;

import java.util.ArrayList;
import java.util.List;

/**
 * List 资源——包装一个 List——引用计数 + COW 复制。
 *
 * <p><b>使用模式</b>：
 * <pre>
 *   CopyOnWriteObject&lt;ListResource&lt;BMeshBlock&gt;&gt; cow =
 *       new CopyOnWriteObject&lt;&gt;(new ListResource&lt;&gt;(new ArrayList&lt;&gt;()));
 *
 *   // 读
 *   List&lt;BMeshBlock&gt; snap = cow.getSnapshot().list();
 *
 *   // 写——共享时自动复制
 *   List&lt;BMeshBlock&gt; w = cow.getForWrite().list();
 *   w.add(block);
 * </pre>
 *
 * <p><b>契约</b>：通过 {@link #getList()} 拿到的 List 可读可写——
 * 但调用方必须保证是在 {@code getForWrite()} 之后拿的——
 * 否则可能修改共享数据。
 */
public final class ListResource<T> extends AbstractResource<ListResource<T>> {

    private final List<T> list;

    public ListResource() {
        this.list = new ArrayList<>();
    }

    public ListResource(List<T> initial) {
        this.list = new ArrayList<>(initial);
    }

    /**
     * 底层 List——可读可写。
     *
     * <p><b>契约</b>：只有在 {@code getForWrite()} 之后调用才是安全的写访问。
     */
    public List<T> getList() {
        return list;
    }

    @Override
    public ListResource<T> copy() {
        return new ListResource<>(new ArrayList<>(list));
    }

    @Override
    protected void onDispose() {
        list.clear();
    }
}