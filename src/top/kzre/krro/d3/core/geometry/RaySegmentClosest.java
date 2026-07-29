package top.kzre.krro.d3.core.geometry;

/**
 * 射线与线段最近点计算结果（不可变）。
 * 如果射线与线段平行或最近点无效，hit 为 false。
 */
public final class RaySegmentClosest {
    public static final RaySegmentClosest MISS = new RaySegmentClosest(Float.MAX_VALUE, 0, 0, false);

    public final float distance;   // 最近距离
    public final float t;          // 射线参数（原点 + t * 方向）
    public final float s;          // 线段参数（0~1 表示在线段内部）
    public final boolean hit;      // 是否有效

    private RaySegmentClosest(float distance, float t, float s, boolean hit) {
        this.distance = distance;
        this.t = t;
        this.s = s;
        this.hit = hit;
    }

    public static RaySegmentClosest of(float distance, float t, float s) {
        return new RaySegmentClosest(distance, t, s, true);
    }
}