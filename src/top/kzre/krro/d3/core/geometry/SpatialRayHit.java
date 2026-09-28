package top.kzre.krro.d3.core.geometry;

/**
 * 空间索引的射线查询结果。
 *
 * <p><b>调用约定</b>：不绑定具体结构——BVH / KD / 其他空间索引
 * 都可以返回这个类型。调用方拿到 (primitiveId, t)——语义统一。
 *
 * <p>未命中用 {@link #MISS} 单例——而不是 null——
 * 调用方无需判空——{@link #isHit()} 统一判断。
 */
public final class SpatialRayHit {

    /** 命中的图元 id。未命中为 -1。 */
    public final int primitiveId;

    /** 射线参数 t——命中点 = origin + t × direction。未命中为 +∞。 */
    public final float t;

    public SpatialRayHit(int primitiveId, float t) {
        this.primitiveId = primitiveId;
        this.t = t;
    }

    /** 未命中单例。 */
    public static final SpatialRayHit MISS =
            new SpatialRayHit(-1, Float.POSITIVE_INFINITY);

    public boolean isHit() {
        return primitiveId >= 0;
    }

    @Override
    public String toString() {
        return isHit()
                ? "SpatialRayHit[primitiveId=" + primitiveId + ", t=" + t + "]"
                : "SpatialRayHit[MISS]";
    }
}