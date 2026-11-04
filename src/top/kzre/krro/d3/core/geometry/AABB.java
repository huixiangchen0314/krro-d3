package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.pool.FloatsHolder;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.PoolManagers;

/**
 * 可变的轴对齐包围盒。
 *
 * <p>用于构建、累积、编辑。字段私有 + setter——遵循 JavaBean 约定。
 *
 * <p><b>空盒约定</b>：{@code min > max} 视为空盒——
 * {@link #isEmpty()} 返回 {@code true}——{@link #union} 会忽略空盒。
 */
public final class AABB implements IAABB {

    private float minX;
    private float minY;
    private float minZ;
    private float maxX;
    private float maxY;
    private float maxZ;

    /** 默认构造——空盒（min = +∞，max = -∞ 语义由 {@link #isEmpty()} 判定）。 */
    public AABB() {
        this.minX = Float.POSITIVE_INFINITY;
        this.minY = Float.POSITIVE_INFINITY;
        this.minZ = Float.POSITIVE_INFINITY;
        this.maxX = Float.NEGATIVE_INFINITY;
        this.maxY = Float.NEGATIVE_INFINITY;
        this.maxZ = Float.NEGATIVE_INFINITY;
    }

    public AABB(float minX, float minY, float minZ,
                float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public AABB(IAABB a) {
        this.minX = a.getMinX();
        this.minY = a.getMinY();
        this.minZ = a.getMinZ();
        this.maxX = a.getMaxX();
        this.maxY = a.getMaxY();
        this.maxZ = a.getMaxZ();
    }

    // ── getter ─────────────────────────────────

    @Override public float getMinX() { return minX; }
    @Override public float getMinY() { return minY; }
    @Override public float getMinZ() { return minZ; }
    @Override public float getMaxX() { return maxX; }
    @Override public float getMaxY() { return maxY; }
    @Override public float getMaxZ() { return maxZ; }

    // ── setter ─────────────────────────────────

    public void setMinX(float v) { this.minX = v; }
    public void setMinY(float v) { this.minY = v; }
    public void setMinZ(float v) { this.minZ = v; }
    public void setMaxX(float v) { this.maxX = v; }
    public void setMaxY(float v) { this.maxY = v; }
    public void setMaxZ(float v) { this.maxZ = v; }

    /** 一次性设置全部 6 个字段——避免多次 setter 调用。 */
    public void set(float minX, float minY, float minZ,
                    float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    // ── 复制 ───────────────────────────────────

    /**
     * 把 {@code a} 的六个字段复制到 this——覆盖式——不合并。
     *
     * <p>{@code a == null} 时——重置为默认空盒。
     */
    public void copyFrom(IAABB a) {
        if (a == null) {
            this.minX = Float.POSITIVE_INFINITY;
            this.minY = Float.POSITIVE_INFINITY;
            this.minZ = Float.POSITIVE_INFINITY;
            this.maxX = Float.NEGATIVE_INFINITY;
            this.maxY = Float.NEGATIVE_INFINITY;
            this.maxZ = Float.NEGATIVE_INFINITY;
            return;
        }
        this.minX = a.getMinX();
        this.minY = a.getMinY();
        this.minZ = a.getMinZ();
        this.maxX = a.getMaxX();
        this.maxY = a.getMaxY();
        this.maxZ = a.getMaxZ();
    }

    public void affine(float[] mat4){
        affine(this, mat4, this);
    }

    /**
     * 执行变换， mat4 必须为仿射变换，内部不做检查
     */
    public static void affine(
            IAABB aabb,
            float[] mat4, AABB out) {
        if (mat4 == null) {
            throw new IllegalArgumentException("mat4 cannot be null");
        }
        if (out == null) {
            throw new IllegalArgumentException("out cannot be null");
        }

        FloatsHolder holder = PoolManagers.floats().getHolder();
        FloatsPool pool = holder.getPool(3);
        float[] point = pool.acquire();
        try {
            float newMinX = Float.POSITIVE_INFINITY;
            float newMinY = Float.POSITIVE_INFINITY;
            float newMinZ = Float.POSITIVE_INFINITY;
            float newMaxX = Float.NEGATIVE_INFINITY;
            float newMaxY = Float.NEGATIVE_INFINITY;
            float newMaxZ = Float.NEGATIVE_INFINITY;

            // 变换 AABB 的 8 个角点，再重新求 min/max
            for (int i = 0; i < 8; i++) {
                point[0] = ((i & 1) == 0) ? aabb.getMinX() :aabb. getMaxX();
                point[1] = ((i & 2) == 0) ? aabb.getMinY() : aabb.getMaxY();
                point[2] = ((i & 4) == 0) ? aabb.getMinZ() : aabb.getMaxZ();

                KMath.mat4MulVec3Affine(mat4, point, point);

                newMinX = Math.min(newMinX, point[0]);
                newMinY = Math.min(newMinY, point[1]);
                newMinZ = Math.min(newMinZ, point[2]);
                newMaxX = Math.max(newMaxX, point[0]);
                newMaxY = Math.max(newMaxY, point[1]);
                newMaxZ = Math.max(newMaxZ, point[2]);
            }

            out.minX = newMinX;
            out.minY = newMinY;
            out.minZ = newMinZ;
            out.maxX = newMaxX;
            out.maxY = newMaxY;
            out.maxZ = newMaxZ;
        } finally {
            pool.release(point);
        }
    }

    private static boolean isAffine(float[] m) {
        final float EPS = 1.0E-6F;
        // 列主序下：m[3], m[7], m[11], m[15] 是矩阵最后一行
        return Math.abs(m[3])  <= EPS
                && Math.abs(m[7])  <= EPS
                && Math.abs(m[11]) <= EPS
                && Math.abs(m[15] - 1F) <= EPS;
    }

    // ── 合并 ───────────────────────────────────

    /**
     * 把 this 与 {@code a} 合并——结果写入 {@code out}——this 不变。
     *
     * <p>语义：
     * <ul>
     *   <li>{@code a == null} 或空盒——{@code out} 得到 this 的副本</li>
     *   <li>this 是空盒——{@code out} 得到 a 的副本</li>
     *   <li>否则——{@code out} 得到两者的最小外包</li>
     * </ul>
     *
     * <p>{@code out} 可以是 this 本身——先读后写——安全。
     *
     * @param a   参与合并的另一个盒——可为 null
     * @param out 输出目标——不得为 null
     */
    public void union(IAABB a, AABB out) {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        if (this == out) return;

        // 先读入局部变量——保证 out == this 时也安全
        boolean thisEmpty = isEmpty();
        boolean aEmpty    = (a == null) || a.isEmpty();

        if (thisEmpty && aEmpty) {
            out.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
            return;
        }
        if (thisEmpty) {
            out.copyFrom(a);
            return;
        }
        if (aEmpty) {
            out.copyFrom(this);
            return;
        }

        float nMinX = Math.min(this.minX, a.getMinX());
        float nMinY = Math.min(this.minY, a.getMinY());
        float nMinZ = Math.min(this.minZ, a.getMinZ());
        float nMaxX = Math.max(this.maxX, a.getMaxX());
        float nMaxY = Math.max(this.maxY, a.getMaxY());
        float nMaxZ = Math.max(this.maxZ, a.getMaxZ());

        out.set(nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ);
    }

    /** 原地合并——把 {@code a} 并入 this。 */
    public void unionInPlace(IAABB a) {
        union(a, this);
    }

    // ── 扩展 ───────────────────────────────────

    /** 把单个点纳入包围盒——原地修改。 */
    public void expand(float x, float y, float z) {
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (z < minZ) minZ = z;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        if (z > maxZ) maxZ = z;
    }

    // ── 转换 ───────────────────────────────────

    /** 导出为不可变副本——用于共享 / 作为 map key。 */
    public ImmutableAABB toImmutable() {
        return new ImmutableAABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ── Object ────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IAABB)) return false;
        IAABB b = (IAABB) o;
        return Float.compare(minX, b.getMinX()) == 0
                && Float.compare(minY, b.getMinY()) == 0
                && Float.compare(minZ, b.getMinZ()) == 0
                && Float.compare(maxX, b.getMaxX()) == 0
                && Float.compare(maxY, b.getMaxY()) == 0
                && Float.compare(maxZ, b.getMaxZ()) == 0;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(minX);
        h = 31 * h + Float.floatToIntBits(minY);
        h = 31 * h + Float.floatToIntBits(minZ);
        h = 31 * h + Float.floatToIntBits(maxX);
        h = 31 * h + Float.floatToIntBits(maxY);
        h = 31 * h + Float.floatToIntBits(maxZ);
        return h;
    }

    @Override
    public String toString() {
        return "AABB[(" + minX + ", " + minY + ", " + minZ + ") – ("
                + maxX + ", " + maxY + ", " + maxZ + ")]";
    }
}