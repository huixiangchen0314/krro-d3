package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.util.math.KMath;

/**
 * 几何相交算法集合。
 *
 * <p><b>接口约定</b>：所有 AABB 参数走 {@link IAABB}——不触碰具体实现字段。
 *
 * <p><b>KMath 复用</b>：数值运算统一走 {@link KMath}——避免散落的
 * {@code Math.min/max/abs/sqrt}。
 */
public final class IntersectionAlgo {

    private IntersectionAlgo() {}

    /** 平行判定的阈值——三角形面积接近零。 */
    private static final float PARALLEL_EPS = 1e-7f;

    /** 距离计算的最小阈值——射线与线段平行的判定。 */
    private static final float PARALLEL_DET_EPS = 1e-12f;

    // ═══════════════════════════════════════════════
    // 射线 × AABB
    // ═══════════════════════════════════════════════

    /** 射线 × AABB——纯 float 参数——无 AABB 分配。 */
    public static RayHit intersectRayAABB(
            Ray ray,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ) {
        float tmin = Float.NEGATIVE_INFINITY;
        float tmax = Float.POSITIVE_INFINITY;

        if (ray.dx != 0.0f) {
            float tx1 = (minX - ray.ox) / ray.dx;
            float tx2 = (maxX - ray.ox) / ray.dx;
            tmin = KMath.max(tmin, KMath.min(tx1, tx2));
            tmax = KMath.min(tmax, KMath.max(tx1, tx2));
        } else if (ray.ox < minX || ray.ox > maxX) {
            return RayHit.MISS;
        }

        if (ray.dy != 0.0f) {
            float ty1 = (minY - ray.oy) / ray.dy;
            float ty2 = (maxY - ray.oy) / ray.dy;
            tmin = KMath.max(tmin, KMath.min(ty1, ty2));
            tmax = KMath.min(tmax, KMath.max(ty1, ty2));
        } else if (ray.oy < minY || ray.oy > maxY) {
            return RayHit.MISS;
        }

        if (ray.dz != 0.0f) {
            float tz1 = (minZ - ray.oz) / ray.dz;
            float tz2 = (maxZ - ray.oz) / ray.dz;
            tmin = KMath.max(tmin, KMath.min(tz1, tz2));
            tmax = KMath.min(tmax, KMath.max(tz1, tz2));
        } else if (ray.oz < minZ || ray.oz > maxZ) {
            return RayHit.MISS;
        }

        if (tmax >= tmin && tmax > 0f) {
            float t = tmin > 0f ? tmin : tmax;
            return RayHit.of(t);
        }
        return RayHit.MISS;
    }

    /** 球体 × AABB——纯 float 参数。 */
    public static boolean sphereIntersectsAABB(
            float cx, float cy, float cz, float radius,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ) {
        float closestX = KMath.clamp(cx, minX, maxX);
        float closestY = KMath.clamp(cy, minY, maxY);
        float closestZ = KMath.clamp(cz, minZ, maxZ);
        float dx = cx - closestX;
        float dy = cy - closestY;
        float dz = cz - closestZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public static RayHit intersectRayAABB(Ray ray, IAABB box) {
        return intersectRayAABB(ray,
                box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    public static boolean sphereIntersectsAABB(float cx, float cy, float cz,
                                               float radius, IAABB box) {
        return sphereIntersectsAABB(cx, cy, cz, radius,
                box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    // ═══════════════════════════════════════════════
    // 射线 × 三角形
    // ═══════════════════════════════════════════════

    /**
     * 射线与三角形相交（Möller–Trumbore）。
     *
     * @param ray 射线
     * @param v0x,v0y,v0z 顶点 0
     * @param v1x,v1y,v1z 顶点 1
     * @param v2x,v2y,v2z 顶点 2
     * @return 命中结果——含重心坐标 (u, v)
     */
    public static RayTriangleHit intersectRayTriangle(
            Ray ray,
            float v0x, float v0y, float v0z,
            float v1x, float v1y, float v1z,
            float v2x, float v2y, float v2z) {

        float edge1x = v1x - v0x, edge1y = v1y - v0y, edge1z = v1z - v0z;
        float edge2x = v2x - v0x, edge2y = v2y - v0y, edge2z = v2z - v0z;

        // h = dir × edge2
        float hx = ray.dy * edge2z - ray.dz * edge2y;
        float hy = ray.dz * edge2x - ray.dx * edge2z;
        float hz = ray.dx * edge2y - ray.dy * edge2x;

        float a = edge1x * hx + edge1y * hy + edge1z * hz;
        if (KMath.abs(a) < PARALLEL_EPS) {
            return RayTriangleHit.MISS;   // 平行
        }

        float f = 1.0f / a;
        float sx = ray.ox - v0x, sy = ray.oy - v0y, sz = ray.oz - v0z;
        float u = f * (sx * hx + sy * hy + sz * hz);
        if (u < 0.0f || u > 1.0f) {
            return RayTriangleHit.MISS;
        }

        // q = s × edge1
        float qx = sy * edge1z - sz * edge1y;
        float qy = sz * edge1x - sx * edge1z;
        float qz = sx * edge1y - sy * edge1x;

        float v = f * (ray.dx * qx + ray.dy * qy + ray.dz * qz);
        if (v < 0.0f || u + v > 1.0f) {
            return RayTriangleHit.MISS;
        }

        float t = f * (edge2x * qx + edge2y * qy + edge2z * qz);
        if (t > PARALLEL_EPS) {
            return RayTriangleHit.of(t, u, v);
        }
        return RayTriangleHit.MISS;
    }

    // ═══════════════════════════════════════════════
    // AABB 谓词
    // ═══════════════════════════════════════════════

    /** 点是否在 AABB 内（包含边界）。 */
    public static boolean aabbContainsPoint(IAABB box, float x, float y, float z) {
        return x >= box.getMinX() && x <= box.getMaxX()
                && y >= box.getMinY() && y <= box.getMaxY()
                && z >= box.getMinZ() && z <= box.getMaxZ();
    }

    /** 两个 AABB 是否相交（包含边界）。 */
    public static boolean intersectsAABBs(IAABB a, IAABB b) {
        return a.getMinX() <= b.getMaxX() && a.getMaxX() >= b.getMinX()
                && a.getMinY() <= b.getMaxY() && a.getMaxY() >= b.getMinY()
                && a.getMinZ() <= b.getMaxZ() && a.getMaxZ() >= b.getMinZ();
    }

    // ═══════════════════════════════════════════════
    // 射线 × 线段
    // ═══════════════════════════════════════════════

    /**
     * 射线到线段的最近点。
     *
     * <p>参考：<a href="https://geomalgorithms.com/a07-_distance.html">
     * geomalgorithms.com/a07-_distance</a>
     *
     * @param ray 射线
     * @param p1x,p1y,p1z 线段端点 1
     * @param p2x,p2y,p2z 线段端点 2
     * @return 最近距离 + 参数——线段参数 s ∈ [0, 1]——射线参数 t
     */
    public static RaySegmentClosest raySegmentClosest(
            Ray ray,
            float p1x, float p1y, float p1z,
            float p2x, float p2y, float p2z) {

        float wx = ray.ox - p1x, wy = ray.oy - p1y, wz = ray.oz - p1z;
        float ux = p2x - p1x, uy = p2y - p1y, uz = p2z - p1z;
        float vx = ray.dx, vy = ray.dy, vz = ray.dz;

        float a = ux * ux + uy * uy + uz * uz;  // |u|²
        float b = ux * vx + uy * vy + uz * vz;  // u · v
        float c = vx * vx + vy * vy + vz * vz;  // |v|²（方向向量为单位长度时应为 1）
        float d = ux * wx + uy * wy + uz * wz;  // u · w
        float e = vx * wx + vy * wy + vz * wz;  // v · w

        float det = a * c - b * b;
        float s, t;

        if (det < PARALLEL_DET_EPS) {
            // 射线与线段平行——退化——取线段起点
            s = 0.0f;
            t = (b > c ? d / b : e / c);
        } else {
            s = (b * e - c * d) / det;
            t = (a * e - b * d) / det;
        }

        // s 限制在线段 [0, 1]
        s = KMath.clamp(s, 0.0f, 1.0f);

        // 线段上最近点
        float closestX = p1x + s * ux;
        float closestY = p1y + s * uy;
        float closestZ = p1z + s * uz;

        // 射线上对应点
        float rayPointX = ray.ox + t * ray.dx;
        float rayPointY = ray.oy + t * ray.dy;
        float rayPointZ = ray.oz + t * ray.dz;

        float dx = rayPointX - closestX;
        float dy = rayPointY - closestY;
        float dz = rayPointZ - closestZ;
        float dist = KMath.sqrt(dx * dx + dy * dy + dz * dz);

        return RaySegmentClosest.of(dist, t, s);
    }

    // ═══════════════════════════════════════════════
    // 球体谓词
    // ═══════════════════════════════════════════════


    /** 点是否在球体内（含边界）。 */
    public static boolean sphereContainsPoint(float cx, float cy, float cz,
                                              float radius,
                                              float x, float y, float z) {
        return pointToSphereSqDist(cx, cy, cz, x, y, z) <= radius * radius;
    }

    /** 点到球心的平方距离——避免开方——用于快速比较。 */
    public static float pointToSphereSqDist(float cx, float cy, float cz,
                                            float x, float y, float z) {
        float dx = x - cx, dy = y - cy, dz = z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** 点到球心的实际距离。 */
    public static float pointToSphereDist(float cx, float cy, float cz,
                                          float x, float y, float z) {
        return KMath.sqrt(pointToSphereSqDist(cx, cy, cz, x, y, z));
    }
}