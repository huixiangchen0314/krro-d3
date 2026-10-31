package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.geometry.algo.*;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.*;
import top.kzre.krro.util.arena.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

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
 * <p><b>失效规则</b>：
 * <pre>
 *   positionForWrite()     → cornerPositions / cornerNormals / tangents / bounds / faceAabbs
 *   cornerUvForWrite()     → tangents
 *   cornerVertsForWrite()  → 全部派生
 *   faceOffsetsForWrite()  → 全部派生
 *   setSmoothAngle()       → cornerNormals / tangents
 * </pre>
 */
public final class ExtendedMesh implements CopyOnWrite<ExtendedMesh> {

    // 算法
    private final MinMaxScanAlgo minMaxScan = new MinMaxScanImpl();
    private final CornerPositionGatherAlgo cornerPositionGather = new CornerPositionGatherImpl();
    private final TriangulationAlgo triangulation = new MikkTriangulationImpl();
    private final FaceAabbAlgo faceAabbs = new FaceAabbScanImpl();
    private final CornerEdgeLookupAlgo cornerEdgeLookup = new CornerEdgeLookupImpl();
    private final CornerNormalsAlgo cornerNormals = new EdgeAdjacentNormalsImpl();
    private final TangentAlgo tangents = new WeldedMikkTangentsImpl();
    // ═══════════════════════════════════════════════
    // 字段
    // ═══════════════════════════════════════════════

    private final Mesh mesh;
    private final ArenaTemplate<?, FloatArenaView> floatTemplate;
    private final ArenaTemplate<?, IntArenaView> intTemplate;

    /**
     * 平滑角度阈值——弧度——用于面角法线计算。
     */
    private float smoothAngleRadians;

    /**
     * 面角法线缓存。float3[ncorners]。null 表示未计算。
     */
    private CopyOnWriteFloats cachedCornerNormals;

    /**
     * 面角 → 边映射缓存。int[ncorners]。null 表示未计算。
     */
    private CopyOnWriteInts cachedCornerEdges;

    /**
     * 切线缓存。float4[ncorners]。null 表示未计算。
     */
    private CopyOnWriteFloats cachedTangents;

    /**
     * 包围盒缓存。float[6]。null 表示未计算。
     */
    private CopyOnWriteFloats cachedBounds;

    /**
     * 图元 AABB 缓存。float6[nfaces]。null 表示未计算。
     */
    private CopyOnWriteFloats cachedFaceAabbs;

    /**
     * 面角位置缓存。float3[ncorners]。
     *
     * <p>依赖：positions + cornerVerts。per-corner 的 position——GPU
     * 顶点是 per-corner 的，这个重排是渲染的必要步骤。
     */
    private CopyOnWriteFloats cachedCornerPositions;

    /**
     * 三角化 corner 索引缓存。int[ntriangles * 3]。
     *
     * <p>依赖：cornerVerts + faceOffsets。配合 {@link #getCornerPositions()}
     * 使用——corner 索引直接索引 per-corner 顶点缓冲（1:1 拆分）。
     */
    private CopyOnWriteInts cachedTriangleIndices;

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
    // Mesh 访问
    // ═══════════════════════════════════════════════

    /**
     * 直接暴露，不需要 share。
     */
    public Mesh mesh() {
        return mesh;
    }

    public long vertCount() {
        return mesh.vertCount();
    }

    public long cornerCount() {
        return mesh.cornerCount();
    }

    public long faceCount() {
        return mesh.faceCount();
    }

    // ═══════════════════════════════════════════════
    // 本质数据——读
    // ═══════════════════════════════════════════════

    public CopyOnWriteFloats positions() {
        return mesh.positions();
    }

    public float[] getPositionsSnapshot() {
        return mesh.positions().getFloatsSnapshot();
    }

    public FloatBuffer getPositionBufferSnapshot() {
        return mesh.positions().getFloatBufferSnapshot();
    }

    public int[] getCornerVertsSnapshot() {
        return mesh.cornerVerts().getIntsSnapshot();
    }

    public IntBuffer getCornerVertBufferSnapshot() {
        return mesh.cornerVerts().getIntBufferSnapshot();
    }

    public int[] getFaceOffsetsSnapshot() {
        return mesh.faceOffsets().getIntsSnapshot();
    }

    public IntBuffer getFaceOffsetBufferSnapshot() {
        return mesh.faceOffsets().getIntBufferSnapshot();
    }

    /**
     * 面角位置。float3[ncorners]——per-corner 的 position。
     *
     * <p>依赖：positions + cornerVerts。
     */
    public CopyOnWriteFloats getCornerPositions() {
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
     * <p>依赖：cornerVerts + faceOffsets。配合 {@link #getCornerPositions()}
     * 使用——corner 索引直接索引 per-corner 顶点缓冲。
     */
    public CopyOnWriteInts getTriangleIndices() {
        CopyOnWriteInts c = cachedTriangleIndices;
        if (c != null) return c;

        CopyOnWriteInts out = allocateInts(triangulation.count(mesh.faceOffsets()));
        triangulation.compute(
                mesh.positions(), mesh.cornerVerts(), mesh.faceOffsets(),
                mesh.cornerUvs().getSnapshot().getList().getFirst(), out);
        cachedTriangleIndices = out;
        return cachedTriangleIndices;
    }

    // ═══════════════════════════════════════════════
    // 本质数据——写(写必须经过装饰，才能维护好缓存)
    // ═══════════════════════════════════════════════

    /**
     * 声明写位置。
     *
     * <p><b>副作用</b>：丢弃 cornerPositions / cornerNormals / tangents /
     * bounds / faceAabbs。
     */
    public FloatBuffer positionForWrite() {
        discardCornerPositions();
        discardCornerNormals();
        discardTangents();
        discardBounds();
        discardFaceAabbs();
        return mesh.positions().getFloatBufferForWrite();
    }

    /**
     * 声明写面角 → 顶点——拓扑变化——丢弃全部派生。
     */
    public IntBuffer cornerVertsForWrite() {
        discardAll();
        return mesh.cornerVerts().getIntBufferForWrite();
    }

    /**
     * 声明写面 → 角范围偏移——拓扑变化——丢弃全部派生。
     */
    public IntBuffer faceOffsetsForWrite() {
        discardAll();
        return mesh.faceOffsets().getIntBufferForWrite();
    }

    // ═══════════════════════════════════════════════
    // 派生数据——读
    // ═══════════════════════════════════════════════

    /**
     * 面角法线。float3[ncorners]。
     *
     * <p>依赖：positions + topology + smoothAngleRadians。
     */
    public CopyOnWriteFloats getCornerNormals() {
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
    public CopyOnWriteInts getCornerEdges() {
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
     * <p>依赖：positions + uv + normals。
     *
     * <p>通过 {@link #getCornerNormals()} 取法线——保证 cachedCornerNormals
     * 已就绪（直接调 getTangents 而没先调 getCornerNormals 时不会 NPE）。
     */
    public CopyOnWriteFloats getTangents() {
        CopyOnWriteFloats c = cachedTangents;
        if (c != null) return c;

        CopyOnWriteFloats uv0 = mesh.cornerUv0();
        if (uv0 == null) {
            throw new IllegalStateException(
                    "tangents require UV0; mesh has no UV set. "
                            + "Generate a default UV set before requesting tangents.");
        }

        CopyOnWriteFloats normals    = getCornerNormals();
        CopyOnWriteInts   triIndices = getTriangleIndices();   // ← 复用渲染三角化
        CopyOnWriteFloats out = allocateFloats(tangents.count(mesh.cornerVerts()));
        tangents.compute(
                mesh.positions(), mesh.cornerVerts(), mesh.faceOffsets(),
                uv0, normals, triIndices, out);
        cachedTangents = out;
        return cachedTangents;
    }


    /**
     * 包围盒。float[6] = [minX, minY, minZ, maxX, maxY, maxZ]。
     *
     * <p>依赖：positions。
     */
    public CopyOnWriteFloats getBounds() {
        CopyOnWriteFloats c = cachedBounds;
        if (c != null) return c;

        CopyOnWriteFloats out = allocateFloats(minMaxScan.count());
        minMaxScan.compute(mesh.positions(), out);
        cachedBounds = out;
        return cachedBounds;
    }

    /**
     * 图元 AABB。float6[nfaces]——[minX, minY, minZ, maxX, maxY, maxZ] × nfaces。
     *
     * <p>依赖：positions + topology。
     *
     * <p>用途：BVH / KD 构建的输入。
     */
    public CopyOnWriteFloats getFaceAabbs() {
        CopyOnWriteFloats c = cachedFaceAabbs;
        if (c != null) return c;

        CopyOnWriteFloats out = allocateFloats(faceAabbs.count(mesh.faceOffsets()));
        faceAabbs.compute(mesh.positions(), mesh.cornerVerts(), mesh.faceOffsets(), out);
        cachedFaceAabbs = out;
        return cachedFaceAabbs;
    }

    // ═══════════════════════════════════════════════
    // 分配——走 AutoGrow Template
    // ═══════════════════════════════════════════════

    /**
     * 分配 + 拷贝已有数组。
     *
     * <p>只给"算法还返回 {@code float[]}"的过渡路径用——当前仅
     * {@link #getCornerNormals()}。等 {@link AngleClusteredNormalsImpl} 改成
     * view 输出后删除。
     */
    private CopyOnWriteFloats allocateFloats(float[] data) {
        int byteSize = data.length * Float.BYTES;

        AllocateResult<FloatArenaView> r = floatTemplate.allocate(byteSize);

        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow float allocation failed unexpectedly: "
                            + byteSize + " bytes");
        }

        FloatArenaView view = r.getView();
        view.floatBuffer().put(data);
        return new DefaultCopyOnWriteFloats(view);
    }

    // ═══════════════════════════════════════════════
    // 丢弃派生
    // ═══════════════════════════════════════════════

    private void discardCornerNormals() {
        if (cachedCornerNormals != null) {
            try {
                cachedCornerNormals.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedCornerNormals = null;
        }
    }

    private void discardCornerEdges() {
        if (cachedCornerEdges != null) {
            try {
                cachedCornerEdges.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedCornerEdges = null;
        }
    }

    private void discardTangents() {
        if (cachedTangents != null) {
            try {
                cachedTangents.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedTangents = null;
        }
    }

    private void discardBounds() {
        if (cachedBounds != null) {
            try {
                cachedBounds.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedBounds = null;
        }
    }

    private void discardFaceAabbs() {
        if (cachedFaceAabbs != null) {
            try {
                cachedFaceAabbs.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedFaceAabbs = null;
        }
    }

    private void discardAll() {
        discardCornerPositions();
        discardCornerNormals();
        discardCornerEdges();
        discardTangents();
        discardBounds();
        discardFaceAabbs();
        discardTriangleIndices();
    }

    private void discardCornerPositions() {
        if (cachedCornerPositions != null) {
            try {
                cachedCornerPositions.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedCornerPositions = null;
        }
    }

    private void discardTriangleIndices() {
        if (cachedTriangleIndices != null) {
            try {
                cachedTriangleIndices.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cachedTriangleIndices = null;
        }
    }

    // ═══════════════════════════════════════════════
    // 共享
    // ═══════════════════════════════════════════════

    @Override
    public ExtendedMesh shared() {
        ExtendedMesh m = new ExtendedMesh(
                mesh.shared(), floatTemplate, intTemplate, smoothAngleRadians);

        m.cachedCornerPositions = cachedCornerPositions == null ? null : cachedCornerPositions.shared();
        m.cachedCornerNormals = cachedCornerNormals == null ? null : cachedCornerNormals.shared();
        m.cachedCornerEdges = cachedCornerEdges == null ? null : cachedCornerEdges.shared();
        m.cachedTangents = cachedTangents == null ? null : cachedTangents.shared();
        m.cachedBounds = cachedBounds == null ? null : cachedBounds.shared();
        m.cachedFaceAabbs = cachedFaceAabbs == null ? null : cachedFaceAabbs.shared();
        m.cachedTriangleIndices = cachedTriangleIndices == null ? null : cachedTriangleIndices.shared();

        return m;
    }

    // ═══════════════════════════════════════════════
    // 关闭
    // ═══════════════════════════════════════════════

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(
                cachedCornerPositions, cachedCornerNormals, cachedCornerEdges,
                cachedTangents, cachedBounds, cachedFaceAabbs,
                cachedTriangleIndices,
                mesh);
        if (ex != null) throw ex;
    }

    // ═══════════════════════════════════════════════
    // 分配视图——算法直接写入
    // ═══════════════════════════════════════════════

    /**
     * 分配 float arena 视图——不初始化，由算法直接写入。
     *
     * <p>和 {@link #allocateFloats(float[])} 的差异：那个是"分配 + 拷贝
     * 已有数组"，这个是"分配 + 返回视图"——算法写入视图，避免二次复制。
     */
    private FloatArenaView allocateFloatView(int floatCount) {
        int byteSize = floatCount * Float.BYTES;
        AllocateResult<FloatArenaView> r = floatTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow float allocation failed unexpectedly: "
                            + byteSize + " bytes");
        }
        return r.getView();
    }

    private IntArenaView allocateIntView(int intCount) {
        int byteSize = intCount * Integer.BYTES;
        AllocateResult<IntArenaView> r = intTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow int allocation failed unexpectedly: "
                            + byteSize + " bytes");
        }
        return r.getView();
    }

    private CopyOnWriteFloats allocateFloats(long floatCount) {
        long byteSize = floatCount * Float.BYTES;
        AllocateResult<FloatArenaView> r = floatTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow float allocation failed unexpectedly: " + byteSize + " bytes");
        }
        return new DefaultCopyOnWriteFloats(r.getView());
    }

    private CopyOnWriteInts allocateInts(long floatCount) {
        long byteSize = floatCount * Integer.BYTES;
        AllocateResult<IntArenaView> r = intTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow float allocation failed unexpectedly: " + byteSize + " bytes");
        }
        return new DefaultCopyOnWriteInts(r.getView());
    }


}