package top.kzre.krro.d3.core.geometry;

import java.util.Collection;

public final class AABB {
    public final float minX, minY, minZ, maxX, maxY, maxZ;
    public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    /** 合并两个包围盒，返回新的 AABB */
    public static AABB union(AABB a, AABB b) {
        if (a == null) return b;
        if (b == null) return a;
        return new AABB(
                Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ));
    }

    public static AABB union(Collection<AABB> aabbs) {
        if (aabbs == null || aabbs.isEmpty()) return null;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        boolean has = false;
        for (AABB box : aabbs) {
            if (box != null) {
                if (box.minX < minX) minX = box.minX;
                if (box.minY < minY) minY = box.minY;
                if (box.minZ < minZ) minZ = box.minZ;
                if (box.maxX > maxX) maxX = box.maxX;
                if (box.maxY > maxY) maxY = box.maxY;
                if (box.maxZ > maxZ) maxZ = box.maxZ;
                has = true;
            }
        }
        return has ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    /** 包围盒的中心点（返回长度为3的数组） */
    public static float[] center(AABB box) {
        return new float[]{
                (box.minX + box.maxX) * 0.5f,
                (box.minY + box.maxY) * 0.5f,
                (box.minZ + box.maxZ) * 0.5f};
    }

    /** 包围盒的半尺寸（extent） */
    public static float[] extent(AABB box) {
        return new float[]{
                (box.maxX - box.minX) * 0.5f,
                (box.maxY - box.minY) * 0.5f,
                (box.maxZ - box.minZ) * 0.5f};
    }

    /** 将包围盒向外扩展 delta（所有方向），返回新的 AABB */
    public static AABB expand(AABB box, float delta) {
        return new AABB(
                box.minX - delta, box.minY - delta, box.minZ - delta,
                box.maxX + delta, box.maxY + delta, box.maxZ + delta);
    }

    /** 包围盒体积 */
    public static float volume(AABB box) {
        return (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
    }

    /** 包围盒表面积 */
    public static float surfaceArea(AABB box) {
        float dx = box.maxX - box.minX, dy = box.maxY - box.minY, dz = box.maxZ - box.minZ;
        return 2.0f * (dx*dy + dy*dz + dz*dx);
    }
}