package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.pool.FloatsHolder;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.PoolManagers;

/**
 * 不可变射线，由原点 (ox, oy, oz) 和单位方向 (dx, dy, dz) 组成。
 * 方向在构造时自动归一化（可选）。
 */
public final class Ray {
    private static final FloatsPool vec3Pool;
    static {
        FloatsHolder holder = PoolManagers.floats().getHolder();
        vec3Pool = holder.getPool(3);
    }
    public final float ox, oy, oz;     // 原点
    public final float dx, dy, dz;     // 方向（单位向量）

    /**
     * 构造射线，方向向量会被归一化。
     */
    public Ray(float ox, float oy, float oz,
               float dx, float dy, float dz) {
        this.ox = ox; this.oy = oy; this.oz = oz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.dx = dx / len;
        this.dy = dy / len;
        this.dz = dz / len;
    }

    /**
     * 变换射线，只支持仿射变换，外部自己保证 mat4 为仿射。
     * 内部不做检查
     */
    public static Ray affine(Ray ray, float[] mat4){
        float[] vec3 = vec3Pool.acquire();
        float ox;
        float oy;
        float oz;
        float dx;
        float dy;
        float dz;
        try {
            vec3[0] = ray.ox;
            vec3[1] = ray.oy;
            vec3[2] = ray.oz;
            // 原地写出，点变换
            KMath.mat4MulVec3Affine(mat4, vec3, vec3);
            ox = vec3[0];
            oy = vec3[1];
            oz = vec3[2];
            vec3[0] = ray.dx;
            vec3[1] = ray.dy;
            vec3[2] = ray.dz;
            // 原地写出，方向变换
            KMath.mat4MulDir3(mat4, vec3, vec3);
            dx = vec3[0];
            dy = vec3[1];
            dz = vec3[2];
        }finally {
            vec3Pool.release(vec3);
        }

        return new Ray(ox, oy, oz, dx, dy, dz);
    }


    /**
     * 直接指定原点与已归一化的方向，用于内部高效构造。
     */
    private Ray(float ox, float oy, float oz,
                float dx, float dy, float dz,
                boolean normalized) {
        this.ox = ox; this.oy = oy; this.oz = oz;
        this.dx = dx; this.dy = dy; this.dz = dz;
    }

    /**
     * 从原点指向目标点创建射线，方向自动归一化。
     */
    public static Ray fromPoints(float ox, float oy, float oz,
                                 float tx, float ty, float tz) {
        return new Ray(ox, oy, oz, tx - ox, ty - oy, tz - oz);
    }

    /**
     * 计算射线上的点：原点 + t * 方向。
     * 结果写入给定的数组 out[3]。
     */
    public void pointAt(float t, float[] out) {
        out[0] = ox + dx * t;
        out[1] = oy + dy * t;
        out[2] = oz + dz * t;
    }

}