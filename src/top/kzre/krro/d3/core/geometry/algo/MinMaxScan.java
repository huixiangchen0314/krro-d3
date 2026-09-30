package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.Mesh;

/**
 * MinMax 扫描——遍历顶点位置——取最小 / 最大。
 *
 * <p><b>输出</b>：float[6]——[minX, minY, minZ, maxX, maxY, maxZ]。
 *
 * <p><b>复用</b>：输出长度固定 6——允许外部传入数组。
 */
public final class MinMaxScan {

    private MinMaxScan() {}

    /** 结果长度。 */
    public static final int LENGTH = 6;

    public static float[] compute(Mesh mesh) {
        return compute(mesh, new float[LENGTH]);
    }

    public static float[] compute(Mesh mesh, float[] out) {
        float[] positions = mesh.positions().getFloatsSnapshot();
        int     startIdx  = (int) (mesh.positions().offset() / Float.BYTES);
        long    vertCount = mesh.vertCount();

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (long v = 0; v < vertCount; v++) {
            int base = startIdx + (int) (v * 3);
            float x = positions[base];
            float y = positions[base + 1];
            float z = positions[base + 2];

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        out[0] = minX;
        out[1] = minY;
        out[2] = minZ;
        out[3] = maxX;
        out[4] = maxY;
        out[5] = maxZ;
        return out;
    }
}