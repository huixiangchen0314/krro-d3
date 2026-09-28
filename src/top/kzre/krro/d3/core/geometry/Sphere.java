package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.util.math.KMath;

/**
 * 球体——中心 (cx, cy, cz) + 半径 radius。
 *
 * <p>与 {@link Ray} 同构——不可变值类、字段直访、无接口。
 * <ul>
 *   <li>单一实现——接口无多态价值</li>
 *   <li>天然不可变——无"可变版本"需求</li>
 *   <li>高频构造 + 字段直访——性能敏感</li>
 * </ul>
 *
 * <p><b>用途</b>：打包球体参数——Clojure 侧 primitive 签名最多 4 参数——
 * 把 (cx, cy, cz, radius) 打成单个对象——传参变成 1 个。
 */
public final class Sphere {

    public final float cx;
    public final float cy;
    public final float cz;
    public final float radius;

    public Sphere(float cx, float cy, float cz, float radius) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
    }

    /**
     * 从 AABB 构造外接球——中心 = 盒中心——半径 = 半对角线长。
     */
    public static Sphere fromAABB(IAABB box) {
        float cx = box.centerX();
        float cy = box.centerY();
        float cz = box.centerZ();
        float dx = box.getMaxX() - box.getMinX();
        float dy = box.getMaxY() - box.getMinY();
        float dz = box.getMaxZ() - box.getMinZ();
        float radius = 0.5f * KMath.sqrt(dx * dx + dy * dy + dz * dz);
        return new Sphere(cx, cy, cz, radius);
    }

    /** 点是否在球内（含边界）。 */
    public boolean contains(float x, float y, float z) {
        float dx = x - cx;
        float dy = y - cy;
        float dz = z - cz;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    @Override
    public String toString() {
        return "Sphere[(" + cx + ", " + cy + ", " + cz + ") r=" + radius + "]";
    }
}