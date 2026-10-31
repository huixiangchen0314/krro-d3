package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.LongIntMap;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

import java.util.Arrays;

/**
 * 面角法线——边邻接并查集。
 *
 * <p><b>与全对比较的区别</b>：只合并“共享一条边”的 corner。共享顶点但
 * 不共享边的 corner 即使法线夹角在阈值内也不合并——修正了
 * “共享顶点不等于共享边”的语义错误。
 *
 * <p><b>算法</b>：
 * <ol>
 *   <li>逐面 Newell 法线（面积加权）</li>
 *   <li>corner → face 映射</li>
 *   <li>vertex → corner CSR</li>
 *   <li>edge key → edge index 哈希表（从 {@code edgeEndpoints}）</li>
 *   <li>corner → 起始边 index（{@code cornerEdgeStart}）</li>
 *   <li>edge → corner CSR（从 {@code cornerEdgeStart} 填充）</li>
 *   <li>对每个 vertex，收集它的 corner 列表；对每个 corner 的两条邻边，
 *       通过 edge → corner CSR 找到另一侧 corner，法线夹角 ≤ 阈值则并查集合并</li>
 *   <li>按连通分量累加面法线——归一化——赋给分量内的所有 corner</li>
 * </ol>
 *
 * <p><b>输出</b>：{@code float3[ncorners]}——直接写 {@code out} 的底层数组。
 *
 * <p><b>edgeEndpoints</b>：必须非 null——边邻接需要它。边界边（只有一侧
 * corner）不合并；不在 {@code edgeEndpoints} 中的边跳过。
 *
 * <p><b>线程契约</b>：只读输入，写 {@code out}。ThreadLocal 缓冲按线程隔离。
 */
public final class EdgeAdjacentNormalsImpl implements CornerNormalsAlgo {

    // ═══════════════════════════════════════════════
    // ThreadLocal 复用缓冲
    // ═══════════════════════════════════════════════

    private static final ThreadLocal<int[]>   VERT_CORNER_OFFSET =
            ThreadLocal.withInitial(() -> new int[1024 + 1]);
    private static final ThreadLocal<int[]>   VERT_CORNERS =
            ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<int[]>   WRITE_POS =
            ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<int[]>   CORNER_FACE =
            ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<float[]> FACE_NORMALS =
            ThreadLocal.withInitial(() -> new float[1024 * 3]);
    private static final ThreadLocal<LongIntMap> EDGE_MAP =
            ThreadLocal.withInitial(() -> new LongIntMap(1024));
    private static final ThreadLocal<int[]>   CORNER_EDGE_START =
            ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<int[]>   EDGE_CORNER_OFFSET =
            ThreadLocal.withInitial(() -> new int[1024 + 1]);
    private static final ThreadLocal<int[]>   EDGE_CORNERS =
            ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<int[]>   EDGE_WRITE_POS =
            ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<int[]>   UNION_PARENT =
            ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<float[]> UNION_ACCUM =
            ThreadLocal.withInitial(() -> new float[1024 * 3]);
    private static final ThreadLocal<int[]>   CORNER_LOCAL_INDEX =
            ThreadLocal.withInitial(() -> new int[1024]);

    public EdgeAdjacentNormalsImpl() {}

    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteInts edgeEndpoints,
                        // 平滑角
                        float angleThresholdRadians,
                        CopyOnWriteFloats out) {
        if (positions == null)     throw new NullPointerException("positions");
        if (cornerVerts == null)   throw new NullPointerException("cornerVerts");
        if (faceOffsets == null)   throw new NullPointerException("faceOffsets");
        if (edgeEndpoints == null) throw new NullPointerException("edgeEndpoints");
        if (out == null)           throw new NullPointerException("out");
        if (angleThresholdRadians < 0f) {
            throw new IllegalArgumentException(
                    "angleThresholdRadians must be >= 0, got " + angleThresholdRadians);
        }

        long posCount = positions.count();
        if (posCount % 3 != 0) {
            throw new IllegalArgumentException(
                    "positions count must be multiple of 3, got " + posCount);
        }
        long edgeCount2 = edgeEndpoints.count();
        if (edgeCount2 % 2 != 0) {
            throw new IllegalArgumentException(
                    "edgeEndpoints count must be multiple of 2, got " + edgeCount2);
        }

        long ncorners = cornerVerts.count();
        long needed   = ncorners * 3L;
        if (out.count() < needed) {
            throw new IllegalArgumentException(
                    "out needs " + needed + " floats, has " + out.count());
        }
        if (ncorners == 0) return;

        // ── 读 ──
        float[] pos   = positions.getFloatsSnapshot();
        int     pOff  = (int) positions.floatOffset();
        int[]   cv    = cornerVerts.getIntsSnapshot();
        int     cvOff = (int) cornerVerts.intOffset();
        int[]   fo    = faceOffsets.getIntsSnapshot();
        int     foOff = (int) faceOffsets.intOffset();
        int[]   ee    = edgeEndpoints.getIntsSnapshot();
        int     eeOff = (int) edgeEndpoints.intOffset();

        int ncornersI = (int) ncorners;
        int faceCount = (int) faceOffsets.count() - 1;
        int vertCount = (int) (posCount / 3);
        int edgeCount = (int) (edgeCount2 / 2);

        // ── 1. 面法线 ──
        float[] faceNormals = getFaceNormalsBuffer(faceCount * 3);
        computeFaceNormals(fo, foOff, cv, cvOff, pos, pOff, faceCount, faceNormals);

        // ── 2. corner → face ──
        int[] cornerFace = getCornerFaceBuffer(ncornersI);
        for (int f = 0; f < faceCount; f++) {
            int start = fo[foOff + f];
            int end   = fo[foOff + f + 1];
            for (int c = start; c < end; c++) cornerFace[c] = f;
        }

        // ── 3. vertex → corner CSR ──
        int[] vertCornerOffset = getVertCornerOffsetBuffer(vertCount + 1);
        Arrays.fill(vertCornerOffset, 0, vertCount + 1, 0);
        for (int c = 0; c < ncornersI; c++) {
            vertCornerOffset[cv[cvOff + c] + 1]++;
        }
        for (int v = 1; v <= vertCount; v++) {
            vertCornerOffset[v] += vertCornerOffset[v - 1];
        }
        int[] vertCorners = getVertCornersBuffer(ncornersI);
        int[] writePos    = getWritePosBuffer(vertCount);
        System.arraycopy(vertCornerOffset, 0, writePos, 0, vertCount);
        for (int c = 0; c < ncornersI; c++) {
            int v = cv[cvOff + c];
            vertCorners[writePos[v]++] = c;
        }

        // ── 4. edge key → edge index 哈希表 ──
        LongIntMap edgeMap = EDGE_MAP.get();
        edgeMap.ensureCapacity(edgeCount);
        for (int e = 0; e < edgeCount; e++) {
            int  v0  = ee[eeOff + e * 2];
            int  v1  = ee[eeOff + e * 2 + 1];
            int  a   = v0 < v1 ? v0 : v1;
            int  b   = v0 < v1 ? v1 : v0;
            long key = ((long) a << 32) | (b & 0xFFFFFFFFL);
            edgeMap.put(key, e);
        }

        // ── 5. corner → 起始边 index ──
        int[] cornerEdgeStart = getCornerEdgeStartBuffer(ncornersI);
        for (int f = 0; f < faceCount; f++) {
            int start = fo[foOff + f];
            int n     = fo[foOff + f + 1] - start;
            if (n < 2) continue;
            for (int i = 0; i < n; i++) {
                int c     = start + i;
                int vCurr = cv[cvOff + c];
                int vNext = cv[cvOff + start + (i + 1) % n];
                int a     = vCurr < vNext ? vCurr : vNext;
                int b     = vCurr < vNext ? vNext : vCurr;
                long key  = ((long) a << 32) | (b & 0xFFFFFFFFL);
                cornerEdgeStart[c] = edgeMap.get(key);
            }
        }

        // ── 6. edge → corner CSR ──
        int[] edgeCornerOffset = getEdgeCornerOffsetBuffer(edgeCount + 1);
        Arrays.fill(edgeCornerOffset, 0, edgeCount + 1, 0);
        for (int c = 0; c < ncornersI; c++) {
            int e = cornerEdgeStart[c];
            if (e >= 0) edgeCornerOffset[e + 1]++;
        }
        for (int e = 1; e <= edgeCount; e++) {
            edgeCornerOffset[e] += edgeCornerOffset[e - 1];
        }
        int[] edgeCorners = getEdgeCornersBuffer(ncornersI);
        int[] edgeWritePos = getEdgeWritePosBuffer(edgeCount);
        System.arraycopy(edgeCornerOffset, 0, edgeWritePos, 0, edgeCount);
        for (int c = 0; c < ncornersI; c++) {
            int e = cornerEdgeStart[c];
            if (e >= 0) edgeCorners[edgeWritePos[e]++] = c;
        }

        // ── 7. 按 vertex 分组做边邻接并查集 ──
        float cosThreshold = (float) Math.cos(angleThresholdRadians);

        int[] cornerLocalIndex = getCornerLocalIndexBuffer(ncornersI);
        Arrays.fill(cornerLocalIndex, 0, ncornersI, -1);

        float[] o    = out.getFloatsForWrite();
        int     oOff = (int) out.floatOffset();

        for (int v = 0; v < vertCount; v++) {
            int vStart = vertCornerOffset[v];
            int vEnd   = vertCornerOffset[v + 1];
            int k      = vEnd - vStart;
            if (k == 0) continue;

            // 单角——直接用面法线
            if (k == 1) {
                int c  = vertCorners[vStart];
                int f  = cornerFace[c];
                int cb = oOff + c * 3;
                o[cb]     = faceNormals[f * 3];
                o[cb + 1] = faceNormals[f * 3 + 1];
                o[cb + 2] = faceNormals[f * 3 + 2];
                continue;
            }

            // 并查集初始化
            int[]   parent = getUnionParentBuffer(k);
            float[] accum  = getUnionAccumBuffer(k * 3);
            for (int i = 0; i < k; i++) parent[i] = i;

            // 全局 corner → 局部索引
            for (int i = 0; i < k; i++) {
                cornerLocalIndex[vertCorners[vStart + i]] = i;
            }

            // 对每个 corner，检查两条边
            for (int i = 0; i < k; i++) {
                int c = vertCorners[vStart + i];
                int f = cornerFace[c];
                int fStart = fo[foOff + f];
                int fEnd   = fo[foOff + f + 1];
                int n      = fEnd - fStart;
                if (n < 2) continue;

                int posInFace = c - fStart;
                int prevC = (posInFace == 0) ? fEnd - 1 : c - 1;

                // 前一条边 (v_prev, v_c)——起始 corner 是 prevC
                mergeAcrossEdge(cornerEdgeStart[prevC], i, c,
                        edgeCornerOffset, edgeCorners, cornerLocalIndex,
                        cornerFace, faceNormals, parent, cosThreshold);

                // 后一条边 (v_c, v_next)——起始 corner 是 c
                mergeAcrossEdge(cornerEdgeStart[c], i, c,
                        edgeCornerOffset, edgeCorners, cornerLocalIndex,
                        cornerFace, faceNormals, parent, cosThreshold);
            }

            // 路径压缩
            for (int i = 0; i < k; i++) find(parent, i);

            // 清零累加器
            for (int i = 0; i < k; i++) {
                accum[i * 3]     = 0f;
                accum[i * 3 + 1] = 0f;
                accum[i * 3 + 2] = 0f;
            }

            // 按根累加面法线
            for (int i = 0; i < k; i++) {
                int fi   = cornerFace[vertCorners[vStart + i]];
                int root = find(parent, i);
                accum[root * 3]     += faceNormals[fi * 3];
                accum[root * 3 + 1] += faceNormals[fi * 3 + 1];
                accum[root * 3 + 2] += faceNormals[fi * 3 + 2];
            }

            // 归一化根
            for (int i = 0; i < k; i++) {
                if (parent[i] != i) continue;
                float x   = accum[i * 3];
                float y   = accum[i * 3 + 1];
                float z   = accum[i * 3 + 2];
                float len = (float) Math.sqrt(x * x + y * y + z * z);
                if (len > 1e-8f) {
                    float inv = 1f / len;
                    accum[i * 3]     = x * inv;
                    accum[i * 3 + 1] = y * inv;
                    accum[i * 3 + 2] = z * inv;
                } else {
                    accum[i * 3]     = 0f;
                    accum[i * 3 + 1] = 1f;
                    accum[i * 3 + 2] = 0f;
                }
            }

            // 赋给角
            for (int i = 0; i < k; i++) {
                int ci   = vertCorners[vStart + i];
                int root = parent[i];
                int cb   = oOff + ci * 3;
                o[cb]     = accum[root * 3];
                o[cb + 1] = accum[root * 3 + 1];
                o[cb + 2] = accum[root * 3 + 2];
            }

            // 清空全局 corner → 局部索引
            for (int i = 0; i < k; i++) {
                cornerLocalIndex[vertCorners[vStart + i]] = -1;
            }
        }
    }

    // ── 跨边合并 ──

    /**
     * 给定边 index，遍历引用这条边的 corner，找另一侧 corner，
     * 如果在当前 vertex 的列表里（{@code cornerLocalIndex >= 0}），
     * 法线夹角 ≤ 阈值则合并。
     *
     * @param edgeIndex        边的 index，{@code -1} 表示边不在 edgeEndpoints 中——跳过
     * @param localI           当前 corner 在 vertex 列表中的局部索引
     * @param c                当前 corner 的全局索引
     */
    private static void mergeAcrossEdge(int edgeIndex, int localI, int c,
                                        int[] edgeCornerOffset, int[] edgeCorners,
                                        int[] cornerLocalIndex,
                                        int[] cornerFace, float[] faceNormals,
                                        int[] parent, float cosThreshold) {
        if (edgeIndex < 0) return;

        int eStart = edgeCornerOffset[edgeIndex];
        int eEnd   = edgeCornerOffset[edgeIndex + 1];

        int fi = cornerFace[c];
        float nix = faceNormals[fi * 3];
        float niy = faceNormals[fi * 3 + 1];
        float niz = faceNormals[fi * 3 + 2];

        for (int k = eStart; k < eEnd; k++) {
            int other = edgeCorners[k];
            if (other == c) continue;

            int j = cornerLocalIndex[other];
            if (j < 0) continue;   // 另一侧 corner 不引用当前 vertex——跳过

            int fj = cornerFace[other];
            float dot = nix * faceNormals[fj * 3]
                    + niy * faceNormals[fj * 3 + 1]
                    + niz * faceNormals[fj * 3 + 2];

            if (dot > cosThreshold) {
                int ri = find(parent, localI);
                int rj = find(parent, j);
                if (ri != rj) parent[ri] = rj;
            }
        }
    }

    // ── 面法线（Newell）──

    private static void computeFaceNormals(int[] fo, int foOff,
                                           int[] cv, int cvOff,
                                           float[] pos, int pOff,
                                           int faceCount, float[] out) {
        for (int f = 0; f < faceCount; f++) {
            int start = fo[foOff + f];
            int end   = fo[foOff + f + 1];
            int n     = end - start;

            if (n < 3) {
                out[f * 3]     = 0f;
                out[f * 3 + 1] = 1f;
                out[f * 3 + 2] = 0f;
                continue;
            }

            float nx = 0f, ny = 0f, nz = 0f;
            for (int i = 0; i < n; i++) {
                int vCurr = cv[cvOff + start + i];
                int vNext = cv[cvOff + start + (i + 1) % n];
                int bCurr = pOff + vCurr * 3;
                int bNext = pOff + vNext * 3;

                float x0 = pos[bCurr],     y0 = pos[bCurr + 1], z0 = pos[bCurr + 2];
                float x1 = pos[bNext],     y1 = pos[bNext + 1], z1 = pos[bNext + 2];

                nx += (y0 - y1) * (z0 + z1);
                ny += (z0 - z1) * (x0 + x1);
                nz += (x0 - x1) * (y0 + y1);
            }

            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-8f) {
                float inv = 1f / len;
                out[f * 3]     = nx * inv;
                out[f * 3 + 1] = ny * inv;
                out[f * 3 + 2] = nz * inv;
            } else {
                out[f * 3]     = 0f;
                out[f * 3 + 1] = 1f;
                out[f * 3 + 2] = 0f;
            }
        }
    }

    // ── 并查集 ──

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    // ── ThreadLocal 增长 ──

    private static int[] getVertCornerOffsetBuffer(int needed) {
        int[] buf = VERT_CORNER_OFFSET.get();
        if (buf.length < needed) { buf = new int[needed]; VERT_CORNER_OFFSET.set(buf); }
        return buf;
    }

    private static int[] getVertCornersBuffer(int needed) {
        int[] buf = VERT_CORNERS.get();
        if (buf.length < needed) { buf = new int[needed]; VERT_CORNERS.set(buf); }
        return buf;
    }

    private static int[] getWritePosBuffer(int needed) {
        int[] buf = WRITE_POS.get();
        if (buf.length < needed) { buf = new int[needed]; WRITE_POS.set(buf); }
        return buf;
    }

    private static int[] getCornerFaceBuffer(int needed) {
        int[] buf = CORNER_FACE.get();
        if (buf.length < needed) { buf = new int[needed]; CORNER_FACE.set(buf); }
        return buf;
    }

    private static float[] getFaceNormalsBuffer(int needed) {
        float[] buf = FACE_NORMALS.get();
        if (buf.length < needed) { buf = new float[needed]; FACE_NORMALS.set(buf); }
        return buf;
    }

    private static int[] getCornerEdgeStartBuffer(int needed) {
        int[] buf = CORNER_EDGE_START.get();
        if (buf.length < needed) { buf = new int[needed]; CORNER_EDGE_START.set(buf); }
        return buf;
    }

    private static int[] getEdgeCornerOffsetBuffer(int needed) {
        int[] buf = EDGE_CORNER_OFFSET.get();
        if (buf.length < needed) { buf = new int[needed]; EDGE_CORNER_OFFSET.set(buf); }
        return buf;
    }

    private static int[] getEdgeCornersBuffer(int needed) {
        int[] buf = EDGE_CORNERS.get();
        if (buf.length < needed) { buf = new int[needed]; EDGE_CORNERS.set(buf); }
        return buf;
    }

    private static int[] getEdgeWritePosBuffer(int needed) {
        int[] buf = EDGE_WRITE_POS.get();
        if (buf.length < needed) { buf = new int[needed]; EDGE_WRITE_POS.set(buf); }
        return buf;
    }

    private static int[] getUnionParentBuffer(int needed) {
        int[] buf = UNION_PARENT.get();
        if (buf.length < needed) { buf = new int[needed]; UNION_PARENT.set(buf); }
        return buf;
    }

    private static float[] getUnionAccumBuffer(int needed) {
        float[] buf = UNION_ACCUM.get();
        if (buf.length < needed) { buf = new float[needed]; UNION_ACCUM.set(buf); }
        return buf;
    }

    private static int[] getCornerLocalIndexBuffer(int needed) {
        int[] buf = CORNER_LOCAL_INDEX.get();
        if (buf.length < needed) { buf = new int[needed]; CORNER_LOCAL_INDEX.set(buf); }
        return buf;
    }
}