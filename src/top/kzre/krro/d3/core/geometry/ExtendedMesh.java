package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.geometry.algo.*;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.*;
import top.kzre.krro.util.arena.*;

import java.util.Collections;
import java.util.List;

/**
 * <p><b>派生数据</b>（全部走 COW + Arena）：
 * <pre>
 *   cornerPositions   float3[ncorners]  ← CornerPositionGather
 *   cornerNormals     float3[ncorners]  ← AngleClusteredNormals
 *   cornerEdges       int[ncorners]     ← CornerEdgeLookup
 *   tangents          float4[ncorners]  ← MikkTSpaceTangents
 *   bounds            float[6]          ← MinMaxScan
 *   faceAabbs         float6[nfaces]    ← FaceAabbScan
 *   triangleIndices   int[ntri*3]       ← MeshTriangulation
 * </pre>
 *
 * <p><b>本质数据代理</b>：{@link Mesh} 不再对外暴露，本类代理全部本质数据
 * 的读写：
 * <ul>
 *   <li><b>只读</b>：{@link #positions()} / {@link #cornerVerts()} /
 *       {@link #faceOffsets()} / {@link #edgeEndpoints()} /
 *       {@link #cornerUvs()} / {@link #submeshes()}——返回 COW 对象，
 *       调用方按读路径访问（{@code getSnapshot()} / {@code getFloatsSnapshot()}
 *       等），不产生副作用。</li>
 *   <li><b>可写</b>：{@link #positionsForWrite()} / {@link #cornerVertsForWrite()} /
 *       {@link #faceOffsetsForWrite()} / {@link #edgeEndpointsForWrite()} /
 *       {@link #cornerUvsForWrite()} / {@link #submeshesForWrite()}——先丢弃
 *       依赖该字段的派生缓存，再返回 COW 对象，调用方按写路径访问
 *       （{@code getFloatsForWrite()} / {@code getForWrite()} 等）。</li>
 * </ul>
 *
 * <p><b>失效规则</b>：
 * <pre>
 *   positionsForWrite()     → cornerPositions / triangleIndices /
 *                             cornerNormals / tangents / bounds / faceAabbs
 *   cornerVertsForWrite()   → 全部派生
 *   faceOffsetsForWrite()   → 全部派生
 *   edgeEndpointsForWrite() → cornerEdges / cornerNormals / tangents
 *   cornerUvsForWrite()     → triangleIndices / tangents
 *   submeshesForWrite()     → 无（submeshes 不参与派生计算）
 *   setSmoothAngle()        → cornerNormals / tangents
 * </pre>
 */
public final class ExtendedMesh implements CopyOnWrite<ExtendedMesh> {

    // 算法
    private final MinMaxScanAlgo minMaxScan = new MinMaxScanImpl();
    private final CornerPositionGatherAlgo cornerPositionGather = new CornerPositionGatherImpl();
    private final TriangulationAlgo triangulation = new GeometryEdgeTangentsImpl();
    private final FaceAabbAlgo faceAabbs = new FaceAabbScanImpl();
    private final CornerEdgeLookupAlgo cornerEdgeLookup = new CornerEdgeLookupImpl();
    private final CornerNormalsAlgo cornerNormals = new EdgeAdjacentNormalsImpl();
    private final TangentAlgo tangents = new WeldedMikkTangentsImpl();

    // 数据
    private final Mesh mesh;
    private final ArenaTemplate<?, FloatArenaView> floatTemplate;
    private final ArenaTemplate<?, IntArenaView> intTemplate;

    /**
     * 平滑角度阈值——弧度——用于面角法线计算。
     */
    private float smoothAngleRadians;

    /**
     * 面角法线缓存（per-corner normals）。
     *
     * <p>布局：每面角 3 个 float（nx, ny, nz），总长 = cornerCount × 3。
     * 面角法线按 {@code AngleClusteredNormals} 的夹角阈值分离——
     * 同一顶点的不同面角可拥有不同法线（如立方体 90° 硬边）。
     *
     * <p>懒加载：{@code null} 表示尚未计算。计算完成后不可变。
     */
    private CopyOnWriteFloats cachedCornerNormals;

    /**
     * 面角边缓存（per-corner edge indices）。
     *
     * <p>布局：每面角 2 个 int，分别指向该面角的两条相邻边在
     * 全局边表中的下标——用于轮廓边检测、法线分离、法线生成
     * 等需要面角与边拓扑关联的算法。
     *
     * <p>懒加载：{@code null} 表示尚未计算。计算完成后不可变。
     */
    private CopyOnWriteInts   cachedCornerEdges;

    /**
     * 切线缓存（per-corner tangents）。
     *
     * <p>布局：每面角 3 个 float（tx, ty, tz），总长 = cornerCount × 3。
     * 由 UV 与位置推导，供法线贴图使用——与面角法线、UV 一一对应。
     *
     * <p>懒加载：{@code null} 表示尚未计算。计算完成后不可变。
     */
    private CopyOnWriteFloats cachedTangents;

    /**
     * 整体包围盒缓存。
     *
     * <p>覆盖所有顶点位置的 AABB（世界空间 = 局部空间，含 model 变换前）。
     * 用于视锥剔除、拾取加速、相机自动取景等。
     *
     * <p>懒加载：{@code null} 表示尚未计算。计算完成后不可变——
     * 用 {@link top.kzre.krro.d3.core.geometry.ImmutableAABB} 而非可变 AABB，
     * 避免外部持有后污染缓存。
     */
    private ImmutableAABB     cachedBounds;

    /**
     * 每面 AABB 缓存（per-face AABBs）。
     *
     * <p>布局：每面 6 个 float（minX, minY, minZ, maxX, maxY, maxZ），
     * 总长 = faceCount × 6。每个 AABB 是该面所有面角位置的紧包围。
     *
     * <p>用于逐面剔除、面级 BVH 构建、空间划分等不需要展开到
     * 三角形粒度的加速结构。
     *
     * <p>懒加载：{@code null} 表示尚未计算。计算完成后不可变。
     */
    private CopyOnWriteFloats cachedFaceAabbs;

    /**
     * 面角位置缓存（per-corner positions）。
     *
     * <p>布局：每面角 3 个 float（x, y, z），总长 = cornerCount × 3。
     * 由 per-vertex {@code positions} 经 {@code cornerVerts} 索引展开——
     * 展开逻辑见 {@link top.kzre.krro.d3.core.geometry.algo.CornerPositionGatherImpl}。
     *
     * <p>渲染管线直接消费此数据（每 3 个角点组成一个三角形面角），
     * 无需再经过 {@code cornerVerts} 间接寻址。
     *
     * <p>懒加载：{@code null} 表示尚未计算。计算完成后不可变。
     */
    private CopyOnWriteFloats cachedCornerPositions;

    /**
     * 三角形索引缓存（triangle indices）。
     *
     * <p>布局：每三角形 3 个 int，总长 = triangleCount × 3。下标指向
     * <b>面角空间</b>（{@code 0..cornerCount-1}），不是 per-vertex 空间——
     * 与 {@link #cachedCornerPositions} 配合使用。
     *
     * <p>由 {@code faceOffsets} 对每面（n 边形）做扇形剖分得到：
     * 每个 n 边形生成 n−2 个三角形，对 quad 即 2 个。
     *
     * <p>懒加载：{@code null} 表示尚未计算。计算完成后不可变。
     */
    private CopyOnWriteInts   cachedTriangleIndices;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public ExtendedMesh(
            Mesh mesh,
            ArenaTemplate<?, FloatArenaView> floatTemplate,
            ArenaTemplate<?, IntArenaView> intTemplate,
            float smoothAngleRadians) {
        if (mesh == null) throw new NullPointerException("mesh");
        if (floatTemplate == null) throw new NullPointerException("floatTemplate");
        if (intTemplate == null) throw new NullPointerException("intTemplate");

        this.mesh = mesh;
        this.floatTemplate = floatTemplate;
        this.intTemplate = intTemplate;
        this.smoothAngleRadians = smoothAngleRadians;
    }

    // ═══════════════════════════════════════════════
    // 本质数据——只读代理
    // ═══════════════════════════════════════════════

    public int faceCount(){
        return mesh.faceCount();
    }

    /** 顶点位置。float3[nverts]。无副作用。 */
    public CopyOnWriteFloats positions() {
        return mesh.positions();
    }

    /** 面角 → 顶点。int[ncorners]。无副作用。 */
    public CopyOnWriteInts cornerVerts() {
        return mesh.cornerVerts();
    }

    /** 面 → 面角范围偏移。int[nfaces + 1]。无副作用。 */
    public CopyOnWriteInts faceOffsets() {
        return mesh.faceOffsets();
    }

    /** 边端点。int2[nedges]。无副作用。 */
    public CopyOnWriteInts edgeEndpoints() {
        return mesh.edgeEndpoints();
    }

    /** 面角 UV 通道列表。无副作用。 */
    public List<CopyOnWriteFloats> cornerUvs() {
        // COWObject 不是预期的边缘字段
        return Collections.unmodifiableList(mesh.cornerUvs().getSnapshot().getList());
    }

    /**
     * 材质分组。无副作用。
     */
    public List<SubMesh> submeshes() {
        return Collections.unmodifiableList(mesh.submeshes().getSnapshot().getList());
    }

    // ═══════════════════════════════════════════════
    // 本质数据——可写代理
    // ═══════════════════════════════════════════════

    /**
     * 声明写位置。
     *
     * <p><b>副作用</b>：丢弃 cornerPositions / triangleIndices /
     * cornerNormals / tangents / bounds / faceAabbs。
     *
     * <p>返回的 COW 对象由调用方按写路径访问（{@code getFloatsForWrite()}）。
     */
    public CopyOnWriteFloats positionsForWrite() {
        discardCornerPositions();
        discardTriangleIndices();   // triangulation 依赖 positions（quad 对角线、ear clipping 投影）
        discardCornerNormals();
        discardTangents();
        discardBounds();
        discardFaceAabbs();
        return mesh.positions();
    }

    /**
     * 声明写面角 → 顶点——拓扑变化——丢弃全部派生。
     */
    public CopyOnWriteInts cornerVertsForWrite() {
        discardAll();
        return mesh.cornerVerts();
    }

    /**
     * 声明写面 → 角范围偏移——拓扑变化——丢弃全部派生。
     */
    public CopyOnWriteInts faceOffsetsForWrite() {
        discardAll();
        return mesh.faceOffsets();
    }

    /**
     * 声明写边端点。
     *
     * <p><b>副作用</b>：丢弃 cornerEdges / cornerNormals / tangents。
     * cornerEdges 直接用 edgeEndpoints 查表；cornerNormals 用边邻接合并；
     * tangents 依赖 cornerNormals。
     *
     * <p>不影响 cornerPositions / triangleIndices / bounds / faceAabbs——
     * 这些只依赖 positions + cornerVerts + faceOffsets。
     */
    public CopyOnWriteInts edgeEndpointsForWrite() {
        discardCornerEdges();
        discardCornerNormals();
        discardTangents();
        return mesh.edgeEndpoints();
    }

    /**
     * 声明写 UV 通道。
     *
     * <p><b>副作用</b>：丢弃 triangleIndices / tangents。
     * triangulation 在 quad 拆分时用 UV 作等距对角线的 tie-break；
     * tangents 直接用 UV 算 tan1/tan2。
     *
     * <p>返回 {@code CopyOnWriteObject<ListResource<CopyOnWriteFloats>>}——
     * 调用方通过 {@code getForWrite()} 拿可写列表，对目标通道
     * 调 {@code getFloatsForWrite()}。
     */
    public List<CopyOnWriteFloats> cornerUvsForWrite() {
        discardTriangleIndices();
        discardTangents();
        return mesh.cornerUvs().getForWrite().getList();
    }

    /**
     * 声明写材质分组。
     *
     * <p><b>无副作用</b>：submeshes 不参与任何派生计算。
     */
    public List<SubMesh> submeshesForWrite() {
        return mesh.submeshes().getForWrite().getList();
    }

    // ═══════════════════════════════════════════════
    // 配置——平滑角度
    // ═══════════════════════════════════════════════

    public float getSmoothAngle() {
        return smoothAngleRadians;
    }

    /**
     * 设置平滑角度阈值——弧度。
     *
     * <p><b>副作用</b>：丢弃 cornerNormals / tangents 缓存。
     */
    public void setSmoothAngle(float smoothAngleRadians) {
        if (this.smoothAngleRadians == smoothAngleRadians) return;
        this.smoothAngleRadians = smoothAngleRadians;
        discardCornerNormals();
        discardTangents();
    }

    // ═══════════════════════════════════════════════
    // 派生数据
    // ═══════════════════════════════════════════════

    /**
     * 面角位置。float3[ncorners]——per-corner 的 position。
     *
     * <p>依赖：positions + cornerVerts。
     */
    public CopyOnWriteFloats cornerPositions() {
        CopyOnWriteFloats c = cachedCornerPositions;
        if (c != null) return c;

        CopyOnWriteFloats out = allocateFloats(
                cornerPositionGather.count(mesh.cornerVerts()));
        cornerPositionGather.compute(mesh.positions(), mesh.cornerVerts(), out);
        cachedCornerPositions = out;
        return cachedCornerPositions;
    }

    /**
     * 三角化 corner 索引。int[ntriangles * 3]。
     *
     * <p>依赖：positions + cornerVerts + faceOffsets + UV0。
     * 配合 {@link #cornerPositions()} 使用——corner 索引直接索引 per-corner
     * 顶点缓冲。
     */
    public CopyOnWriteInts triangleIndices() {
        CopyOnWriteInts c = cachedTriangleIndices;
        if (c != null) return c;

        CopyOnWriteInts out = allocateInts(triangulation.count(mesh.faceOffsets()));
        triangulation.compute(
                mesh.positions(), mesh.cornerVerts(), mesh.faceOffsets(),
                mesh.cornerUv0(), out);
        cachedTriangleIndices = out;
        return cachedTriangleIndices;
    }

    /**
     * 面角法线。float3[ncorners]。
     *
     * <p>依赖：positions + topology + edgeEndpoints + smoothAngleRadians。
     */
    public CopyOnWriteFloats cornerNormals() {
        CopyOnWriteFloats c = cachedCornerNormals;
        if (c != null) return c;

        CopyOnWriteFloats out = allocateFloats(cornerNormals.count(mesh.cornerVerts()));
        cornerNormals.compute(
                mesh.positions(), mesh.cornerVerts(), mesh.faceOffsets(),
                mesh.edgeEndpoints(), smoothAngleRadians, out);
        cachedCornerNormals = out;
        return cachedCornerNormals;
    }

    /**
     * 面角 → 边映射。int[ncorners]。
     *
     * <p>依赖：topology。边来自 {@code mesh.edgeEndpoints()}。
     */
    public CopyOnWriteInts cornerEdges() {
        CopyOnWriteInts c = cachedCornerEdges;
        if (c != null) return c;

        CopyOnWriteInts out = allocateInts(cornerEdgeLookup.count(mesh.cornerVerts()));
        cornerEdgeLookup.compute(
                mesh.cornerVerts(), mesh.faceOffsets(), mesh.edgeEndpoints(), out);
        cachedCornerEdges = out;
        return cachedCornerEdges;
    }

    /**
     * 切线。float4[ncorners]——xyz + 手性 w。
     *
     * <p>依赖：positions + uv + normals + triangleIndices。
     *
     * <p>通过 {@link #cornerNormals()} 取法线——保证 cachedCornerNormals
     * 已就绪。
     */
    public CopyOnWriteFloats tangents() {
        CopyOnWriteFloats c = cachedTangents;
        if (c != null) return c;

        CopyOnWriteFloats uv0 = mesh.cornerUv0();
        if (uv0 == null) {
            throw new IllegalStateException(
                    "tangents require UV0; mesh has no UV set. "
                            + "Generate a default UV set before requesting tangents.");
        }

        CopyOnWriteFloats normals    = cornerNormals();
        CopyOnWriteInts   triIndices = triangleIndices();
        CopyOnWriteFloats out = allocateFloats(tangents.count(mesh.cornerVerts()));
        tangents.compute(
                mesh.positions(), mesh.cornerVerts(), mesh.faceOffsets(),
                uv0, normals, triIndices, out);
        cachedTangents = out;
        return cachedTangents;
    }

    /**
     * 包围盒。
     *
     * <p>依赖：positions。
     */
    public IAABB bound() {
        ImmutableAABB c = cachedBounds;
        if (c != null) return c;

        AABB aabb = new AABB();
        minMaxScan.compute(mesh.positions(), aabb);
        cachedBounds = aabb.toImmutable();
        return cachedBounds;
    }

    /**
     * 图元 AABB。float6[nfaces]——[minX, minY, minZ, maxX, maxY, maxZ] × nfaces。
     *
     * <p>依赖：positions + topology。
     *
     * <p>用途：BVH / KD 构建的输入。
     */
    public CopyOnWriteFloats faceAabbs() {
        CopyOnWriteFloats c = cachedFaceAabbs;
        if (c != null) return c;

        CopyOnWriteFloats out = allocateFloats(faceAabbs.count(mesh.faceOffsets()));
        faceAabbs.compute(mesh.positions(), mesh.cornerVerts(), mesh.faceOffsets(), out);
        cachedFaceAabbs = out;
        return cachedFaceAabbs;
    }

    // ═══════════════════════════════════════════════
    // 丢弃派生
    // ═══════════════════════════════════════════════

    private void discardCornerNormals() {
        if (cachedCornerNormals != null) {
            try { cachedCornerNormals.close(); }
            catch (Exception e) { throw new RuntimeException(e); }
            cachedCornerNormals = null;
        }
    }

    private void discardCornerEdges() {
        if (cachedCornerEdges != null) {
            try { cachedCornerEdges.close(); }
            catch (Exception e) { throw new RuntimeException(e); }
            cachedCornerEdges = null;
        }
    }

    private void discardTangents() {
        if (cachedTangents != null) {
            try { cachedTangents.close(); }
            catch (Exception e) { throw new RuntimeException(e); }
            cachedTangents = null;
        }
    }

    private void discardBounds() {
        if (cachedBounds != null) {
            cachedBounds = null;
        }
    }

    private void discardFaceAabbs() {
        if (cachedFaceAabbs != null) {
            try { cachedFaceAabbs.close(); }
            catch (Exception e) { throw new RuntimeException(e); }
            cachedFaceAabbs = null;
        }
    }

    private void discardCornerPositions() {
        if (cachedCornerPositions != null) {
            try { cachedCornerPositions.close(); }
            catch (Exception e) { throw new RuntimeException(e); }
            cachedCornerPositions = null;
        }
    }

    private void discardTriangleIndices() {
        if (cachedTriangleIndices != null) {
            try { cachedTriangleIndices.close(); }
            catch (Exception e) { throw new RuntimeException(e); }
            cachedTriangleIndices = null;
        }
    }

    private void discardAll() {
        discardCornerPositions();
        discardTriangleIndices();
        discardCornerNormals();
        discardCornerEdges();
        discardTangents();
        discardBounds();
        discardFaceAabbs();
    }

    // ═══════════════════════════════════════════════
    // 共享 / 关闭
    // ═══════════════════════════════════════════════

    @Override
    public ExtendedMesh shared() {
        ExtendedMesh m = new ExtendedMesh(
                mesh.shared(), floatTemplate, intTemplate, smoothAngleRadians);

        m.cachedCornerPositions = cachedCornerPositions == null ? null : cachedCornerPositions.shared();
        m.cachedCornerNormals   = cachedCornerNormals   == null ? null : cachedCornerNormals.shared();
        m.cachedCornerEdges     = cachedCornerEdges     == null ? null : cachedCornerEdges.shared();
        m.cachedTangents        = cachedTangents        == null ? null : cachedTangents.shared();
        m.cachedBounds          = cachedBounds;
        m.cachedFaceAabbs       = cachedFaceAabbs       == null ? null : cachedFaceAabbs.shared();
        m.cachedTriangleIndices = cachedTriangleIndices == null ? null : cachedTriangleIndices.shared();

        return m;
    }

    @Override
    public void close() {
        cachedBounds = null;
        RuntimeException ex = ResourceUtils.closeDelayError(
                cachedCornerPositions, cachedCornerNormals, cachedCornerEdges,
                cachedTangents, cachedFaceAabbs, cachedTriangleIndices,
                mesh);
        if (ex != null) throw ex;
    }

    // ═══════════════════════════════════════════════
    // 分配
    // ═══════════════════════════════════════════════

    private CopyOnWriteFloats allocateFloats(long floatCount) {
        long byteSize = floatCount * Float.BYTES;
        AllocateResult<FloatArenaView> r = floatTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow float allocation failed unexpectedly: " + byteSize + " bytes");
        }
        return new DefaultCopyOnWriteFloats(r.getView());
    }

    private CopyOnWriteInts allocateInts(long intCount) {
        long byteSize = intCount * Integer.BYTES;
        AllocateResult<IntArenaView> r = intTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow int allocation failed unexpectedly: " + byteSize + " bytes");
        }
        return new DefaultCopyOnWriteInts(r.getView());
    }
}