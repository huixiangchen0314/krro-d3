package top.kzre.krro.d3.core.geometry;

/**
 * 空间轴常量——0 = X，1 = Y，2 = Z。
 *
 * <p>与 Clojure 侧 {@code top.kzre.krro.d3.core.geometry.axis}
 * 的 X / Y / Z 常量值一致。
 *
 * <p><b>不用 enum</b>：Clojure 的 {@code case} 需要编译期常量——
 * enum 的 {@code .ordinal()} 是方法调用——不满足。
 */
public final class Axis {

    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;

    private Axis() {}

    public static float select(int axisId, float x, float y, float z) {
        switch (axisId) {
            case X: return x;
            case Y: return y;
            case Z: return z;
            default:
                throw new IllegalArgumentException("Unknown axis: " + axisId);
        }
    }

    /** 轴的数字转名称——调试用。 */
    public static String name(int a) {
        switch (a) {
            case X: return "X";
            case Y: return "Y";
            case Z: return "Z";
            default: return "?";
        }
    }
}