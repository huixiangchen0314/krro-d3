package top.kzre.krro.d3.core.geometry;

/**
 * UV 坐标——不可变值类型。
 *
 * <p>环 UV——float2（u, v）。
 */
public final class UV {

    public final float u;
    public final float v;

    public UV(float u, float v) {
        this.u = u;
        this.v = v;
    }

    public static UV of(double u, double v) {
        return new UV((float) u, (float) v);
    }

    public static final UV ZERO = new UV(0f, 0f);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UV)) return false;
        UV uv = (UV) o;
        return Float.compare(u, uv.u) == 0
                && Float.compare(v, uv.v) == 0;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(u);
        h = 31 * h + Float.floatToIntBits(v);
        return h;
    }

    @Override
    public String toString() {
        return "UV(" + u + ", " + v + ")";
    }
}