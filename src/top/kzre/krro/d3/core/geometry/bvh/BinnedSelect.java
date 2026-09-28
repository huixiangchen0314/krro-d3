package top.kzre.krro.d3.core.geometry.bvh;

import top.kzre.krro.d3.core.geometry.Axis;

/**
 * Binned BVH 构建辅助——最长轴分桶 + SAH 扫描。
 *
 * <p>在指定轴上创建 K 个桶，累积每个桶的 AABB 与数量，
 * 扫描所有分割位置取 SAH 成本最小者——就地重排 prims。
 *
 * <p><b>布局耦合</b>：假定 AABB 数组为 AabbArray 布局（stride 6）。
 *
 * <p><b>非线程安全</b>：仅限单线程构建使用。
 */
public final class BinnedSelect {

    private BinnedSelect() {}

    private static final int AABB_STRIDE = 6;
    private static final int K = 12;

    /**
     * 计算最长轴——0 = X，1 = Y，2 = Z。
     */
    public static int longestAxis(float[] aabbs, long[] prims, int start, int end) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        for (int i = start; i < end; i++) {
            int base = (int) (prims[i] * AABB_STRIDE);
            if (aabbs[base]     < minX) minX = aabbs[base];
            if (aabbs[base + 1] < minY) minY = aabbs[base + 1];
            if (aabbs[base + 2] < minZ) minZ = aabbs[base + 2];
            if (aabbs[base + 3] > maxX) maxX = aabbs[base + 3];
            if (aabbs[base + 4] > maxY) maxY = aabbs[base + 4];
            if (aabbs[base + 5] > maxZ) maxZ = aabbs[base + 5];
        }

        float ex = maxX - minX;
        float ey = maxY - minY;
        float ez = maxZ - minZ;

        if (ex >= ey && ex >= ez) return Axis.X;
        if (ey >= ez)             return Axis.Y;
        return Axis.Z;
    }

    /**
     * 分桶 SAH 分区。
     *
     * @param prims 图元 id 数组
     * @param start 起始索引（含）
     * @param end   结束索引（不含）
     * @param aabbs AABB 数组——AabbArray 布局
     * @param axis  最长轴 0 = X，1 = Y，2 = Z
     * @return split——左分区结束位置（右分区从 split 开始）
     *         无法分割时返回 -1
     */
    public static int binnedPartition(long[] prims, int start, int end,
                                      float[] aabbs, int axis) {
        int n = end - start;
        if (n < 2) return -1;

        // 沿轴的范围
        float rangeMin = Float.POSITIVE_INFINITY;
        float rangeMax = Float.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            int base = (int) (prims[i] * AABB_STRIDE);
            float lo = aabbs[base + axis];
            float hi = aabbs[base + axis + 3];
            if (lo < rangeMin) rangeMin = lo;
            if (hi > rangeMax) rangeMax = hi;
        }
        float range = rangeMax - rangeMin;
        if (range < 1e-20f) return -1;
        float scale = K / range;

        // 桶累积
        float[] bMinX = new float[K], bMinY = new float[K], bMinZ = new float[K];
        float[] bMaxX = new float[K], bMaxY = new float[K], bMaxZ = new float[K];
        int[]   bCnt  = new int[K];
        for (int b = 0; b < K; b++) {
            bMinX[b] = Float.POSITIVE_INFINITY;
            bMinY[b] = Float.POSITIVE_INFINITY;
            bMinZ[b] = Float.POSITIVE_INFINITY;
            bMaxX[b] = Float.NEGATIVE_INFINITY;
            bMaxY[b] = Float.NEGATIVE_INFINITY;
            bMaxZ[b] = Float.NEGATIVE_INFINITY;
        }
        for (int i = start; i < end; i++) {
            int base = (int) (prims[i] * AABB_STRIDE);
            float c = (aabbs[base + axis] + aabbs[base + axis + 3]) * 0.5f;
            int b = (int) ((c - rangeMin) * scale);
            if (b < 0) b = 0;
            else if (b >= K) b = K - 1;

            if (aabbs[base]     < bMinX[b]) bMinX[b] = aabbs[base];
            if (aabbs[base + 1] < bMinY[b]) bMinY[b] = aabbs[base + 1];
            if (aabbs[base + 2] < bMinZ[b]) bMinZ[b] = aabbs[base + 2];
            if (aabbs[base + 3] > bMaxX[b]) bMaxX[b] = aabbs[base + 3];
            if (aabbs[base + 4] > bMaxY[b]) bMaxY[b] = aabbs[base + 4];
            if (aabbs[base + 5] > bMaxZ[b]) bMaxZ[b] = aabbs[base + 5];
            bCnt[b]++;
        }

        // 从左到右累积
        float[] lMinX = new float[K-1], lMinY = new float[K-1], lMinZ = new float[K-1];
        float[] lMaxX = new float[K-1], lMaxY = new float[K-1], lMaxZ = new float[K-1];
        int[]   lCnt  = new int[K-1];
        float aMinX = Float.POSITIVE_INFINITY, aMinY = Float.POSITIVE_INFINITY, aMinZ = Float.POSITIVE_INFINITY;
        float aMaxX = Float.NEGATIVE_INFINITY, aMaxY = Float.NEGATIVE_INFINITY, aMaxZ = Float.NEGATIVE_INFINITY;
        int   aCnt  = 0;
        for (int k = 0; k < K - 1; k++) {
            if (bCnt[k] > 0) {
                if (bMinX[k] < aMinX) aMinX = bMinX[k];
                if (bMinY[k] < aMinY) aMinY = bMinY[k];
                if (bMinZ[k] < aMinZ) aMinZ = bMinZ[k];
                if (bMaxX[k] > aMaxX) aMaxX = bMaxX[k];
                if (bMaxY[k] > aMaxY) aMaxY = bMaxY[k];
                if (bMaxZ[k] > aMaxZ) aMaxZ = bMaxZ[k];
                aCnt += bCnt[k];
            }
            lMinX[k] = aMinX; lMinY[k] = aMinY; lMinZ[k] = aMinZ;
            lMaxX[k] = aMaxX; lMaxY[k] = aMaxY; lMaxZ[k] = aMaxZ;
            lCnt[k]  = aCnt;
        }

        // 从右到左累积
        float[] rMinX = new float[K-1], rMinY = new float[K-1], rMinZ = new float[K-1];
        float[] rMaxX = new float[K-1], rMaxY = new float[K-1], rMaxZ = new float[K-1];
        int[]   rCnt  = new int[K-1];
        aMinX = Float.POSITIVE_INFINITY; aMinY = Float.POSITIVE_INFINITY; aMinZ = Float.POSITIVE_INFINITY;
        aMaxX = Float.NEGATIVE_INFINITY; aMaxY = Float.NEGATIVE_INFINITY; aMaxZ = Float.NEGATIVE_INFINITY;
        aCnt  = 0;
        for (int k = K - 1; k >= 1; k--) {
            if (bCnt[k] > 0) {
                if (bMinX[k] < aMinX) aMinX = bMinX[k];
                if (bMinY[k] < aMinY) aMinY = bMinY[k];
                if (bMinZ[k] < aMinZ) aMinZ = bMinZ[k];
                if (bMaxX[k] > aMaxX) aMaxX = bMaxX[k];
                if (bMaxY[k] > aMaxY) aMaxY = bMaxY[k];
                if (bMaxZ[k] > aMaxZ) aMaxZ = bMaxZ[k];
                aCnt += bCnt[k];
            }
            int idx = k - 1;
            rMinX[idx] = aMinX; rMinY[idx] = aMinY; rMinZ[idx] = aMinZ;
            rMaxX[idx] = aMaxX; rMaxY[idx] = aMaxY; rMaxZ[idx] = aMaxZ;
            rCnt[idx]  = aCnt;
        }

        // SAH 扫描
        float bestCost = Float.POSITIVE_INFINITY;
        int   bestSplit = -1;
        for (int k = 0; k < K - 1; k++) {
            if (lCnt[k] == 0 || rCnt[k] == 0) continue;
            float saL = surfaceArea(lMinX[k], lMinY[k], lMinZ[k], lMaxX[k], lMaxY[k], lMaxZ[k]);
            float saR = surfaceArea(rMinX[k], rMinY[k], rMinZ[k], rMaxX[k], rMaxY[k], rMaxZ[k]);
            float cost = saL * lCnt[k] + saR * rCnt[k];
            if (cost < bestCost) {
                bestCost  = cost;
                bestSplit = k + 1;
            }
        }
        if (bestSplit < 0) return -1;

        // 按桶顺序稳定重排 prims
        int[] binStart = new int[K];
        int acc = start;
        for (int b = 0; b < K; b++) {
            binStart[b] = acc;
            acc += bCnt[b];
        }
        long[] tmp = new long[n];
        for (int i = start; i < end; i++) {
            int base = (int) (prims[i] * AABB_STRIDE);
            float c = (aabbs[base + axis] + aabbs[base + axis + 3]) * 0.5f;
            int b = (int) ((c - rangeMin) * scale);
            if (b < 0) b = 0;
            else if (b >= K) b = K - 1;
            tmp[binStart[b] - start] = prims[i];
            binStart[b]++;
        }
        System.arraycopy(tmp, 0, prims, start, n);

        // 计算 split
        int split = start;
        for (int b = 0; b < bestSplit; b++) split += bCnt[b];
        return split;
    }

    private static float surfaceArea(float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ) {
        if (minX > maxX) return 0f;
        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;
        return 2f * (dx * dy + dy * dz + dz * dx);
    }
}