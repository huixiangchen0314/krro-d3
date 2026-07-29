package top.kzre.krro.d3.core.geometry;

/**
 * 射线与三角形的相交结果（不可变）。
 * 如果未命中，hit 为 false，其他字段无意义。
 */
public final class RayTriangleHit {
    public static final RayTriangleHit MISS = new RayTriangleHit(Float.MAX_VALUE, 0, 0, false);

    public final float t;      // 射线参数，正数表示交点距离
    public final float u;      // 重心坐标 u
    public final float v;      // 重心坐标 v
    public final boolean hit;  // 是否命中

    public RayTriangleHit(float t, float u, float v, boolean hit) {
        this.t = t;
        this.u = u;
        this.v = v;
        this.hit = hit;
    }

    /** 便捷工厂：产生命中结果 */
    public static RayTriangleHit of(float t, float u, float v) {
        return new RayTriangleHit(t, u, v, true);
    }
}