package top.kzre.krro.d3.core.geometry;

/**
 * 射线与 AABB 的相交结果（不可变）。
 * 如果未命中，hit 为 false，t 值无意义（但保证为 Float.MAX_VALUE）。
 */
public final class RayHit {
    public static final RayHit MISS = new RayHit(Float.MAX_VALUE, false);

    public final float t;        // 射线参数，命中时为正数，表示沿射线的距离
    public final boolean hit;    // 是否命中

    private RayHit(float t, boolean hit) {
        this.t = t;
        this.hit = hit;
    }

    public static RayHit of(float t) {
        return new RayHit(t, true);
    }
}