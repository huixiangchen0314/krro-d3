package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.util.math.KMath;

/**
 * Morton 码（Z-order 曲线）计算工具。
 * 用于将 3D 空间坐标编码为 1D 整数，保留空间局部性。
 * 在网格分块中用于空间排序和 LBVH 构建。
 *
 * <p><b>接口约定</b>：本类只依赖 {@link IAABB}——不触碰具体实现字段。
 *
 * <p><b>KMath 复用</b>：数值钳制走 {@link KMath#clamp} /
 * {@link KMath#clamp01}——不重复造轮子。
 */
public final class Morton {

    private Morton() {}

    /** 21-bit 每轴——64 位 Morton 码的分辨率上限。 */
    public static final int MAX_21 = 0x1FFFFF;   // 2097151

    /** 10-bit 每轴——32 位 Morton 码的分辨率上限。 */
    public static final int MAX_10 = 0x3FF;      // 1023

    // ────────── 64-bit 版本（支持较高精度）──────────

    /** 将 21-bit 的每个分量扩展为 63-bit 的 Morton 码（每个分量占 21 位）。 */
    private static long expand21(long v) {
        v &= 0x1FFFFFL; // 21 bits
        v = (v | (v << 32)) & 0x1F00000000FFFFL;
        v = (v | (v << 16)) & 0x1F0000FF0000FFL;
        v = (v | (v << 8))  & 0x100F00F00F00F00FL;
        v = (v | (v << 4))  & 0x10C30C30C30C30C3L;
        v = (v | (v << 2))  & 0x1249249249249249L;
        return v;
    }

    /**
     * 计算 3D Morton 码（64 位）。
     * @param x 取值范围 0 ~ 2097151 (21 bits)
     * @param y 取值范围 0 ~ 2097151
     * @param z 取值范围 0 ~ 2097151
     * @return Morton 码（64 位 long）
     */
    public static long morton3D(int x, int y, int z) {
        return expand21(x) | (expand21(y) << 1) | (expand21(z) << 2);
    }

    // ────────── 32-bit 版本（适合较小范围）──────────

    /** 将 10-bit 分量扩展为 30-bit 的 Morton 码（每个分量占 10 位）。 */
    private static int expand10(int v) {
        v &= 0x3FF; // 10 bits
        v = (v | (v << 16)) & 0x030000FF;
        v = (v | (v << 8))  & 0x0300F00F;
        v = (v | (v << 4))  & 0x030C30C3;
        v = (v | (v << 2))  & 0x09249249;
        return v;
    }

    /**
     * 计算 3D Morton 码（32 位）。
     * @param x 取值范围 0 ~ 1023 (10 bits)
     * @param y 取值范围 0 ~ 1023
     * @param z 取值范围 0 ~ 1023
     * @return Morton 码（32 位 int）
     */
    public static int morton3DInt(int x, int y, int z) {
        return expand10(x) | (expand10(y) << 1) | (expand10(z) << 2);
    }

    // ────────── AABB → Morton ──────────

    /**
     * 把 trunk 的 AABB 中心点编码为 Morton 码。
     *
     * <p>委托 {@link #fromCenter}——只是把 AABB 拆成三个中心分量。
     *
     * @param trunkAABB  待编码的块 AABB——只读接口
     * @param globalAABB 全局包围盒——提供归一化尺度——只读接口
     * @return 64 位 Morton 码
     */
    public static long fromAABBs(IAABB trunkAABB, IAABB globalAABB) {
        if (trunkAABB == null || trunkAABB.isEmpty()) {
            return 0L;
        }
        return fromCenter(trunkAABB.centerX(),
                trunkAABB.centerY(),
                trunkAABB.centerZ(),
                globalAABB);
    }

    /**
     * 把中心点编码为 Morton 码。
     *
     * <p>工作流程：
     * <ol>
     *   <li>用 {@code globalAABB} 把中心点归一化到 [0, 1]</li>
     *   <li>缩放为 [0, {@link #MAX_21}] 的整数</li>
     *   <li>三轴整数交错得到 64 位 Morton 码</li>
     * </ol>
     *
     * <p><b>边界</b>：
     * <ul>
     *   <li>{@code globalAABB == null} 或空盒——退化为"直接用中心点的整数坐标"</li>
     *   <li>某轴长度为 0——该轴归一化取 0</li>
     *   <li>归一化越界——{@link KMath#clamp01} 钳制——防止 (int) 截断溢出</li>
     * </ul>
     *
     * @param cx         中心点 X
     * @param cy         中心点 Y
     * @param cz         中心点 Z
     * @param globalAABB 全局包围盒——可为 null
     * @return 64 位 Morton 码
     */
    public static long fromCenter(float cx, float cy, float cz, IAABB globalAABB) {
        // 全局包围盒缺失或空——退化为不归一化——直接量化
        if (globalAABB == null || globalAABB.isEmpty()) {
            return morton3D(quantize(cx), quantize(cy), quantize(cz));
        }

        return morton3D(
                quantizeNormalized(cx, globalAABB.getMinX(), globalAABB.getMaxX()),
                quantizeNormalized(cy, globalAABB.getMinY(), globalAABB.getMaxY()),
                quantizeNormalized(cz, globalAABB.getMinZ(), globalAABB.getMaxZ()));
    }

    // ────────── 内部工具 ──────────

    /**
     * 直接把坐标量化到 [0, MAX_21]。
     * 复用 {@link KMath#clamp}——MAX_21 小于 2^24——float 精确表示——转 int 无损。
     */
    private static int quantize(float v) {
        return (int) KMath.clamp(v, 0f, (float) MAX_21);
    }

    /**
     * 归一化到 [0, 1] 再量化到 [0, MAX_21]。
     *
     * <p>{@code scale <= 0} 时该轴归 0——避免除零。
     * 归一化结果走 {@link KMath#clamp01}——浮点误差可能微弱越界。
     */
    private static int quantizeNormalized(float v, float lo, float hi) {
        float scale = hi - lo;
        if (scale <= 0f) {
            return 0;
        }
        float norm = KMath.clamp01((v - lo) / scale);
        return (int) (norm * MAX_21);
    }
}