package top.kzre.krro.d3.core.geometry;

/**
 * Morton 码（Z-order 曲线）计算工具。
 * 用于将 3D 空间坐标编码为 1D 整数，保留空间局部性。
 * 在网格分块中用于空间排序和 LBVH 构建。
 */
public final class Morton {
    private Morton() {}

    // ────────── 64-bit 版本（支持较高精度）──────────

    /** 将 21-bit 的每个分量扩展为 63-bit 的 Morton 码（每个分量占 21 位）。 */
    private static long expand21(long v) {
        v &= 0x1FFFFF; // 21 bits
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

    // 在 Morton.java 中添加
    public static long fromAABBs(AABB trunkAABB, AABB globalAABB) {
        float cx = (trunkAABB.minX + trunkAABB.maxX) * 0.5f;
        float cy = (trunkAABB.minY + trunkAABB.maxY) * 0.5f;
        float cz = (trunkAABB.minZ + trunkAABB.maxZ) * 0.5f;
        float scaleX = globalAABB.maxX - globalAABB.minX;
        float scaleY = globalAABB.maxY - globalAABB.minY;
        float scaleZ = globalAABB.maxZ - globalAABB.minZ;
        float normX = (scaleX == 0f) ? 0f : (cx - globalAABB.minX) / scaleX;
        float normY = (scaleY == 0f) ? 0f : (cy - globalAABB.minY) / scaleY;
        float normZ = (scaleZ == 0f) ? 0f : (cz - globalAABB.minZ) / scaleZ;
        int ix = (int)(normX * 2097151f);
        int iy = (int)(normY * 2097151f);
        int iz = (int)(normZ * 2097151f);
        return morton3D(ix, iy, iz);
    }
}