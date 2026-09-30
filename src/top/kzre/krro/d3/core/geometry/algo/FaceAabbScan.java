package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.Mesh;

/**
 * 面 AABB——逐面扫描顶点位置——求 min/max。
 *
 * <p><b>输出</b>：float6[nfaces]——[minX, minY, minZ, maxX, maxY, maxZ] × nfaces。
 *
 * <p><b>复用</b>：结果长度 = faceCount × 6——允许外部传入。
 */
public final class FaceAabbScan {

    private FaceAabbScan() {}

    public static int length(Mesh mesh) {
        return (int) (mesh.faceCount() * 6);
    }

    public static float[] compute(Mesh mesh) {
        return compute(mesh, new float[length(mesh)]);
    }

    public static float[] compute(Mesh mesh, float[] out) {
        int[]   cornerVerts = mesh.cornerVerts().getIntsSnapshot();
        int[]   faceOffsets = mesh.faceOffsets().getIntsSnapshot();
        float[] positions   = mesh.positions().getFloatsSnapshot();

        int posStart  = (int) (mesh.positions().offset() / Float.BYTES);
        int faceCount = (int) mesh.faceCount();

        for (int f = 0; f < faceCount; f++) {
            int start = faceOffsets[f];
            int end   = faceOffsets[f + 1];

            int base = f * 6;

            if (start >= end) {
                out[base]     = Float.POSITIVE_INFINITY;
                out[base + 1] = Float.POSITIVE_INFINITY;
                out[base + 2] = Float.POSITIVE_INFINITY;
                out[base + 3] = Float.NEGATIVE_INFINITY;
                out[base + 4] = Float.NEGATIVE_INFINITY;
                out[base + 5] = Float.NEGATIVE_INFINITY;
                continue;
            }

            int v0 = cornerVerts[start];
            int b0 = posStart + v0 * 3;

            float mnx = positions[b0];
            float mny = positions[b0 + 1];
            float mnz = positions[b0 + 2];
            float mxx = mnx;
            float mxy = mny;
            float mxz = mnz;

            for (int c = start + 1; c < end; c++) {
                int v = cornerVerts[c];
                int b = posStart + v * 3;

                float x = positions[b];
                float y = positions[b + 1];
                float z = positions[b + 2];

                if (x < mnx) mnx = x;
                if (y < mny) mny = y;
                if (z < mnz) mnz = z;
                if (x > mxx) mxx = x;
                if (y > mxy) mxy = y;
                if (z > mxz) mxz = z;
            }

            out[base]     = mnx;
            out[base + 1] = mny;
            out[base + 2] = mnz;
            out[base + 3] = mxx;
            out[base + 4] = mxy;
            out[base + 5] = mxz;
        }

        return out;
    }
}