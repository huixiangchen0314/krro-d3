package top.kzre.krro.d3.core.geometry.bvh;

import top.kzre.krro.d3.core.geometry.Axis;
import top.kzre.krro.util.pool.FloatsHolder;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.IntsHolder;
import top.kzre.krro.util.pool.IntsPool;
import top.kzre.krro.util.pool.PoolManagers;

/**
 * Binned BVH 构建辅助——最长轴分桶 + SAH 扫描。
 *
 * <p>在指定轴上创建 K 个桶，累积每个桶的 AABB 与数量，
 * 扫描所有分割位置取 SAH 成本最小者——就地重排 prims。
 *
 * <p><b>布局耦合</b>：假定 AABB 数组为 AabbArray 布局（stride 6）。
 *
 * <p><b>池分配</b>：固定大小的临时数组走全局 {@link FloatsPool} /
 * {@link IntsPool}——稳态零分配。
 *
 * <p><b>非线程安全</b>：仅限单线程构建使用。
 */
public final class BinnedSelect {

    private BinnedSelect() {}

    private static final int AABB_STRIDE = 6;
    private static final int K = 12;

    // ─── 池——按长度缓存 ─────────────────────────────

    private static final FloatsHolder FLOATS = PoolManagers.floats().getHolder();
    private static final IntsHolder   INTS   = PoolManagers.ints().getHolder();

    private static final FloatsPool POOL_F_K  = FLOATS.getPool(K);
    private static final FloatsPool POOL_F_K1 = FLOATS.getPool(K - 1);
    private static final IntsPool   POOL_I_K  = INTS.getPool(K);
    private static final IntsPool   POOL_I_K1 = INTS.getPool(K - 1);

    // ═══════════════════════════════════════════════
    // 最长轴
    // ═══════════════════════════════════════════════

    /**
     * 计算最长轴——返回 {@link Axis#X} / {@link Axis#Y} / {@link Axis#Z}。
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

    // ═══════════════════════════════════════════════
    // Binned SAH 分区
    // ═══════════════════════════════════════════════

    /**
     * 分桶 SAH 分区。
     *
     * @param prims 图元 id 数组
     * @param start 起始索引（含）
     * @param end   结束索引（不含）
     * @param aabbs AABB 数组——AabbArray 布局
     * @param axis  最长轴
     * @return split——左分区结束位置（右分区从 split 开始）
     *         无法分割时返回 -1
     */
    public static int binnedPartition(long[] prims, int start, int end,
                                      float[] aabbs, int axis) {
        int n = end - start;
        if (n < 2) return -1;

        // ─── 沿轴的范围 ──────────────────────────────
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

        // ─── 从池获取临时数组 ────────────────────────
        float[] bMinX = POOL_F_K.acquire();
        float[] bMinY = POOL_F_K.acquire();
        float[] bMinZ = POOL_F_K.acquire();
        float[] bMaxX = POOL_F_K.acquire();
        float[] bMaxY = POOL_F_K.acquire();
        float[] bMaxZ = POOL_F_K.acquire();
        int[]   bCnt  = POOL_I_K.acquire();

        float[] lMinX = POOL_F_K1.acquire();
        float[] lMinY = POOL_F_K1.acquire();
        float[] lMinZ = POOL_F_K1.acquire();
        float[] lMaxX = POOL_F_K1.acquire();
        float[] lMaxY = POOL_F_K1.acquire();
        float[] lMaxZ = POOL_F_K1.acquire();
        int[]   lCnt  = POOL_I_K1.acquire();

        float[] rMinX = POOL_F_K1.acquire();
        float[] rMinY = POOL_F_K1.acquire();
        float[] rMinZ = POOL_F_K1.acquire();
        float[] rMaxX = POOL_F_K1.acquire();
        float[] rMaxY = POOL_F_K1.acquire();
        float[] rMaxZ = POOL_F_K1.acquire();
        int[]   rCnt  = POOL_I_K1.acquire();

        int[]   binStart = POOL_I_K.acquire();

        try {
            // ─── 桶累积——初始化 ─────────────────────
            for (int b = 0; b < K; b++) {
                bMinX[b] = Float.POSITIVE_INFINITY;
                bMinY[b] = Float.POSITIVE_INFINITY;
                bMinZ[b] = Float.POSITIVE_INFINITY;
                bMaxX[b] = Float.NEGATIVE_INFINITY;
                bMaxY[b] = Float.NEGATIVE_INFINITY;
                bMaxZ[b] = Float.NEGATIVE_INFINITY;
                bCnt[b]  = 0;                                  // ← 池化——必须显式清零
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

            // ─── 从左到右累积 ────────────────────────
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

            // ─── 从右到左累积 ────────────────────────
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

            // ─── SAH 扫描 ─────────────────────────────
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

            // ─── 按桶顺序稳定重排 prims ──────────────
            int acc = start;
            for (int b = 0; b < K; b++) {
                binStart[b] = acc;
                acc += bCnt[b];
            }
            long[] tmp = new long[n];                          // ← 长度可变——不池化
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

            // ─── 计算 split ───────────────────────────
            int split = start;
            for (int b = 0; b < bestSplit; b++) split += bCnt[b];
            return split;

        } finally {
            // ─── 归还池 ───────────────────────────────
            POOL_F_K.release(bMinX);
            POOL_F_K.release(bMinY);
            POOL_F_K.release(bMinZ);
            POOL_F_K.release(bMaxX);
            POOL_F_K.release(bMaxY);
            POOL_F_K.release(bMaxZ);
            POOL_I_K.release(bCnt);

            POOL_F_K1.release(lMinX);
            POOL_F_K1.release(lMinY);
            POOL_F_K1.release(lMinZ);
            POOL_F_K1.release(lMaxX);
            POOL_F_K1.release(lMaxY);
            POOL_F_K1.release(lMaxZ);
            POOL_I_K1.release(lCnt);

            POOL_F_K1.release(rMinX);
            POOL_F_K1.release(rMinY);
            POOL_F_K1.release(rMinZ);
            POOL_F_K1.release(rMaxX);
            POOL_F_K1.release(rMaxY);
            POOL_F_K1.release(rMaxZ);
            POOL_I_K1.release(rCnt);

            POOL_I_K.release(binStart);
        }
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