package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.d3.core.util.IndexAllocator;
import top.kzre.krro.d3.core.util.LongIntMap;
import top.kzre.krro.d3.core.util.ResourceUtils;

/**
 * 资源表——持有一种 COW 资源类型的容器。
 *
 * <p>三张表协作：
 * <ul>
 *   <li>{@code items}          —— 顺序持有资源——真正的 COW 容器</li>
 *   <li>{@code indexMap}       —— id → 位置——只加速查找——不持有资源</li>
 *   <li>{@code indexAllocator} —— 位置分配器——写路径使用</li>
 * </ul>
 *
 * @param <A> 资源类型
 */
public final class ResourceTable<A extends CopyOnWrite<A>> implements CopyOnWrite<ResourceTable<A>> {

    /** 元素列表——持有资源 */
    private final CopyOnWriteObject<ListResource<A>> items;

    /** id → 位置索引——只加速查找 */
    private final CopyOnWriteObject<LongIntMapResource> indexMap;

    /** 位置分配器——写路径使用 */
    private final CopyOnWriteObject<IndexAllocatorResource> indexAllocator;

    private ResourceTable(
            CopyOnWriteObject<ListResource<A>> items,
            CopyOnWriteObject<LongIntMapResource> indexMap,
            CopyOnWriteObject<IndexAllocatorResource> indexAllocator) {
        this.items          = items;
        this.indexMap       = indexMap;
        this.indexAllocator = indexAllocator;
    }

    public static <T extends CopyOnWrite<T>> ResourceTable<T> create() {
        return new ResourceTable<T>(
                new CopyOnWriteObject<>(new ListResource<>()),
                new CopyOnWriteObject<>(new LongIntMapResource(new LongIntMap())),
                new CopyOnWriteObject<>(new IndexAllocatorResource(new IndexAllocator())));
    }

    // ═══════════════════════════════════════════════
    // 访问
    // ═══════════════════════════════════════════════

    public CopyOnWriteObject<ListResource<A>>        items()          { return items; }
    public CopyOnWriteObject<LongIntMapResource>     indexMap()       { return indexMap; }
    public CopyOnWriteObject<IndexAllocatorResource> indexAllocator() { return indexAllocator; }

    /** 按 id 查找。 */
    public A get(long id) {
        int idx = indexMap.getSnapshot().getMap().get(id);
        if (idx < 0) return null;
        return items.getSnapshot().getList().get(idx);
    }

    /** 按位置查找。 */
    public A getByIndex(int index) {
        return items.getSnapshot().getList().get(index);
    }

    /** 元素数量。 */
    public int size() {
        return items.getSnapshot().getList().size();
    }

    // ═══════════════════════════════════════════════
    // COW
    // ═══════════════════════════════════════════════

    @Override
    public ResourceTable<A> shared() {
        return new ResourceTable<>(
                items.shared(),
                indexMap.shared(),
                indexAllocator.shared());
    }

    @Override
    public void close() throws Exception {
        RuntimeException ex = ResourceUtils.closeDelayError(items, indexMap, indexAllocator);
        if (ex != null) throw ex;
    }
}