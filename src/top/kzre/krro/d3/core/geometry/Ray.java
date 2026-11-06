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

    /** 浮点比较容差（长度平方级别） */
    private static final double LEN_SQ_EPS = 1e-30;

    public final float ox, oy, oz;     // 原点
    public final float dx, dy, dz;     // 方向（不一定为单位向量）

    /**
     * 构造射线，不对方向向量进行归一化，也不保证退化
     */
    public Ray(float ox, float oy, float oz,
               float dx, float dy, float dz) {
        this.ox = ox; this.oy = oy; this.oz = oz;
        this.dx = dx; this.dy = dy; this.dz = dz;
    }

    /**
     * 直接构造
     */
    public static Ray create(float ox, float oy, float oz,
                             float dx, float dy, float dz){
        return new Ray(ox, oy, oz, dx, dy, dz);
    }

    /**
     * 构造，但归一化方向向量，方向向量退化报错
     */
    public static Ray normalized(float ox, float oy, float oz,
                                 float dx, float dy, float dz){
        double lenSq = lengthSq(dx, dy, dz);
        if (!isFinitePositive(lenSq)) {
            throw new IllegalArgumentException(
                    "方向向量退化或非法: (" + dx + ", " + dy + ", " + dz + ")");
        }
        double invLen = 1.0 / Math.sqrt(lenSq);
        return new Ray(ox, oy, oz,
                (float) (dx * invLen),
                (float) (dy * invLen),
                (float) (dz * invLen));
    }

    /**
     * 归一化射线，退化报错
     */
    public Ray normalized(){
        return normalized(ox, oy, oz, dx, dy, dz);
    }

    /**
     * 归一化射线方向，返回归一化的射线。
     * 若原射线方向退化，则返回方向为零向量、原点不变的射线。
     */
    public static Ray normalize(Ray ray){
        double lenSq = lengthSq(ray.dx, ray.dy, ray.dz);
        if (!isFinitePositive(lenSq)) {
            return new Ray(ray.ox, ray.oy, ray.oz, 0f, 0f, 0f);
        }
        double invLen = 1.0 / Math.sqrt(lenSq);
        return new Ray(ray.ox, ray.oy, ray.oz,
                (float) (ray.dx * invLen),
                (float) (ray.dy * invLen),
                (float) (ray.dz * invLen));
    }

    /**
     * 是否是退化射线,即方向是否退化
     */
    public boolean isDegraded(){
        return !isFinitePositive(lengthSq(dx, dy, dz));
    }

    /**
     * 方向向量的长度（float 精度）
     */
    public float directionLength() {
        return (float) Math.sqrt(lengthSq(dx, dy, dz));
    }

    /**
     * 返回方向相反的射线（原点不变）
     */
    public Ray negate() {
        return new Ray(ox, oy, oz, -dx, -dy, -dz);
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
     * 计算射线上的点：原点 + t * 方向。
     * 结果写入给定的数组 out[3]。
     */
    public void pointAt(float t, float[] out) {
        out[0] = ox + dx * t;
        out[1] = oy + dy * t;
        out[2] = oz + dz * t;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 方向长度平方，用 double 计算以避免 float 下溢/上溢 */
    private static double lengthSq(float x, float y, float z) {
        return (double) x * x + (double) y * y + (double) z * z;
    }

    /** 是否为正的有限值（排除了 0、NaN、Inf） */
    private static boolean isFinitePositive(double v) {
        return v > LEN_SQ_EPS && !Double.isInfinite(v);
    }

    // ------------------------------------------------------------------
    // Object
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ray)) return false;
        Ray r = (Ray) o;
        return Float.compare(ox, r.ox) == 0
                && Float.compare(oy, r.oy) == 0
                && Float.compare(oz, r.oz) == 0
                && Float.compare(dx, r.dx) == 0
                && Float.compare(dy, r.dy) == 0
                && Float.compare(dz, r.dz) == 0;
    }

    @Override
    public int hashCode() {
        int h = 17;
        h = 31 * h + Float.floatToIntBits(ox);
        h = 31 * h + Float.floatToIntBits(oy);
        h = 31 * h + Float.floatToIntBits(oz);
        h = 31 * h + Float.floatToIntBits(dx);
        h = 31 * h + Float.floatToIntBits(dy);
        h = 31 * h + Float.floatToIntBits(dz);
        return h;
    }

    @Override
    public String toString() {
        return "Ray{origin=(" + ox + ", " + oy + ", " + oz + "), " +
                "direction=(" + dx + ", " + dy + ", " + dz + ")" +
                (isDegraded() ? ", degraded" : "") + "}";
    }
}