package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.LongIntMap;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.FloatArenaView;
import top.kzre.krro.util.arena.IntArenaView;

import java.util.Arrays;

/**
 * 面角法线——全局边邻接并查集。
 *
 * <p><b>与全对比较的区别</b>：只合并“共享一条边”的 corner。共享顶点但
 * 不共享边的 corner 即使法线夹角在阈值内也不合并。
 *
 * <p><b>全局并查集</b>：所有 corner 在同一个 {@code parent[cornerCount]} 里
 * 合并——同一个 corner 只属于一个全局连通分量，只赋值一次。避免了
 * per-vertex 局部并查集“corner 在两个 vertex 的列表里各赋一次、
 * 最终值依赖遍历顺序”的问题。
 *
 * <p><b>退化面</b>（{@code n < 3}）：面法线为 {@code (0, 1, 0)}。退化面的
 * corner 不参与合并——它们的法线无效，合并会污染相邻正常面。输出时
 * 退化 corner 直接写 {@code (0, 1, 0)}。
 *
 * <p><b>非流形边</b>（一条边被 3+ 个 corner 引用）：<b>不合并</b>——
 * 视为硬边，两侧 corner 法线不连续。非流形边没有唯一的切平面——
 * “和哪一侧的面平滑”在数学上是歧义的。跳过合并是保守且确定的行为：
 * 不猜测用户意图，把“清理非流形边”留给编辑流程。
 *
 * <p><b>Session 访问</b>：通过 {@link FloatArenaView.ArraySession} /
 * {@link IntArenaView.ArraySession} 访问底层数组与偏移——会话生命周期
 * 由 try-with-resources 保证。{@code out} 用 {@code arrayForWrite()}
 * （写会话，可能触发 COW 复制）；输入用 {@code array()}（读会话，不触发复制）。
 *
 * <p><b>线程契约</b>：只读输入，写 {@code out}。ThreadLocal 缓冲按线程隔离。
 */
public final class EdgeAdjacentNormalsImpl implements CornerNormalsAlgo {

    // ═══════════════════════════════════════════════
    // ThreadLocal 复用缓冲
    // ═══════════════════════════════════════════════

    private static final ThreadLocal<int[]>   CORNER_FACE =
            ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<float[]> FACE_NORMALS =
            ThreadLocal.withInitial(() -> new float[1024 * 3]);
    private static final ThreadLocal<byte[]>  DEGENERATE =
            ThreadLocal.withInitial(() -> new byte[1024]);
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

    public EdgeAdjacentNormalsImpl() {}

    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteInts edgeEndpoints,
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

        // 四个读会话 + 写会话。读会话不触发 COW 复制，写会话可能触发。
        // 空输入时提前返回，不打开写会话。
        try (FloatArenaView.ArraySession posSess = positions.array();
             IntArenaView.ArraySession  cvSess  = cornerVerts.array();
             IntArenaView.ArraySession  foSess  = faceOffsets.array();
             IntArenaView.ArraySession  eeSess  = edgeEndpoints.array()) {

            int posCount = posSess.count();
            if (posCount % 3 != 0) {
                throw new IllegalArgumentException(
                        "positions count must be multiple of 3, got " + posCount);
            }
            int edgeCount2 = eeSess.count();
            if (edgeCount2 % 2 != 0) {
                throw new IllegalArgumentException(
                        "edgeEndpoints count must be multiple of 2, got " + edgeCount2);
            }

            int ncorners = cvSess.count();
            if (ncorners == 0) return;

            long needed = (long) ncorners * 3L;

            // ── 读快照（会话生命周期内稳定）──
            float[] pos    = posSess.getFloats();
            int     pOff   = posSess.offset();
            int[]   cv     = cvSess.getInts();
            int     cvOff  = cvSess.offset();
            int[]   fo     = foSess.getInts();
            int     foOff  = foSess.offset();
            int[]   ee     = eeSess.getInts();
            int     eeOff  = eeSess.offset();

            int faceCount = foSess.count() - 1;
            int edgeCount = edgeCount2 / 2;

            // ── 写会话：容量检查 + 写 ──
            try (FloatArenaView.ArraySession outSess = out.arrayForWrite()) {
                if (outSess.count() < needed) {
                    throw new IllegalArgumentException(
                            "out needs " + needed + " floats, has " + outSess.count());
                }

                // ── 1. 面法线（Newell）──
                float[] faceNormals = getFaceNormalsBuffer(faceCount * 3);
                computeFaceNormals(fo, foOff, cv, cvOff, pos, pOff, faceCount, faceNormals);

                // ── 2. corner → face + 退化标记 ──
                int[]  cornerFace = getCornerFaceBuffer(ncorners);
                byte[] degenerate = getDegenerateBuffer(ncorners);
                Arrays.fill(degenerate, 0, ncorners, (byte) 0);

                for (int f = 0; f < faceCount; f++) {
                    int start = fo[foOff + f];
                    int end   = fo[foOff + f + 1];
                    boolean degen = (end - start) < 3;
                    for (int c = start; c < end; c++) {
                        cornerFace[c] = f;
                        if (degen) degenerate[c] = 1;
                    }
                }

                // ── 3. edge key → edge index 哈希表 ──
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

                // ── 4. corner → 起始边 index ──
                int[] cornerEdgeStart = getCornerEdgeStartBuffer(ncorners);
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

                // ── 5. edge → corner CSR ──
                int[] edgeCornerOffset = getEdgeCornerOffsetBuffer(edgeCount + 1);
                Arrays.fill(edgeCornerOffset, 0, edgeCount + 1, 0);
                for (int c = 0; c < ncorners; c++) {
                    int e = cornerEdgeStart[c];
                    if (e >= 0) edgeCornerOffset[e + 1]++;
                }
                for (int e = 1; e <= edgeCount; e++) {
                    edgeCornerOffset[e] += edgeCornerOffset[e - 1];
                }
                int[] edgeCorners  = getEdgeCornersBuffer(ncorners);
                int[] edgeWritePos = getEdgeWritePosBuffer(edgeCount);
                System.arraycopy(edgeCornerOffset, 0, edgeWritePos, 0, edgeCount);
                for (int c = 0; c < ncorners; c++) {
                    int e = cornerEdgeStart[c];
                    if (e >= 0) edgeCorners[edgeWritePos[e]++] = c;
                }

                // ── 6. 全局并查集 ──
                float cosThreshold = (float) Math.cos(angleThresholdRadians);

                int[] parent = getUnionParentBuffer(ncorners);
                for (int c = 0; c < ncorners; c++) parent[c] = c;

                for (int e = 0; e < edgeCount; e++) {
                    int eStart = edgeCornerOffset[e];
                    int eEnd   = edgeCornerOffset[e + 1];

                    // 边界边（1 个 corner）没有对——跳过
                    // 非流形边（3+ 个 corner）——总是跳过，大部分情况用户总是想清理非流形边的。
                    if (eEnd - eStart != 2) continue;

                    int ci = edgeCorners[eStart];
                    int cj = edgeCorners[eStart + 1];

                    // 退化面 corner 不参与合并
                    if (degenerate[ci] != 0 || degenerate[cj] != 0) continue;

                    int fi = cornerFace[ci];
                    int fj = cornerFace[cj];

                    float dot = faceNormals[fi * 3]     * faceNormals[fj * 3]
                            + faceNormals[fi * 3 + 1] * faceNormals[fj * 3 + 1]
                            + faceNormals[fi * 3 + 2] * faceNormals[fj * 3 + 2];

                    if (dot > cosThreshold) {
                        int ri = find(parent, ci);
                        int rj = find(parent, cj);
                        if (ri != rj) parent[ri] = rj;
                    }
                }

                // ── 7. 按根累加面法线（跳过退化 corner）──
                float[] accum = getUnionAccumBuffer(ncorners * 3);
                Arrays.fill(accum, 0, ncorners * 3, 0f);

                for (int c = 0; c < ncorners; c++) {
                    if (degenerate[c] != 0) continue;
                    int fi   = cornerFace[c];
                    int root = find(parent, c);
                    accum[root * 3]     += faceNormals[fi * 3];
                    accum[root * 3 + 1] += faceNormals[fi * 3 + 1];
                    accum[root * 3 + 2] += faceNormals[fi * 3 + 2];
                }

                // ── 8. 归一化根（跳过退化 corner）──
                for (int c = 0; c < ncorners; c++) {
                    if (degenerate[c] != 0) continue;
                    if (parent[c] != c) continue;

                    float x   = accum[c * 3];
                    float y   = accum[c * 3 + 1];
                    float z   = accum[c * 3 + 2];
                    float len = (float) Math.sqrt(x * x + y * y + z * z);

                    if (len > 1e-8f) {
                        float inv = 1f / len;
                        accum[c * 3]     = x * inv;
                        accum[c * 3 + 1] = y * inv;
                        accum[c * 3 + 2] = z * inv;
                    } else {
                        accum[c * 3]     = 0f;
                        accum[c * 3 + 1] = 1f;
                        accum[c * 3 + 2] = 0f;
                    }
                }

                // ── 9. 赋值 ──
                float[] o    = outSess.getFloats();
                int     oOff = outSess.offset();

                for (int c = 0; c < ncorners; c++) {
                    int cb = oOff + c * 3;
                    if (degenerate[c] != 0) {
                        // 退化面 corner——直接 (0, 1, 0)
                        o[cb]     = 0f;
                        o[cb + 1] = 1f;
                        o[cb + 2] = 0f;
                    } else {
                        int root = find(parent, c);
                        o[cb]     = accum[root * 3];
                        o[cb + 1] = accum[root * 3 + 1];
                        o[cb + 2] = accum[root * 3 + 2];
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    private static byte[] getDegenerateBuffer(int needed) {
        byte[] buf = DEGENERATE.get();
        if (buf.length < needed) { buf = new byte[needed]; DEGENERATE.set(buf); }
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
}