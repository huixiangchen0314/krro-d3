package top.kzre.krro.d3.core.geometry.bvh;

import top.kzre.krro.d3.core.geometry.Axis;
import top.kzre.krro.util.pool.FloatsHolder;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.IntsHolder;
import top.kzre.krro.util.pool.IntsPool;
import top.kzre.krro.util.pool.PoolManagers;

/**
 * SBVH 分区——分桶 SAH + 空间分裂。
 *
 * <p>与 BinnedSelect 的差异：
 * <ul>
 *   <li>输入是 ref 列表（prim-id + AABB）——不是 prims + 原始 AABB</li>
 *   <li>分区时——跨越分割面的 ref 复制到两侧——AABB 裁剪</li>
 *   <li>采用"追加"模式——新 refs 写入 ctx 尾部</li>
 * </ul>
 *
 * <p><b>非线程安全</b>。
 */
public final class SbvhSelect {

    private SbvhSelect() {}

    private static final int K = 12;

    private static final FloatsHolder FLOATS = PoolManagers.floats().getHolder();
    private static final IntsHolder   INTS   = PoolManagers.ints().getHolder();
    private static final FloatsPool POOL_F_K  = FLOATS.getPool(K);
    private static final FloatsPool POOL_F_K1 = FLOATS.getPool(K - 1);
    private static final IntsPool   POOL_I_K  = INTS.getPool(K);
    private static final IntsPool   POOL_I_K1 = INTS.getPool(K - 1);

    /** 分区结果——refs 中的索引范围。 */
    public static final class PartitionResult {
        public int   leftStart;
        public int   leftEnd;
        public int   rightStart;
        public int   rightEnd;
        public int   axis;
        public float splitPos;
    }

    /**
     * 对 refs[start, end) 分区——追加左右两组到 ctx 尾部。
     *
     * @return 分区结果——null 表示无法分区（退化）
     */
    public static PartitionResult partition(SBVHBuildCtx ctx, int start, int end) {
        int n = end - start;
        if (n < 2) return null;

        float[] aabbs = ctx.refAabbs();
        int     axis  = longestAxis(aabbs, start, end);

        // ─── 沿轴范围 ───
        float rangeMin = Float.POSITIVE_INFINITY;
        float rangeMax = Float.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            float lo = aabbs[i * 6 + axis];
            float hi = aabbs[i * 6 + 3 + axis];
            if (lo < rangeMin) rangeMin = lo;
            if (hi > rangeMax) rangeMax = hi;
        }
        float range = rangeMax - rangeMin;
        if (range < 1e-20f) return null;
        float scale = K / range;

        // ─── 池获取 ───
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

        try {
            // ─── 桶初始化 ───
            for (int b = 0; b < K; b++) {
                bMinX[b] = Float.POSITIVE_INFINITY;
                bMinY[b] = Float.POSITIVE_INFINITY;
                bMinZ[b] = Float.POSITIVE_INFINITY;
                bMaxX[b] = Float.NEGATIVE_INFINITY;
                bMaxY[b] = Float.NEGATIVE_INFINITY;
                bMaxZ[b] = Float.NEGATIVE_INFINITY;
                bCnt[b]  = 0;
            }

            // ─── 桶累积 ───
            for (int i = start; i < end; i++) {
                int base = i * 6;
                float c = (aabbs[base + axis] + aabbs[base + 3 + axis]) * 0.5f;
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

            // ─── 前缀累积 ───
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

            // ─── 后缀累积 ───
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

            // ─── SAH 扫描 ───
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
            if (bestSplit < 0) return null;

            float splitPos = rangeMin + bestSplit * range / K;

            // ─── 分区——追加 ───
            return partitionAppend(ctx, start, end, axis, splitPos);

        } finally {
            POOL_F_K.release(bMinX); POOL_F_K.release(bMinY); POOL_F_K.release(bMinZ);
            POOL_F_K.release(bMaxX); POOL_F_K.release(bMaxY); POOL_F_K.release(bMaxZ);
            POOL_I_K.release(bCnt);
            POOL_F_K1.release(lMinX); POOL_F_K1.release(lMinY); POOL_F_K1.release(lMinZ);
            POOL_F_K1.release(lMaxX); POOL_F_K1.release(lMaxY); POOL_F_K1.release(lMaxZ);
            POOL_I_K1.release(lCnt);
            POOL_F_K1.release(rMinX); POOL_F_K1.release(rMinY); POOL_F_K1.release(rMinZ);
            POOL_F_K1.release(rMaxX); POOL_F_K1.release(rMaxY); POOL_F_K1.release(rMaxZ);
            POOL_I_K1.release(rCnt);
        }
    }

    // ═══════════════════════════════════════════════
    // 分区——追加
    // ═══════════════════════════════════════════════

    private static PartitionResult partitionAppend(
            SBVHBuildCtx ctx, int start, int end, int axis, float splitPos) {

        // 第一遍——算 leftCount / rightCount
        float[] aabbs = ctx.refAabbs();
        int[]   prims = ctx.refPrims();

        int leftCount = 0, rightCount = 0;
        for (int i = start; i < end; i++) {
            int base = i * 6;
            float lo = aabbs[base + axis];
            float hi = aabbs[base + 3 + axis];
            if (hi <= splitPos) {
                leftCount++;
            } else if (lo >= splitPos) {
                rightCount++;
            } else {
                leftCount++;
                rightCount++;
            }
        }
        if (leftCount == 0 || rightCount == 0) return null;

        // 追加位置——在 refs 尾部
        int outStart = ctx.refCount();
        int total    = leftCount + rightCount;
        ctx.ensureRefCapacity(outStart + total);

        // 重新获取数组引用——ensureRefCapacity 可能重新分配
        aabbs = ctx.refAabbs();
        prims = ctx.refPrims();

        int leftStart  = outStart;
        int rightStart = outStart + leftCount;
        int lp = leftStart;
        int rp = rightStart;

        for (int i = start; i < end; i++) {
            int base = i * 6;
            float lo = aabbs[base + axis];
            float hi = aabbs[base + 3 + axis];
            int   pid = prims[i];

            if (hi <= splitPos) {
                // 纯左
                prims[lp] = pid;
                System.arraycopy(aabbs, base, aabbs, lp * 6, 6);
                lp++;
            } else if (lo >= splitPos) {
                // 纯右
                prims[rp] = pid;
                System.arraycopy(aabbs, base, aabbs, rp * 6, 6);
                rp++;
            } else {
                // 跨界——复制到两侧——裁剪 AABB
                // 左侧——(lo, splitPos)
                prims[lp] = pid;
                System.arraycopy(aabbs, base, aabbs, lp * 6, 6);
                aabbs[lp * 6 + 3 + axis] = splitPos;
                lp++;

                // 右侧——(splitPos, hi)
                prims[rp] = pid;
                System.arraycopy(aabbs, base, aabbs, rp * 6, 6);
                aabbs[rp * 6 + axis] = splitPos;
                rp++;
            }
        }

        ctx.setRefCount(outStart + total);

        PartitionResult r = new PartitionResult();
        r.leftStart  = leftStart;
        r.leftEnd    = leftStart + leftCount;
        r.rightStart = rightStart;
        r.rightEnd   = rightStart + rightCount;
        r.axis       = axis;
        r.splitPos   = splitPos;
        return r;
    }

    // ═══════════════════════════════════════════════
    // 辅助
    // ═══════════════════════════════════════════════

    private static int longestAxis(float[] aabbs, int start, int end) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            int base = i * 6;
            if (aabbs[base]     < minX) minX = aabbs[base];
            if (aabbs[base + 1] < minY) minY = aabbs[base + 1];
            if (aabbs[base + 2] < minZ) minZ = aabbs[base + 2];
            if (aabbs[base + 3] > maxX) maxX = aabbs[base + 3];
            if (aabbs[base + 4] > maxY) maxY = aabbs[base + 4];
            if (aabbs[base + 5] > maxZ) maxZ = aabbs[base + 5];
        }
        float ex = maxX - minX, ey = maxY - minY, ez = maxZ - minZ;
        if (ex >= ey && ex >= ez) return Axis.X;
        if (ey >= ez)             return Axis.Y;
        return Axis.Z;
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