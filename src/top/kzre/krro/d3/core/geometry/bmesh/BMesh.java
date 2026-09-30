package top.kzre.krro.d3.core.geometry.bmesh;

import top.kzre.krro.d3.core.util.cow.CopyOnWrite;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteObject;
import top.kzre.krro.d3.core.util.cow.ListResource;

/**
 * BMesh——编辑内核。
 *
 * <p><b>角色</b>：三维网格的编辑期数据结构——提供径向边拓扑——
 * 支持任意拓扑变更（挤出 / 切分 / 溶解 / 合并）。不持久化——
 * 编辑结束烘焙回 {@code Mesh}。
 *
 * <p><b>四元素结构</b>：
 * <ul>
 *   <li><b>顶点（Vert）</b>——位置 + 一条出边</li>
 *   <li><b>边（Edge）</b>——两端点 + 径向环入口</li>
 *   <li><b>环（Loop）</b>——角点——归属 + 面内链 + 径向链 + UV</li>
 *   <li><b>面（Face）</b>——环入口 + 环长 + 法线 + 子网格归属</li>
 * </ul>
 * 四元素通过数组下标互联——非负整数——哨兵 -1 表示无引用。
 *
 * <p><b>属性分组</b>：总是一起访问 / 编辑的属性合并到同一
 * {@link CopyOnWriteInts}——通过 stride 复合——一次 COW 更新多字段。
 * <pre>
 *   vertPositions      float3   position
 *   vertOutEdges       int      out-edge
 *
 *   edgeEndpoints      int2     v0, v1
 *   edgeLoops          int      radial loop 入口
 *
 *   loopUvs            float2   u, v
 *   loopOwnership      int3     vert, edge, face
 *   loopRing           int2     next, prev
 *   loopRadialRing     int2     radial-next, radial-prev
 *
 *   faceNormals        float3   normal
 *   faceSubmeshIds     int      submesh id
 *   faceTopology       int2     loop, len
 * </pre>
 *
 * <p><b>顶点法线不在 BMesh</b>：顶点法线仅在全平滑时有意义——
 * 本质是面角法线的一个特例——由烘焙期从面法线算出——不在
 * BMesh 中存储。
 *
 * <p><b>COW 表达</b>：所有可变状态都是 COW——
 * <ul>
 *   <li>每个属性组一个 {@link CopyOnWriteObject} +
 *       {@link ListResource}&lt;{@link CopyOnWriteFloats}|{@link CopyOnWriteInts}&gt;——
 *       段列表</li>
 *   <li>列表内每个段——{@link CopyOnWriteFloats} / {@link CopyOnWriteInts}</li>
 * </ul>
 * 所有可变状态都是 COW——{@link #shared()} 后整体表现为 COW——
 * 任一方写自动隔离——另一方不受影响。
 *
 * <p><b>纯容器</b>：本类不含任何逻辑——段大小 / 索引计算 /
 * 计数 / 空闲槽 / 拓扑操作——全部归编辑层。本类只持有 COW 数据
 * 对象并提供访问器。
 *
 * <p><b>不持久化 / 不进 GPU / 不需要序列化</b>——编辑期结构——
 * 会话级临时表示。
 *
 * <p><b>线程契约</b>：
 * <ul>
 *   <li>单 BMesh 实例 = 单线程。</li>
 *   <li>多线程——每个线程通过 {@link #shared()} 获取副本——
 *       副本之间通过 COW 隔离。</li>
 * </ul>
 *
 * @see CopyOnWrite
 * @see CopyOnWriteObject
 * @see ListResource
 */
public final class BMesh implements CopyOnWrite<BMesh> {

    // ═══════════════════════════════════════════════
    // 顶点
    // ═══════════════════════════════════════════════

    /** 顶点位置——float3（x y z）。 */
    private final CopyOnWriteObject<ListResource<CopyOnWriteFloats>> vertPositions;

    /** 顶点出边——int（边索引）——孤立顶点 = -1。 */
    private final CopyOnWriteObject<ListResource<CopyOnWriteInts>> vertOutEdges;

    // ═══════════════════════════════════════════════
    // 边
    // ═══════════════════════════════════════════════

    /**
     * 边端点——int2（v0, v1）——stride = 2。
     *
     * <p>约定：loop.vert 为 v0 时——loop.next.vert 为 v1。
     */
    private final CopyOnWriteObject<ListResource<CopyOnWriteInts>> edgeEndpoints;

    /** 边径向环入口——int——孤立边 = -1。 */
    private final CopyOnWriteObject<ListResource<CopyOnWriteInts>> edgeLoops;

    // ═══════════════════════════════════════════════
    // 环
    // ═══════════════════════════════════════════════

    /** 环 UV——float2（u v）。 */
    private final CopyOnWriteObject<ListResource<CopyOnWriteFloats>> loopUvs;

    /**
     * 环归属——int3（vert, edge, face）——stride = 3。
     *
     * <p>创建 loop 时三者一起写——合并存储。
     */
    private final CopyOnWriteObject<ListResource<CopyOnWriteInts>> loopOwnership;

    /**
     * 面内链——int2（next, prev）——stride = 2。
     *
     * <p>next / prev 总是一起维护——合并存储。
     */
    private final CopyOnWriteObject<ListResource<CopyOnWriteInts>> loopRing;

    /**
     * 径向链——int2（radial-next, radial-prev）——stride = 2。
     *
     * <p>允许非流形：径向链长度不定。
     */
    private final CopyOnWriteObject<ListResource<CopyOnWriteInts>> loopRadialRing;

    // ═══════════════════════════════════════════════
    // 面
    // ═══════════════════════════════════════════════

    /** 面法线——float3（x y z）——编辑期缓存——按需重算。 */
    private final CopyOnWriteObject<ListResource<CopyOnWriteFloats>> faceNormals;

    /** 面子网格 id——int。 */
    private final CopyOnWriteObject<ListResource<CopyOnWriteInts>> faceSubmeshIds;

    /**
     * 面拓扑——int2（loop, len）——stride = 2。
     *
     * <p>loop = 环入口——len = 环长。创建面时一起写。
     */
    private final CopyOnWriteObject<ListResource<CopyOnWriteInts>> faceTopology;

    // ═══════════════════════════════════════════════
    // 分配器
    // ═══════════════════════════════════════════════

    /** 顶点索引分配器。 */
    private final CopyOnWriteObject<AllocatorResource> vertAllocator;

    /** 边索引分配器。 */
    private final CopyOnWriteObject<AllocatorResource> edgeAllocator;

    /** 环索引分配器。 */
    private final CopyOnWriteObject<AllocatorResource> loopAllocator;

    /** 面索引分配器。 */
    private final CopyOnWriteObject<AllocatorResource> faceAllocator;

    // ═══════════════════════════════════════════════
    // 全参构造器
    // ═══════════════════════════════════════════════

    /**
     * 全参构造器——所有 COW 对象由调用方提供——所有权转移。
     */
    public BMesh(
            CopyOnWriteObject<ListResource<CopyOnWriteFloats>> vertPositions,
            CopyOnWriteObject<ListResource<CopyOnWriteInts>>   vertOutEdges,

            CopyOnWriteObject<ListResource<CopyOnWriteInts>>   edgeEndpoints,
            CopyOnWriteObject<ListResource<CopyOnWriteInts>>   edgeLoops,

            CopyOnWriteObject<ListResource<CopyOnWriteFloats>> loopUvs,
            CopyOnWriteObject<ListResource<CopyOnWriteInts>>   loopOwnership,
            CopyOnWriteObject<ListResource<CopyOnWriteInts>>   loopRing,
            CopyOnWriteObject<ListResource<CopyOnWriteInts>>   loopRadialRing,

            CopyOnWriteObject<ListResource<CopyOnWriteFloats>> faceNormals,
            CopyOnWriteObject<ListResource<CopyOnWriteInts>>   faceSubmeshIds,
            CopyOnWriteObject<ListResource<CopyOnWriteInts>>   faceTopology,

            CopyOnWriteObject<AllocatorResource> vertAllocator,
            CopyOnWriteObject<AllocatorResource> edgeAllocator,
            CopyOnWriteObject<AllocatorResource> loopAllocator,
            CopyOnWriteObject<AllocatorResource> faceAllocator) {

        this.vertPositions = vertPositions;
        this.vertOutEdges  = vertOutEdges;

        this.edgeEndpoints = edgeEndpoints;
        this.edgeLoops     = edgeLoops;

        this.loopUvs        = loopUvs;
        this.loopOwnership  = loopOwnership;
        this.loopRing       = loopRing;
        this.loopRadialRing = loopRadialRing;

        this.faceNormals    = faceNormals;
        this.faceSubmeshIds = faceSubmeshIds;
        this.faceTopology   = faceTopology;

        this.vertAllocator = vertAllocator;
        this.edgeAllocator = edgeAllocator;
        this.loopAllocator = loopAllocator;
        this.faceAllocator = faceAllocator;
    }

    // ═══════════════════════════════════════════════
    // 访问器——顶点
    // ═══════════════════════════════════════════════

    public CopyOnWriteObject<ListResource<CopyOnWriteFloats>> vertPositions() { return vertPositions; }
    public CopyOnWriteObject<ListResource<CopyOnWriteInts>>   vertOutEdges()  { return vertOutEdges; }

    // ═══════════════════════════════════════════════
    // 访问器——边
    // ═══════════════════════════════════════════════

    /** 边端点——int2（v0, v1）——stride = 2。 */
    public CopyOnWriteObject<ListResource<CopyOnWriteInts>> edgeEndpoints() { return edgeEndpoints; }

    public CopyOnWriteObject<ListResource<CopyOnWriteInts>> edgeLoops()     { return edgeLoops; }

    // ═══════════════════════════════════════════════
    // 访问器——环
    // ═══════════════════════════════════════════════

    public CopyOnWriteObject<ListResource<CopyOnWriteFloats>> loopUvs()         { return loopUvs; }

    /** 环归属——int3（vert, edge, face）——stride = 3。 */
    public CopyOnWriteObject<ListResource<CopyOnWriteInts>>   loopOwnership()   { return loopOwnership; }

    /** 面内链——int2（next, prev）——stride = 2。 */
    public CopyOnWriteObject<ListResource<CopyOnWriteInts>>   loopRing()        { return loopRing; }

    /** 径向链——int2（radial-next, radial-prev）——stride = 2。 */
    public CopyOnWriteObject<ListResource<CopyOnWriteInts>>   loopRadialRing()  { return loopRadialRing; }

    // ═══════════════════════════════════════════════
    // 访问器——面
    // ═══════════════════════════════════════════════

    public CopyOnWriteObject<ListResource<CopyOnWriteFloats>> faceNormals()    { return faceNormals; }
    public CopyOnWriteObject<ListResource<CopyOnWriteInts>>   faceSubmeshIds() { return faceSubmeshIds; }

    /** 面拓扑——int2（loop, len）——stride = 2。 */
    public CopyOnWriteObject<ListResource<CopyOnWriteInts>>   faceTopology()   { return faceTopology; }

    // ═══════════════════════════════════════════════
    // 访问器——分配器
    // ═══════════════════════════════════════════════

    public CopyOnWriteObject<AllocatorResource> vertAllocator() { return vertAllocator; }
    public CopyOnWriteObject<AllocatorResource> edgeAllocator() { return edgeAllocator; }
    public CopyOnWriteObject<AllocatorResource> loopAllocator() { return loopAllocator; }
    public CopyOnWriteObject<AllocatorResource> faceAllocator() { return faceAllocator; }

    // ═══════════════════════════════════════════════
    // 便利工厂——create
    // ═══════════════════════════════════════════════

    private static <T> CopyOnWriteObject<ListResource<T>> emptyList() {
        return new CopyOnWriteObject<>(new ListResource<>());
    }

    private static CopyOnWriteObject<AllocatorResource> newAllocator(int segmentSize) {
        return new CopyOnWriteObject<>(
                new AllocatorResource(new SegmentizedIndexAllocator(segmentSize)));
    }

    /**
     * 创建空 BMesh——所有属性为空——分配器独立——统一段大小。
     */
    public static BMesh create(int segmentSize) {
        return new BMesh(
                emptyList(), emptyList(),               // vert
                emptyList(), emptyList(),               // edge
                emptyList(), emptyList(),               // loop: uv / ownership
                emptyList(), emptyList(),               // loop: ring / radial
                emptyList(), emptyList(), emptyList(),  // face

                newAllocator(segmentSize),
                newAllocator(segmentSize),
                newAllocator(segmentSize),
                newAllocator(segmentSize)
        );
    }

    /**
     * 创建空 BMesh——各元素类型用不同段大小。
     */
    public static BMesh create(
            int vertSegmentSize,
            int edgeSegmentSize,
            int loopSegmentSize,
            int faceSegmentSize) {
        return new BMesh(
                emptyList(), emptyList(),
                emptyList(), emptyList(),
                emptyList(), emptyList(),
                emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList(),

                newAllocator(vertSegmentSize),
                newAllocator(edgeSegmentSize),
                newAllocator(loopSegmentSize),
                newAllocator(faceSegmentSize)
        );
    }

    // ═══════════════════════════════════════════════
    // shared / close
    // ═══════════════════════════════════════════════

    @Override
    public BMesh shared() {
        return new BMesh(
                vertPositions.shared(),
                vertOutEdges.shared(),

                edgeEndpoints.shared(),
                edgeLoops.shared(),

                loopUvs.shared(),
                loopOwnership.shared(),
                loopRing.shared(),
                loopRadialRing.shared(),

                faceNormals.shared(),
                faceSubmeshIds.shared(),
                faceTopology.shared(),

                vertAllocator.shared(),
                edgeAllocator.shared(),
                loopAllocator.shared(),
                faceAllocator.shared()
        );
    }

    @Override
    public void close() {
        Throwable first = null;

        first = tryClose(vertPositions, first);
        first = tryClose(vertOutEdges,  first);

        first = tryClose(edgeEndpoints, first);
        first = tryClose(edgeLoops,     first);

        first = tryClose(loopUvs,        first);
        first = tryClose(loopOwnership,  first);
        first = tryClose(loopRing,       first);
        first = tryClose(loopRadialRing, first);

        first = tryClose(faceNormals,    first);
        first = tryClose(faceSubmeshIds, first);
        first = tryClose(faceTopology,   first);

        first = tryClose(vertAllocator, first);
        first = tryClose(edgeAllocator, first);
        first = tryClose(loopAllocator, first);
        first = tryClose(faceAllocator, first);

        if (first != null) {
            throw new RuntimeException("BMesh.close() failed", first);
        }
    }

    private static Throwable tryClose(CopyOnWrite<?> resource, Throwable first) {
        try {
            resource.close();
        } catch (Throwable t) {
            if (first == null) return t;
            first.addSuppressed(t);
        }
        return first;
    }
}