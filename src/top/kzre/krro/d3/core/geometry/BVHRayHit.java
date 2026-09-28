package top.kzre.krro.d3.core.geometry;

/**
 * BVH 射线查询命中结果。
 *
 * <p><b>值类</b>——不可变、字段直访——对外 API 的返回类型。
 *
 * <p>未命中用 {@link #MISS} 单例——而不是 null——
 * 调用方无需判空——{@link #isHit()} 统一判断。
 */
public final class BVHRayHit {

    /** 命中的图元 id。未命中为 -1。 */
    public final int primitiveId;

    /** 射线参数 t——命中点 = origin + t × direction。未命中为 +∞。 */
    public final float t;

    public BVHRayHit(int primitiveId, float t) {
        this.primitiveId = primitiveId;
        this.t = t;
    }

    /** 未命中单例。 */
    public static final BVHRayHit MISS =
            new BVHRayHit(-1, Float.POSITIVE_INFINITY);

    public boolean isHit() {
        return primitiveId >= 0;
    }

    @Override
    public String toString() {
        return isHit()
                ? "BVHRayHit[primitiveId=" + primitiveId + ", t=" + t + "]"
                : "BVHRayHit[MISS]";
    }
}