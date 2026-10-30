package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.d3.core.util.IndexAllocator;

import java.util.List;

/**
 * {@link ResourceTable} 的通用操作。
 *
 * <p>封装“往表里加一个元素”的多步操作——位置分配、空洞填充、
 * 索引写入——避免每个资产类型各写一遍。
 */
public final class ResourceTables {

    private ResourceTables() {}

    /**
     * 往资源表添加一个元素。
     *
     * <p><b>流程</b>：
     * <ol>
     *   <li>检查 id 是否已存在——重复抛异常</li>
     *   <li>从分配器分配位置（复用空闲或追加）</li>
     *   <li>写入 items 列表——位置不连续时用 null 填充空洞</li>
     *   <li>写入 indexMap——id → 位置</li>
     * </ol>
     *
     * <p><b>COW</b>：三处 {@code getForWrite()} 各自触发 COW——
     * 表被共享时自动隔离。
     *
     * <p><b>异常安全</b>：位置分配后如果写入失败（理论上不会，
     * 除非 OOM），位置会回滚到分配器，不会泄漏。
     *
     * @param table 目标资源表
     * @param id    元素 id——必须全局唯一（表内）
     * @param item  元素——所有权转移给表，调用方如需保留应自己 {@code shared()}
     * @throws NullPointerException  table / item 为 null
     * @throws IllegalStateException id 已存在
     */
    public static <A extends CopyOnWrite<A>> void add(
            ResourceTable<A> table, long id, A item) {
        if (table == null) throw new NullPointerException("table");
        if (item == null)  throw new NullPointerException("item");

        // ── 1. 检查 id 冲突 ──
        int existing = table.indexMap()
                .getSnapshot()
                .getMap()
                .get(id);
        if (existing >= 0) {
            throw new IllegalStateException(
                    "duplicate id: " + id + " (at index " + existing + ")");
        }

        // ── 2. 分配位置 ──
        IndexAllocator allocator = table.indexAllocator()
                .getForWrite()
                .getAllocator();
        int idx = allocator.allocate();

        // ── 3. 写入 items + indexMap，失败时回滚位置 ──
        try {
            List<A> items = table.items()
                    .getForWrite()
                    .getList();
            while (items.size() <= idx) {
                items.add(null);
            }
            items.set(idx, item);

            table.indexMap()
                    .getForWrite()
                    .getMap()
                    .put(id, idx);
        } catch (Throwable t) {
            // 位置已分配但未使用——归还给分配器，避免泄漏
            allocator.free(idx);
            throw t;
        }
    }

    /**
     * 从资源表移除一个元素。
     *
     * <p><b>流程</b>：查位置 → 移除 indexMap 映射 → 清空 items 位置
     * → 归还位置到分配器。
     *
     * <p><b>顺序的意义</b>：先移除 indexMap——一旦移除，该 id 可以被
     * 重新 {@code add}，即使后续步骤失败，也只会留下“位置占用但无
     * 引用”的垃圾，不会留下“id 映射存在但元素已空”的幽灵状态。
     *
     * <p><b>所有权</b>：返回被移除的元素，所有权从表转移给调用方。
     * 调用方负责 {@code close()} 或转交给别处。
     *
     * <p><b>不存在时抛异常</b>：删除不存在的 id 通常是调用方 bug。
     *
     * @param table 目标资源表
     * @param id    元素 id
     * @return 被移除的元素——所有权转移给调用方
     * @throws NullPointerException  table 为 null
     * @throws IllegalStateException id 不存在
     */
    public static <A extends CopyOnWrite<A>> A remove(
            ResourceTable<A> table, long id) {
        if (table == null) throw new NullPointerException("table");

        // ── 1. 查位置 ──
        int idx = table.indexMap()
                .getSnapshot()
                .getMap()
                .get(id);
        if (idx < 0) {
            throw new IllegalStateException("id not found: " + id);
        }

        // ── 2. 取出元素 ──
        A removed = table.items()
                .getSnapshot()
                .getList()
                .get(idx);

        // ── 3. 移除 indexMap 映射——先做，保证 id 可被重新 add ──
        table.indexMap()
                .getForWrite()
                .getMap()
                .remove(id);

        // ── 4. 清空 items 位置 ──
        table.items()
                .getForWrite()
                .getList()
                .set(idx, null);

        // ── 5. 归还位置 ──
        table.indexAllocator()
                .getForWrite()
                .getAllocator()
                .free(idx);

        return removed;
    }
}