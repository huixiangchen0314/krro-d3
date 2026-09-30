package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.Mesh;

/**
 * 切线——简化 MikkTSpace（按顶点累加——已知接缝近似）。
 *
 * <p><b>输出</b>：float4[ncorners]——xyz + 手性 w。
 *
 * <p><b>复用</b>：结果长度 = cornerCount × 4——允许外部传入。
 */
public final class MikkTSpaceTangents {

    private MikkTSpaceTangents() {}

    public static int length(Mesh mesh) {
        return (int) (mesh.cornerCount() * 4);
    }

    public static float[] compute(Mesh mesh, float[] cornerNormals) {
        return compute(mesh, cornerNormals, new float[length(mesh)]);
    }

    public static float[] compute(Mesh mesh, float[] cornerNormals, float[] out) {
        int[]   cornerVerts = mesh.cornerVerts().getIntsSnapshot();
        int[]   faceOffsets = mesh.faceOffsets().getIntsSnapshot();
        float[] positions   = mesh.positions().getFloatsSnapshot();
        float[] uvs         = mesh.cornerUvs().getFloatsSnapshot();

        int posStart = (int) (mesh.positions().offset() / Float.BYTES);
        int uvStart  = (int) (mesh.cornerUvs().offset()  / Float.BYTES);

        int vertCount   = (int) mesh.vertCount();
        int faceCount   = (int) mesh.faceCount();
        int cornerCount = (int) mesh.cornerCount();

        if (cornerCount == 0) return out;

        float[] tan1 = new float[vertCount * 3];
        float[] tan2 = new float[vertCount * 3];

        // ── 第一遍——逐三角形累加 ──
        for (int f = 0; f < faceCount; f++) {
            int start = faceOffsets[f];
            int end   = faceOffsets[f + 1];
            int n     = end - start;

            if (n < 3) continue;

            for (int i = 1; i < n - 1; i++) {
                int c0 = start;
                int c1 = start + i;
                int c2 = start + i + 1;

                int v0 = cornerVerts[c0];
                int v1 = cornerVerts[c1];
                int v2 = cornerVerts[c2];

                int p0 = posStart + v0 * 3;
                int p1 = posStart + v1 * 3;
                int p2 = posStart + v2 * 3;

                float x0 = positions[p0],     y0 = positions[p0 + 1], z0 = positions[p0 + 2];
                float x1 = positions[p1],     y1 = positions[p1 + 1], z1 = positions[p1 + 2];
                float x2 = positions[p2],     y2 = positions[p2 + 1], z2 = positions[p2 + 2];

                float e1x = x1 - x0, e1y = y1 - y0, e1z = z1 - z0;
                float e2x = x2 - x0, e2y = y2 - y0, e2z = z2 - z0;

                int t0 = uvStart + c0 * 2;
                int t1 = uvStart + c1 * 2;
                int t2 = uvStart + c2 * 2;

                float u0  = uvs[t0],     v0u = uvs[t0 + 1];
                float u1  = uvs[t1],     v1u = uvs[t1 + 1];
                float u2  = uvs[t2],     v2u = uvs[t2 + 1];

                float du1 = u1 - u0, dv1 = v1u - v0u;
                float du2 = u2 - u0, dv2 = v2u - v0u;

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

        // ── 第二遍——逐面角正交化 ──
        for (int f = 0; f < faceCount; f++) {
            int start = faceOffsets[f];
            int end   = faceOffsets[f + 1];
            int n     = end - start;

            if (n < 3) continue;

            for (int i = 0; i < n; i++) {
                int c = start + i;
                int v = cornerVerts[c];

                int vb = v * 3;
                int cb = c * 4;

                float nx = cornerNormals[cb];
                float ny = cornerNormals[cb + 1];
                float nz = cornerNormals[cb + 2];

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

                float w = (cx * bx + cy * by + cz * bz) < 0f ? -1f : 1f;

                out[cb]     = tx;
                out[cb + 1] = ty;
                out[cb + 2] = tz;
                out[cb + 3] = w;
            }
        }

        return out;
    }
}