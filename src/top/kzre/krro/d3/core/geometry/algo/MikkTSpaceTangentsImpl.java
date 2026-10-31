package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * 切线——简化 MikkTSpace（按顶点累加——已知接缝近似）。
 *
 * <p><b>输出</b>：{@code float4[ncorners]}——xyz + 手性 w，直接写 {@code out}
 * 的底层数组。
 *
 * <p><b>算法</b>：
 * <ol>
 *   <li>逐三角形累加 {@code tan1/tan2}——按 vertex 粒度</li>
 *   <li>逐 corner 正交化：{@code T = T - N·(N·T)}，归一化</li>
 *   <li>手性 {@code w = sign(cross(N, T) · tan2)}</li>
 * </ol>
 *
 * <p><b>已知简化</b>：当前按 vertex 累加，近似了焊接但遗漏了 UV 接缝处的
 * 焊接组区分——同一 vertex 的两个 corner 如果 UV 不同，标准 MikkTSpace
 * 应视为两个焊接组。后续 {@code WeldedMikkTangentsImpl} 按
 * {@code hash(position, normal, uv)} 分组累加。接口不变。
 *
 * <p><b>写入方式</b>：按 face 遍历写 corner，写入顺序是 corner 顺序——
 * 用游标 {@code w} 顺序写 {@code o[w++]}。退化面（{@code n < 3}）的 corner
 * 写默认切线 {@code (1, 0, 0, 1)}，保持输出长度 = {@code ncorners * 4}。
 *
 * <p><b>线程契约</b>：只读输入，写 {@code out}。内部临时数组每调用分配
 * （{@code tan1/tan2} 大小 {@code vertCount * 3}，由 ThreadLocal 增长
 * 或调用方缓存管理——当前为 {@code new}，后续优化）。
 */
public final class MikkTSpaceTangentsImpl implements TangentAlgo {

    public MikkTSpaceTangentsImpl() {}

    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteFloats uv0,
                        CopyOnWriteFloats cornerNormals,
                        CopyOnWriteFloats out) {
        if (positions == null)     throw new NullPointerException("positions");
        if (cornerVerts == null)   throw new NullPointerException("cornerVerts");
        if (faceOffsets == null)   throw new NullPointerException("faceOffsets");
        if (uv0 == null)           throw new NullPointerException("uv0");
        if (cornerNormals == null) throw new NullPointerException("cornerNormals");
        if (out == null)           throw new NullPointerException("out");

        long posCount = positions.count();
        if (posCount % 3 != 0) {
            throw new IllegalArgumentException(
                    "positions count must be multiple of 3, got " + posCount);
        }
        long uvCount = uv0.count();
        if (uvCount % 2 != 0) {
            throw new IllegalArgumentException(
                    "uv0 count must be multiple of 2, got " + uvCount);
        }
        long normCount = cornerNormals.count();
        if (normCount % 3 != 0) {
            throw new IllegalArgumentException(
                    "cornerNormals count must be multiple of 3, got " + normCount);
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
        int[]   fo    = faceOffsets.getIntsSnapshot();
        int     foOff = (int) faceOffsets.intOffset();
        float[] uv    = uv0.getFloatsSnapshot();
        int     uvOff = (int) uv0.floatOffset();
        float[] nrm   = cornerNormals.getFloatsSnapshot();
        int     nOff  = (int) cornerNormals.floatOffset();

        int ncornersI = (int) ncorners;
        int faceCount = (int) faceOffsets.count() - 1;
        int vertCount = (int) (posCount / 3);

        // ── 临时累加器——tan1/tan2 按 vertex 粒度 ──
        float[] tan1 = new float[vertCount * 3];
        float[] tan2 = new float[vertCount * 3];

        // ── 1. 第一遍——逐三角形累加 ──
        for (int f = 0; f < faceCount; f++) {
            int start = fo[foOff + f];
            int n     = fo[foOff + f + 1] - start;
            if (n < 3) continue;

            for (int i = 1; i < n - 1; i++) {
                int c0 = start;
                int c1 = start + i;
                int c2 = start + i + 1;

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
                if (Math.abs(r) < 1e-10f) continue;

                float invR = 1f / r;

                float tx = (dv2 * e1x - dv1 * e2x) * invR;
                float ty = (dv2 * e1y - dv1 * e2y) * invR;
                float tz = (dv2 * e1z - dv1 * e2z) * invR;

                float bx = (du1 * e2x - du2 * e1x) * invR;
                float by = (du1 * e2y - du2 * e1y) * invR;
                float bz = (du1 * e2z - du2 * e1z) * invR;

                int b0 = v0 * 3;
                tan1[b0]     += tx; tan1[b0 + 1] += ty; tan1[b0 + 2] += tz;
                tan2[b0]     += bx; tan2[b0 + 1] += by; tan2[b0 + 2] += bz;

                int b1 = v1 * 3;
                tan1[b1]     += tx; tan1[b1 + 1] += ty; tan1[b1 + 2] += tz;
                tan2[b1]     += bx; tan2[b1 + 1] += by; tan2[b1 + 2] += bz;

                int b2 = v2 * 3;
                tan1[b2]     += tx; tan1[b2 + 1] += ty; tan1[b2 + 2] += tz;
                tan2[b2]     += bx; tan2[b2 + 1] += by; tan2[b2 + 2] += bz;
            }
        }

        // ── 2. 第二遍——逐 corner 正交化，游标写 ──
        float[] o    = out.getFloatsForWrite();
        int     oOff = (int) out.floatOffset();

        int w = oOff;
        for (int f = 0; f < faceCount; f++) {
            int start = fo[foOff + f];
            int n     = fo[foOff + f + 1] - start;

            if (n < 3) {
                // 退化面——默认切线，保持输出长度
                for (int i = 0; i < n; i++) {
                    o[w++] = 1f;
                    o[w++] = 0f;
                    o[w++] = 0f;
                    o[w++] = 1f;
                }
                continue;
            }

            for (int i = 0; i < n; i++) {
                int c = start + i;
                int v = cv[cvOff + c];

                int vb = v * 3;
                int nb = nOff + c * 3;

                float nx = nrm[nb];
                float ny = nrm[nb + 1];
                float nz = nrm[nb + 2];

                float tx = tan1[vb];
                float ty = tan1[vb + 1];
                float tz = tan1[vb + 2];

                float dot = nx * tx + ny * ty + nz * tz;
                tx -= nx * dot;
                ty -= ny * dot;
                tz -= nz * dot;

                float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (len > 1e-8f) {
                    float inv = 1f / len;
                    tx *= inv; ty *= inv; tz *= inv;
                } else {
                    tx = 1f; ty = 0f; tz = 0f;
                }

                float cx = ny * tz - nz * ty;
                float cy = nz * tx - nx * tz;
                float cz = nx * ty - ny * tx;

                float bx = tan2[vb];
                float by = tan2[vb + 1];
                float bz = tan2[vb + 2];

                float hand = (cx * bx + cy * by + cz * bz) < 0f ? -1f : 1f;

                o[w++] = tx;
                o[w++] = ty;
                o[w++] = tz;
                o[w++] = hand;
            }
        }

        long written = (long) w - oOff;
        if (written != out.count()) {
            throw new IllegalStateException(
                    "tangents count mismatch: expected " + out.count()
                            + ", wrote " + written);
        }
    }
}