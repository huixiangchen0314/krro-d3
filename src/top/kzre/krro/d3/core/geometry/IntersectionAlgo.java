package top.kzre.krro.d3.core.geometry;

public final class IntersectionAlgo {
    private IntersectionAlgo() {}

    /**
     * 射线与 AABB 相交（平板法）。
     */
    public static RayAabbHit intersectRayAABB(Ray ray, AABB box) {
        float tmin = Float.NEGATIVE_INFINITY, tmax = Float.POSITIVE_INFINITY;

        // X 轴
        if (ray.dx != 0.0f) {
            float tx1 = (box.minX - ray.ox) / ray.dx;
            float tx2 = (box.maxX - ray.ox) / ray.dx;
            tmin = Math.max(tmin, Math.min(tx1, tx2));
            tmax = Math.min(tmax, Math.max(tx1, tx2));
        } else {
            if (ray.ox < box.minX || ray.ox > box.maxX) return RayAabbHit.MISS;
        }

        // Y 轴
        if (ray.dy != 0.0f) {
            float ty1 = (box.minY - ray.oy) / ray.dy;
            float ty2 = (box.maxY - ray.oy) / ray.dy;
            tmin = Math.max(tmin, Math.min(ty1, ty2));
            tmax = Math.min(tmax, Math.max(ty1, ty2));
        } else {
            if (ray.oy < box.minY || ray.oy > box.maxY) return RayAabbHit.MISS;
        }

        // Z 轴
        if (ray.dz != 0.0f) {
            float tz1 = (box.minZ - ray.oz) / ray.dz;
            float tz2 = (box.maxZ - ray.oz) / ray.dz;
            tmin = Math.max(tmin, Math.min(tz1, tz2));
            tmax = Math.min(tmax, Math.max(tz1, tz2));
        } else {
            if (ray.oz < box.minZ || ray.oz > box.maxZ) return RayAabbHit.MISS;
        }

        if (tmax >= tmin && tmax > 0) {
            float t = tmin > 0 ? tmin : tmax;
            return RayAabbHit.of(t);
        }
        return RayAabbHit.MISS;
    }

    /**
     * 射线与三角形相交（Möller–Trumbore）。
     * @param v0x,v0y,v0z 顶点0
     * @param v1x,v1y,v1z 顶点1
     * @param v2x,v2y,v2z 顶点2
     */
    public static RayTriangleHit intersectRayTriangle(
            Ray ray,
            float v0x, float v0y, float v0z,
            float v1x, float v1y, float v1z,
            float v2x, float v2y, float v2z) {

        float edge1x = v1x - v0x, edge1y = v1y - v0y, edge1z = v1z - v0z;
        float edge2x = v2x - v0x, edge2y = v2y - v0y, edge2z = v2z - v0z;

        float hx = ray.dy * edge2z - ray.dz * edge2y;
        float hy = ray.dz * edge2x - ray.dx * edge2z;
        float hz = ray.dx * edge2y - ray.dy * edge2x;
        float a = edge1x * hx + edge1y * hy + edge1z * hz;
        if (Math.abs(a) < 1e-7f) return RayTriangleHit.MISS; // 平行

        float f = 1.0f / a;
        float sx = ray.ox - v0x, sy = ray.oy - v0y, sz = ray.oz - v0z;
        float u = f * (sx * hx + sy * hy + sz * hz);
        if (u < 0.0f || u > 1.0f) return RayTriangleHit.MISS;

        float qx = sy * edge1z - sz * edge1y;
        float qy = sz * edge1x - sx * edge1z;
        float qz = sx * edge1y - sy * edge1x;
        float v = f * (ray.dx * qx + ray.dy * qy + ray.dz * qz);
        if (v < 0.0f || u + v > 1.0f) return RayTriangleHit.MISS;

        float t = f * (edge2x * qx + edge2y * qy + edge2z * qz);
        if (t > 1e-7f) { // 有效交点
            return RayTriangleHit.of(t, u, v);
        }
        return RayTriangleHit.MISS;
    }

    /** 点是否在 AABB 内（包含边界） */
    public static boolean aabbContainsPoint(AABB box, float x, float y, float z) {
        return x >= box.minX && x <= box.maxX &&
                y >= box.minY && y <= box.maxY &&
                z >= box.minZ && z <= box.maxZ;
    }

    /** 两个 AABB 是否相交 */
    public static boolean intersectsAABBs(AABB a, AABB b) {
        return a.minX <= b.maxX && a.maxX >= b.minX &&
                a.minY <= b.maxY && a.maxY >= b.minY &&
                a.minZ <= b.maxZ && a.maxZ >= b.minZ;
    }


    /**
     * 射线到线段的最近点。
     * 参考：https://geomalgorithms.com/a07-_distance.html
     * @param p1x,p1y,p1z 线段端点1
     * @param p2x,p2y,p2z 线段端点2
     * @return RaySegmentClosest 结果，包含最近距离和参数
     */
    public static RaySegmentClosest raySegmentClosest(
            Ray ray,
            float p1x, float p1y, float p1z,
            float p2x, float p2y, float p2z) {

        float wx = ray.ox - p1x, wy = ray.oy - p1y, wz = ray.oz - p1z;
        float ux = p2x - p1x, uy = p2y - p1y, uz = p2z - p1z;
        float vx = ray.dx, vy = ray.dy, vz = ray.dz;

        float a = ux * ux + uy * uy + uz * uz;  // 线段长度平方
        float b = ux * vx + uy * vy + uz * vz;
        float c = vx * vx + vy * vy + vz * vz;  // 方向向量长度平方，应为1
        float d = ux * wx + uy * wy + uz * wz;
        float e = vx * wx + vy * wy + vz * wz;

        float det = a * c - b * b;
        float s, t;

        if (det < 1e-12f) { // 射线与线段平行
            s = 0.0f;
            t = (b > c ? d / b : e / c); // 选择最近端点
        } else {
            s = (b * e - c * d) / det;
            t = (a * e - b * d) / det;
        }

        // 将 s 限制在线段 [0,1]
        s = Math.max(0.0f, Math.min(1.0f, s));

        // 最近点坐标
        float closestX = p1x + s * ux;
        float closestY = p1y + s * uy;
        float closestZ = p1z + s * uz;

        float rayPointX = ray.ox + t * ray.dx;
        float rayPointY = ray.oy + t * ray.dy;
        float rayPointZ = ray.oz + t * ray.dz;

        float dist = (float) Math.sqrt(
                (rayPointX - closestX) * (rayPointX - closestX) +
                        (rayPointY - closestY) * (rayPointY - closestY) +
                        (rayPointZ - closestZ) * (rayPointZ - closestZ)
        );

        return RaySegmentClosest.of(dist, t, s);
    }


    /**
     * 球体是否与 AABB 相交（使用最近点距离测试）。
     */
    public static boolean sphereIntersectsAABB(float cx, float cy, float cz, float radius, AABB box) {
        // 找到 AABB 上离球心最近的点
        float closestX = Math.max(box.minX, Math.min(cx, box.maxX));
        float closestY = Math.max(box.minY, Math.min(cy, box.maxY));
        float closestZ = Math.max(box.minZ, Math.min(cz, box.maxZ));

        float dx = cx - closestX;
        float dy = cy - closestY;
        float dz = cz - closestZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /**
     * 点是否在球体内。
     */
    public static boolean sphereContainsPoint(float cx, float cy, float cz, float radius, float x, float y, float z) {
        float dx = x - cx, dy = y - cy, dz = z - cz;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /**
     * 点到球心的距离（平方距离），避免开方，用于快速比较。
     * @return 平方距离
     */
    public static float pointToSphereSqDist(float cx, float cy, float cz, float x, float y, float z) {
        float dx = x - cx, dy = y - cy, dz = z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 点到球心的实际距离。
     */
    public static float pointToSphereDist(float cx, float cy, float cz, float x, float y, float z) {
        return (float) Math.sqrt(pointToSphereSqDist(cx, cy, cz, x, y, z));
    }
}