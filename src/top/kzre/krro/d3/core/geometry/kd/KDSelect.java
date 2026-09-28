package top.kzre.krro.d3.core.geometry.kd;

/**
 * KD 构建辅助——按轴中心做中位数分区。
 *
 * <p>原地 quickselect——平均 O(n)——不需要完全排序。
 * 分区后 [start, mid) 的中心 <= [mid, end) 的中心。
 *
 * <p><b>布局无关</b>：stride / loOffset / hiOffset 由调用方传入——
 * 本类不知道 AABB 的具体布局。
 */
public final class KDSelect {

    private KDSelect() {}

    /** AabbArray 布局——每图元 6 float。 */
    private static final int AABB_STRIDE = 6;

    /** max 分量相对 min 分量的偏移——max-x/y/z = min-x/y/z + 3。 */
    private static final int AABB_MAX_OFFSET = 3;

    // ═══════════════════════════════════════════════
    // 便捷入口——AabbArray 布局
    // ═══════════════════════════════════════════════

    /**
     * AabbArray 布局的中位数分区。
     *
     * <p>轴偏移内化：axis = 0/1/2 → min 偏移 = axis、max 偏移 = axis + 3。
     *
     * @param prims 图元 id 数组
     * @param start 起始索引（含）
     * @param end   结束索引（不含）
     * @param aabbs AABB 数组——AabbArray 布局——每图元 6 float
     * @param axis  分割轴 0 = X，1 = Y，2 = Z
     * @return mid——分区点
     */
    public static int medianPartitionAabb(long[] prims, int start, int end,
                                          float[] aabbs, int axis) {
        return medianPartition(prims, start, end, aabbs,
                AABB_STRIDE,
                axis,
                axis + AABB_MAX_OFFSET);
    }
    /**
     * 对 prims[start, end) 按图元在指定轴上的中心做中位数分区。
     *
     * <p>分区后：
     * <ul>
     *   <li>[start, mid) 内所有图元的中心 <= [mid, end) 内所有图元的中心</li>
     *   <li>mid = start + (end - start) / 2</li>
     * </ul>
     *
     * @param prims     图元 id 数组
     * @param start     起始索引（含）
     * @param end       结束索引（不含）
     * @param aabbs     AABB 数据数组
     * @param stride    每图元占用 float 数（AabbArray 布局为 6）
     * @param loOffset  min 分量在 stride 内的偏移（轴 0/1/2 对应 0/1/2）
     * @param hiOffset  max 分量在 stride 内的偏移（轴 0/1/2 对应 3/4/5）
     * @return mid——分区点
     */
    public static int medianPartition(long[] prims, int start, int end,
                                      float[] aabbs, int stride,
                                      int loOffset, int hiOffset) {
        int mid = start + ((end - start) >>> 1);
        int lo  = start;
        int hi  = end - 1;

        while (lo < hi) {
            float pivot = centerOf(aabbs, prims[mid], stride, loOffset, hiOffset);
            int i = lo;
            int j = hi;
            while (i <= j) {
                while (centerOf(aabbs, prims[i], stride, loOffset, hiOffset) < pivot) i++;
                while (centerOf(aabbs, prims[j], stride, loOffset, hiOffset) > pivot) j--;
                if (i <= j) {
                    long tmp = prims[i];
                    prims[i] = prims[j];
                    prims[j] = tmp;
                    i++;
                    j--;
                }
            }
            if (mid <= j) {
                hi = j;
            } else if (mid >= i) {
                lo = i;
            } else {
                break;
            }
        }
        return mid;
    }

    private static float centerOf(float[] aabbs, long prim,
                                  int stride, int loOffset, int hiOffset) {
        int base = (int) (prim * stride);
        return (aabbs[base + loOffset] + aabbs[base + hiOffset]) * 0.5f;
    }
}