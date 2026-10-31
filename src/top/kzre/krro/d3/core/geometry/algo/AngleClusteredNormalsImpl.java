package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * 面角法线——角度阈值 + 并查集聚类。
 *
 * <p><b>算法</b>：
 * <ol>
 *   <li>逐面 Newell 法线（面积加权）</li>
 *   <li>顶点 → 角列表（CSR）</li>
 *   <li>同顶点周围——面法线夹角 ≤ 阈值则并查集合并</li>
 *   <li>按连通分量累加面法线——归一化</li>
 * </ol>
 *
 * <p><b>输出</b>：{@code float3[ncorners]}——直接写 {@code out} 的底层数组。
 *
 * <p><b>写入方式</b>：按顶点遍历，写入顺序不是 corner 顺序——用绝对位置写
 * {@code o[oOff + ci * 3] = ...}。不碰 {@code FloatBuffer}。
 *
 * <p><b>edgeEndpoints</b>：当前全对比较实现忽略此参数——为后续
 * “边邻接并查集”预留。传 null 或非 null 都不影响当前行为。
 *
 * <p><b>线程契约</b>：只读 {@code positions} / {@code cornerVerts} /
 * {@code faceOffsets}，写 {@code out}。内部 ThreadLocal 缓冲按线程隔离。
 */
public final class AngleClusteredNormalsImpl implements CornerNormalsAlgo {

    // ── ThreadLocal 复用缓冲 ──

    private static final ThreadLocal<int[]>   UNION_PARENT =
            ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<float[]> UNION_ACCUM =
            ThreadLocal.withInitial(() -> new float[1024 * 3]);
    private static final ThreadLocal<float[]> FACE_NORMALS =
            ThreadLocal.withInitial(() -> new float[1024 * 3]);
    private static final ThreadLocal<int[]>   CORNER_FACE =
            ThreadLocal.withInitial(() -> new int[1024]);

    public AngleClusteredNormalsImpl() {}

    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteInts edgeEndpoints,
                        float angleThresholdRadians,
                        CopyOnWriteFloats out) {
        if (positions == null)   throw new NullPointerException("positions");
        if (cornerVerts == null) throw new NullPointerException("cornerVerts");
        if (faceOffsets == null) throw new NullPointerException("faceOffsets");
        if (out == null)         throw new NullPointerException("out");
        if (angleThresholdRadians < 0f) {
            throw new IllegalArgumentException(
                    "angleThresholdRadians must be >= 0, got " + angleThresholdRadians);
        }

        long posCount = positions.count();
        if (posCount % 3 != 0) {
            throw new IllegalArgumentException(
                    "positions count must be multiple of 3, got " + posCount);
        }

        long cornerCount = cornerVerts.count();
        long needed      = cornerCount * 3L;
        if (out.count() < needed) {
            throw new IllegalArgumentException(
                    "out needs " + needed + " floats, has " + out.count());
        }

        if (cornerCount == 0) return;

        // ── 读 ──
        float[] pos    = positions.getFloatsSnapshot();
        int     pOff   = (int) positions.floatOffset();
        int[]   cv     = cornerVerts.getIntsSnapshot();
        int     cvOff  = (int) cornerVerts.intOffset();
        int[]   fo     = faceOffsets.getIntsSnapshot();
        int     foOff  = (int) faceOffsets.intOffset();

        int ncorners  = (int) cornerCount;
        int faceCount = (int) faceOffsets.count() - 1;
        int vertCount = (int) (posCount / 3);

        // ── 写——先 ForWrite，再取 offset ──
        float[] o    = out.getFloatsForWrite();
        int     oOff = (int) out.floatOffset();

        // ── 1. 面法线 ──
        float[] faceNormals = getFaceNormalsBuffer(faceCount * 3);
        computeFaceNormals(fo, foOff, cv, cvOff, pos, pOff, faceCount, faceNormals);

        // ── 2. 角 → 面 ──
        int[] cornerFace = getCornerFaceBuffer(ncorners);
        for (int f = 0; f < faceCount; f++) {
            int start = fo[foOff + f];
            int end   = fo[foOff + f + 1];
            for (int c = start; c < end; c++) cornerFace[c] = f;
        }

        // ── 3. 顶点 → 角列表（CSR）──
        int[] vertCornerCount = new int[vertCount];
        for (int c = 0; c < ncorners; c++) {
            vertCornerCount[cv[cvOff + c]]++;
        }

        int[] vertCornerOffset = new int[vertCount + 1];
        for (int v = 0; v < vertCount; v++) {
            vertCornerOffset[v + 1] = vertCornerOffset[v] + vertCornerCount[v];
        }

        int[] vertCorners = new int[ncorners];
        int[] writePos    = new int[vertCount];
        System.arraycopy(vertCornerOffset, 0, writePos, 0, vertCount);
        for (int c = 0; c < ncorners; c++) {
            int v = cv[cvOff + c];
            vertCorners[writePos[v]++] = c;
        }

        // ── 4. 聚类 + 累加 ──
        float cosThreshold = (float) Math.cos(angleThresholdRadians);

        int[]   parent = getUnionParentBuffer(ncorners);
        float[] accum  = getUnionAccumBuffer(ncorners * 3);

        for (int v = 0; v < vertCount; v++) {
            int start = vertCornerOffset[v];
            int end   = vertCornerOffset[v + 1];
            int k     = end - start;
            if (k == 0) continue;

            // 单角——直接用面法线
            if (k == 1) {
                int c  = vertCorners[start];
                int f  = cornerFace[c];
                int cb = oOff + c * 3;
                o[cb]     = faceNormals[f * 3];
                o[cb + 1] = faceNormals[f * 3 + 1];
                o[cb + 2] = faceNormals[f * 3 + 2];
                continue;
            }

            // 并查集初始化
            for (int i = 0; i < k; i++) parent[i] = i;

            // 合并相似法线
            for (int i = 0; i < k; i++) {
                int fi = cornerFace[vertCorners[start + i]];
                float nix = faceNormals[fi * 3];
                float niy = faceNormals[fi * 3 + 1];
                float niz = faceNormals[fi * 3 + 2];

                for (int j = i + 1; j < k; j++) {
                    int fj = cornerFace[vertCorners[start + j]];
                    float dot = nix * faceNormals[fj * 3]
                            + niy * faceNormals[fj * 3 + 1]
                            + niz * faceNormals[fj * 3 + 2];

                    if (dot > cosThreshold) {
                        int ri = find(parent, i);
                        int rj = find(parent, j);
                        if (ri != rj) parent[ri] = rj;
                    }
                }
            }

            // 清零累加器
            for (int i = 0; i < k; i++) {
                accum[i * 3]     = 0f;
                accum[i * 3 + 1] = 0f;
                accum[i * 3 + 2] = 0f;
            }

            // 按根累加
            for (int i = 0; i < k; i++) {
                int fi   = cornerFace[vertCorners[start + i]];
                int root = find(parent, i);
                accum[root * 3]     += faceNormals[fi * 3];
                accum[root * 3 + 1] += faceNormals[fi * 3 + 1];
                accum[root * 3 + 2] += faceNormals[fi * 3 + 2];
            }

            // 路径压缩
            for (int i = 0; i < k; i++) find(parent, i);

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

            // 赋给角——直接写底层数组
            for (int i = 0; i < k; i++) {
                int ci   = vertCorners[start + i];
                int root = parent[i];
                int cb   = oOff + ci * 3;
                o[cb]     = accum[root * 3];
                o[cb + 1] = accum[root * 3 + 1];
                o[cb + 2] = accum[root * 3 + 2];
            }
        }
    }

    // ── ThreadLocal 增长 ──

    private static float[] getFaceNormalsBuffer(int needed) {
        float[] buf = FACE_NORMALS.get();
        if (buf.length < needed) { buf = new float[needed]; FACE_NORMALS.set(buf); }
        return buf;
    }

    private static int[] getCornerFaceBuffer(int needed) {
        int[] buf = CORNER_FACE.get();
        if (buf.length < needed) { buf = new int[needed]; CORNER_FACE.set(buf); }
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

    // ── 并查集 ──

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    // ── Newell 面法线 ──

    private static void computeFaceNormals(
            int[]   faceOffsets, int foOff,
            int[]   cornerVerts, int cvOff,
            float[] positions,   int pOff,
            int     faceCount,
            float[] out) {

        for (int f = 0; f < faceCount; f++) {
            int start = faceOffsets[foOff + f];
            int end   = faceOffsets[foOff + f + 1];
            int n     = end - start;

            if (n < 3) {
                out[f * 3]     = 0f;
                out[f * 3 + 1] = 1f;
                out[f * 3 + 2] = 0f;
                continue;
            }

            float nx = 0f, ny = 0f, nz = 0f;

            for (int i = 0; i < n; i++) {
                int vCurr = cornerVerts[cvOff + start + i];
                int vNext = cornerVerts[cvOff + start + (i + 1) % n];

                int bCurr = pOff + vCurr * 3;
                int bNext = pOff + vNext * 3;

                float x0 = positions[bCurr];
                float y0 = positions[bCurr + 1];
                float z0 = positions[bCurr + 2];
                float x1 = positions[bNext];
                float y1 = positions[bNext + 1];
                float z1 = positions[bNext + 2];

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
}