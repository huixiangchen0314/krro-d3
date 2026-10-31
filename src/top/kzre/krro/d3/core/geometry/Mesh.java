package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.*;

import java.util.List;

/**
 * 网格——本质数据容器。
 *
 * <p><b>本质数据</b>：
 * <ul>
 *   <li>{@link #positions()}     —— 顶点位置</li>
 *   <li>{@link #cornerVerts()}   —— 面角 → 顶点</li>
 *   <li>{@link #faceOffsets()}   —— 面 → 面角范围偏移</li>
 *   <li>{@link #cornerUvs()}     —— 面角 UV 通道（多通道）</li>
 *   <li>{@link #edgeEndpoints()} —— 边端点（显式，稳定身份）</li>
 *   <li>{@link #submeshes()}     —— 材质分组</li>
 * </ul>
 *
 * <p><b>身份与关系</b>：
 * <ul>
 *   <li><b>身份</b>——顶点、面、面角、边都有稳定索引，显式存储。</li>
 *   <li><b>关系</b>——面角 → 边、面角 → 面等，从身份重建，不存储。</li>
 * </ul>
 *
 * <p><b>UV 通道</b>：{@code cornerUvs} 是列表。索引 0 是 UV0，索引 1 是 UV1，
 * 依此类推。至少一个通道。每通道 float2[ncorners]。
 *
 * <p><b>布局</b>（全部 stride 化数组，无对象开销）：
 * <pre>
 *   positions       float3   stride 3   [nverts]
 *   cornerVerts     int      stride 1   [ncorners]
 *   faceOffsets     int      stride 1   [nfaces + 1]
 *   cornerUvs[i]    float2   stride 2   [ncorners]
 *   edgeEndpoints   int2     stride 2   [nedges]
 *   submeshes       ShallowListResource&lt;SubMesh&gt;
 * </pre>
 *
 * <p><b>不感知派生数据</b>：法线、切线、包围盒、面 AABB 等由
 * {@link ExtendedMesh} 计算、缓存、失效。
 *
 * <p><b>共享</b>：{@link #shared()} 返回共享底层数据的副本——
 * 写时由 {@link CopyOnWrite} 自动隔离。
 */
public final class Mesh implements CopyOnWrite<Mesh> {

    /** 顶点位置。float3——stride 3——长度 = vertCount * 3。 */
    private final CopyOnWriteFloats positions;

    /** 面角 → 顶点索引。int——stride 1——长度 = cornerCount。 */
    private final CopyOnWriteInts cornerVerts;

    /** 面 → 面角范围偏移。int——stride 1——长度 = faceCount + 1。 */
    private final CopyOnWriteInts faceOffsets;

    /**
     * 面角 UV 通道。
     *
     * <p>每通道 float2[ncorners]——stride 2。
     * 至少一个通道（UV0）。索引即通道号。
     */
    private final CopyOnWriteObject<ListResource<CopyOnWriteFloats>> cornerUvs;

    /**
     * 边端点。int2——stride 2——长度 = edgeCount * 2。
     * 规范形式：v0 &lt; v1。边索引 = 数组位置 / 2。
     */
    private final CopyOnWriteInts edgeEndpoints;

    /** 材质分组。ShallowListResource——只持有引用，不拥有 SubMesh。 */
    private final CopyOnWriteObject<ShallowListResource<SubMesh>> submeshes;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public Mesh(
            CopyOnWriteFloats positions,
            CopyOnWriteInts cornerVerts,
            CopyOnWriteInts faceOffsets,
            CopyOnWriteObject<ListResource<CopyOnWriteFloats>> cornerUvs,
            CopyOnWriteInts edgeEndpoints,
            CopyOnWriteObject<ShallowListResource<SubMesh>> submeshes) {

        if (positions == null || cornerVerts == null || faceOffsets == null
                || cornerUvs == null || edgeEndpoints == null || submeshes == null) {
            throw new NullPointerException("Mesh fields must not be null");
        }

        List<CopyOnWriteFloats> channels = cornerUvs.getSnapshot().getList();
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("cornerUvs must have at least one channel");
        }

        this.positions     = positions;
        this.cornerVerts   = cornerVerts;
        this.faceOffsets   = faceOffsets;
        this.cornerUvs     = cornerUvs;
        this.edgeEndpoints = edgeEndpoints;
        this.submeshes     = submeshes;
    }

    // ═══════════════════════════════════════════════
    // 计数
    // ═══════════════════════════════════════════════

    public long vertCount()    { return positions.count()     / 3; }
    public long cornerCount()  { return cornerVerts.count(); }
    public long faceCount()    { return faceOffsets.count()   - 1; }
    public long edgeCount()    { return edgeEndpoints.count() / 2; }
    public int  uvChannelCount() { return cornerUvs.getSnapshot().getList().size(); }

    // ═══════════════════════════════════════════════
    // 本质访问
    // ═══════════════════════════════════════════════

    public CopyOnWriteFloats positions()     { return positions; }
    public CopyOnWriteInts cornerVerts()   { return cornerVerts; }
    public CopyOnWriteInts faceOffsets()   { return faceOffsets; }
    public CopyOnWriteInts edgeEndpoints() { return edgeEndpoints; }

    public CopyOnWriteObject<ListResource<CopyOnWriteFloats>> cornerUvs() { return cornerUvs; }
    public CopyOnWriteObject<ShallowListResource<SubMesh>> submeshes()    { return submeshes; }

    /** 便捷——UV0 通道。 */
    public CopyOnWriteFloats cornerUv0() {
        List<CopyOnWriteFloats> uvs = cornerUvs.getSnapshot().getList();
        if (uvs.isEmpty()) {
            return null;
        }
        return uvs.getFirst();
    }

    // ═══════════════════════════════════════════════
    // 共享
    // ═══════════════════════════════════════════════

    @Override
    public Mesh shared() {
        return new Mesh(
                positions.shared(),
                cornerVerts.shared(),
                faceOffsets.shared(),
                cornerUvs.shared(),
                edgeEndpoints.shared(),
                submeshes.shared());
    }

    // ═══════════════════════════════════════════════
    // 关闭
    // ═══════════════════════════════════════════════

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(
                positions, cornerVerts, faceOffsets, cornerUvs, edgeEndpoints, submeshes);
        if (ex != null) throw ex;
    }
}