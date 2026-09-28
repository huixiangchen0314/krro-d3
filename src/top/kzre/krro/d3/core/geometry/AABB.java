package top.kzre.krro.d3.core.geometry;

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