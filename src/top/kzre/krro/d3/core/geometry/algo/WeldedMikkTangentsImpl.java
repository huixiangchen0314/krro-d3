package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

import java.util.Arrays;

/**
 * 切线——MikkTSpace 焊接组实现。
 *
 * <p><b>焊接组</b>：{@code (position, normal, uv)} 三元组相同的 corner 归为
 * 同一焊接组。{@code tan1/tan2} 按焊接组累加，而不是按 vertex。
 *
 * <p><b>三角化复用</b>：直接遍历 {@code triangleIndices}——和
 * {@link TriangulationAlgo} 输出的三角形完全一致。切线空间和渲染几何用
 * 同一份三角形列表，<b>不存在“切线用的三角化和渲染用的三角化不同”的问题</b>。
 *
 * <p><b>退化继承</b>：退化三角形（{@code |r| < ε}）不贡献 {@code tan1/tan2}。
 * corner 所属焊接组如果有非退化贡献，退化三角形的 corner 自动继承该组的
 * 切线；如果整个组没有有效贡献，fallback 到 {@code (1, 0, 0, 1)}。
 *
 * <p><b>输出</b>：{@code float4[ncorners]}——xyz + 手性 w。
 *
 * <p><b>线程契约</b>：只读输入，写 {@code out}。{@code TmpVert[]} 和
 * {@code groupTan1/2} 是每次调用分配的临时数组。
 */
public final class WeldedMikkTangentsImpl implements TangentAlgo {

    private static final float EPS   = 1e-8f;
    private static final float R_EPS = 1e-10f;

    public WeldedMikkTangentsImpl() {}

    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteFloats uv0,
                        CopyOnWriteFloats cornerNormals,
                        CopyOnWriteInts triangleIndices,
                        CopyOnWriteFloats out) {
        if (positions == null)       throw new NullPointerException("positions");
        if (cornerVerts == null)     throw new NullPointerException("cornerVerts");
        if (faceOffsets == null)     throw new NullPointerException("faceOffsets");
        if (uv0 == null)             throw new NullPointerException("uv0");
        if (cornerNormals == null)   throw new NullPointerException("cornerNormals");
        if (triangleIndices == null) throw new NullPointerException("triangleIndices");
        if (out == null)             throw new NullPointerException("out");

        long posCount = positions.count();
        if (posCount % 3 != 0) {
            throw new IllegalArgumentException(
                    "positions count must be multiple of 3, got " + posCount);
        }
        if (uv0.count() % 2 != 0) {
            throw new IllegalArgumentException(
                    "uv0 count must be multiple of 2, got " + uv0.count());
        }
        if (cornerNormals.count() % 3 != 0) {
            throw new IllegalArgumentException(
                    "cornerNormals count must be multiple of 3, got " + cornerNormals.count());
        }
        if (triangleIndices.count() % 3 != 0) {
            throw new IllegalArgumentException(
                    "triangleIndices count must be multiple of 3, got " + triangleIndices.count());
        }

        long ncorners = cornerVerts.count();
        long needed   = ncorners * 4L;
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
        float[] uv    = uv0.getFloatsSnapshot();
        int     uvOff = (int) uv0.floatOffset();
        float[] nrm   = cornerNormals.getFloatsSnapshot();
        int     nOff  = (int) cornerNormals.floatOffset();
        int[]   tri   = triangleIndices.getIntsSnapshot();
        int     triOff = (int) triangleIndices.intOffset();

        int ncornersI = (int) ncorners;
        int ntri      = (int) (triangleIndices.count() / 3);

        // ── 1. 构建 TmpVert 并排序 ──
        TmpVert[] tmp = new TmpVert[ncornersI];
        for (int c = 0; c < ncornersI; c++) {
            int v  = cv[cvOff + c];
            int pb = pOff + v * 3;
            int nb = nOff + c * 3;
            int ub = uvOff + c * 2;
            tmp[c] = new TmpVert(
                    pos[pb], pos[pb + 1], pos[pb + 2],
                    nrm[nb], nrm[nb + 1], nrm[nb + 2],
                    uv[ub], uv[ub + 1],
                    c);
        }
        Arrays.sort(tmp);

        // ── 2. 分组 ──
        int[] groupIdOfCorner = new int[ncornersI];
        int groupCount = 0;
        groupIdOfCorner[tmp[0].cornerIndex] = 0;
        for (int i = 1; i < ncornersI; i++) {
            if (tmp[i].compareTo(tmp[i - 1]) != 0) {
                groupCount++;
            }
            groupIdOfCorner[tmp[i].cornerIndex] = groupCount;
        }
        groupCount++;

        // ── 3. 遍历 triangleIndices 累加 ──
        float[] groupTan1 = new float[groupCount * 3];
        float[] groupTan2 = new float[groupCount * 3];

        // TODO 并行加速
        for (int t = 0; t < ntri; t++) {
            int ti = triOff + t * 3;
            int c0 = tri[ti];
            int c1 = tri[ti + 1];
            int c2 = tri[ti + 2];
            accumulateTriangle(c0, c1, c2,
                    pos, pOff, cv, cvOff, uv, uvOff,
                    groupIdOfCorner, groupTan1, groupTan2);
        }
        // TODO 并行加速
        // ── 4. 归一化 group ──
        for (int g = 0; g < groupCount; g++) {
            float sx = groupTan1[g * 3];
            float sy = groupTan1[g * 3 + 1];
            float sz = groupTan1[g * 3 + 2];
            float lenS = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (lenS > EPS) {
                float inv = 1f / lenS;
                groupTan1[g * 3]     = sx * inv;
                groupTan1[g * 3 + 1] = sy * inv;
                groupTan1[g * 3 + 2] = sz * inv;
            }

            float tx = groupTan2[g * 3];
            float ty = groupTan2[g * 3 + 1];
            float tz = groupTan2[g * 3 + 2];
            float lenT = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (lenT > EPS) {
                float inv = 1f / lenT;
                groupTan2[g * 3]     = tx * inv;
                groupTan2[g * 3 + 1] = ty * inv;
                groupTan2[g * 3 + 2] = tz * inv;
            }
        }
        // TODO 并行加速
        // ── 5. 输出——逐 corner 从 group 取切线，正交化，手性 ──
        float[] o    = out.getFloatsForWrite();
        int     oOff = (int) out.floatOffset();

        for (int c = 0; c < ncornersI; c++) {
            int g = groupIdOfCorner[c];

            float nx = nrm[nOff + c * 3];
            float ny = nrm[nOff + c * 3 + 1];
            float nz = nrm[nOff + c * 3 + 2];

            float tx = groupTan1[g * 3];
            float ty = groupTan1[g * 3 + 1];
            float tz = groupTan1[g * 3 + 2];

            float dot = nx * tx + ny * ty + nz * tz;
            tx -= nx * dot;
            ty -= ny * dot;
            tz -= nz * dot;

            float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len > EPS) {
                float inv = 1f / len;
                tx *= inv; ty *= inv; tz *= inv;
            } else {
                tx = 1f; ty = 0f; tz = 0f;
            }

            float cx = ny * tz - nz * ty;
            float cy = nz * tx - nx * tz;
            float cz = nx * ty - ny * tx;

            float bx = groupTan2[g * 3];
            float by = groupTan2[g * 3 + 1];
            float bz = groupTan2[g * 3 + 2];

            float hand = (cx * bx + cy * by + cz * bz) < 0f ? -1f : 1f;

            int ob = oOff + c * 4;
            o[ob]     = tx;
            o[ob + 1] = ty;
            o[ob + 2] = tz;
            o[ob + 3] = hand;
        }
    }

    // ── 三角形累加 ──

    private static void accumulateTriangle(int c0, int c1, int c2,
                                           float[] pos, int pOff,
                                           int[] cv, int cvOff,
                                           float[] uv, int uvOff,
                                           int[] groupIdOfCorner,
                                           float[] groupTan1,
                                           float[] groupTan2) {
        int v0 = cv[cvOff + c0];
        int v1 = cv[cvOff + c1];
        int v2 = cv[cvOff + c2];

        int p0 = pOff + v0 * 3;
        int p1 = pOff + v1 * 3;
        int p2 = pOff + v2 * 3;

        float x0 = pos[p0],     y0 = pos[p0 + 1], z0 = pos[p0 + 2];
        float x1 = pos[p1],     y1 = pos[p1 + 1], z1 = pos[p1 + 2];
        float x2 = pos[p2],     y2 = pos[p2 + 1], z2 = pos[p2 + 2];

        float e1x = x1 - x0, e1y = y1 - y0, e1z = z1 - z0;
        float e2x = x2 - x0, e2y = y2 - y0, e2z = z2 - z0;

        int t0 = uvOff + c0 * 2;
        int t1 = uvOff + c1 * 2;
        int t2 = uvOff + c2 * 2;

        float u0  = uv[t0],     vu0 = uv[t0 + 1];
        float u1  = uv[t1],     vu1 = uv[t1 + 1];
        float u2  = uv[t2],     vu2 = uv[t2 + 1];

        float du1 = u1 - u0, dv1 = vu1 - vu0;
        float du2 = u2 - u0, dv2 = vu2 - vu0;

        float r = du1 * dv2 - du2 * dv1;
        if (Math.abs(r) < R_EPS) return;   // 退化三角形——不贡献

        float invR = 1f / r;

        float tx = (dv2 * e1x - dv1 * e2x) * invR;
        float ty = (dv2 * e1y - dv1 * e2y) * invR;
        float tz = (dv2 * e1z - dv1 * e2z) * invR;

        float bx = (du1 * e2x - du2 * e1x) * invR;
        float by = (du1 * e2y - du2 * e1y) * invR;
        float bz = (du1 * e2z - du2 * e1z) * invR;

        int g0 = groupIdOfCorner[c0];
        groupTan1[g0 * 3]     += tx;
        groupTan1[g0 * 3 + 1] += ty;
        groupTan1[g0 * 3 + 2] += tz;
        groupTan2[g0 * 3]     += bx;
        groupTan2[g0 * 3 + 1] += by;
        groupTan2[g0 * 3 + 2] += bz;

        int g1 = groupIdOfCorner[c1];
        groupTan1[g1 * 3]     += tx;
        groupTan1[g1 * 3 + 1] += ty;
        groupTan1[g1 * 3 + 2] += tz;
        groupTan2[g1 * 3]     += bx;
        groupTan2[g1 * 3 + 1] += by;
        groupTan2[g1 * 3 + 2] += bz;

        int g2 = groupIdOfCorner[c2];
        groupTan1[g2 * 3]     += tx;
        groupTan1[g2 * 3 + 1] += ty;
        groupTan1[g2 * 3 + 2] += tz;
        groupTan2[g2 * 3]     += bx;
        groupTan2[g2 * 3 + 1] += by;
        groupTan2[g2 * 3 + 2] += bz;
    }

    // ── 排序用的临时顶点 ──

    private static final class TmpVert implements Comparable<TmpVert> {
        final float px, py, pz;
        final float nx, ny, nz;
        final float u, v;
        final int cornerIndex;

        TmpVert(float px, float py, float pz,
                float nx, float ny, float nz,
                float u, float v,
                int cornerIndex) {
            this.px = px; this.py = py; this.pz = pz;
            this.nx = nx; this.ny = ny; this.nz = nz;
            this.u  = u;  this.v  = v;
            this.cornerIndex = cornerIndex;
        }

        @Override
        public int compareTo(TmpVert o) {
            int c;
            if ((c = Float.compare(px, o.px)) != 0) return c;
            if ((c = Float.compare(py, o.py)) != 0) return c;
            if ((c = Float.compare(pz, o.pz)) != 0) return c;
            if ((c = Float.compare(nx, o.nx)) != 0) return c;
            if ((c = Float.compare(ny, o.ny)) != 0) return c;
            if ((c = Float.compare(nz, o.nz)) != 0) return c;
            if ((c = Float.compare(u, o.u)) != 0) return c;
            return Float.compare(v, o.v);
        }
    }
}