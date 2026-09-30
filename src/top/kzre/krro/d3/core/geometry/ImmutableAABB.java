package top.kzre.krro.d3.core.geometry;

/**
 * 不可变的轴对齐包围盒。
 *
 * <p>构造后不可修改——适合共享、缓存、作为 map key。
 * 需要修改时用 {@link #toMutable()} 得到可变副本。
 */
public final class ImmutableAABB implements IAABB {

    private final float minX;
    private final float minY;
    private final float minZ;
    private final float maxX;
    private final float maxY;
    private final float maxZ;

    public static final ImmutableAABB EMPTY = new ImmutableAABB(
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

    public ImmutableAABB(float minX, float minY, float minZ,
                         float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public ImmutableAABB(IAABB a) {
        this.minX = a.getMinX();
        this.minY = a.getMinY();
        this.minZ = a.getMinZ();
        this.maxX = a.getMaxX();
        this.maxY = a.getMaxY();
        this.maxZ = a.getMaxZ();
    }

    @Override public float getMinX() { return minX; }
    @Override public float getMinY() { return minY; }
    @Override public float getMinZ() { return minZ; }
    @Override public float getMaxX() { return maxX; }
    @Override public float getMaxY() { return maxY; }
    @Override public float getMaxZ() { return maxZ; }

    /** 导出为可变副本——用于后续编辑。 */
    public AABB toMutable() {
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 与另一个盒合并——返回新不可变盒——两者都不变。 */
    public ImmutableAABB union(IAABB a) {
        if (a == null || a.isEmpty()) return this;
        if (isEmpty()) return new ImmutableAABB(a);
        return new ImmutableAABB(
                Math.min(minX, a.getMinX()),
                Math.min(minY, a.getMinY()),
                Math.min(minZ, a.getMinZ()),
                Math.max(maxX, a.getMaxX()),
                Math.max(maxY, a.getMaxY()),
                Math.max(maxZ, a.getMaxZ()));
    }

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
        return "ImmutableAABB[(" + minX + ", " + minY + ", " + minZ + ") – ("
                + maxX + ", " + maxY + ", " + maxZ + ")]";
    }
}