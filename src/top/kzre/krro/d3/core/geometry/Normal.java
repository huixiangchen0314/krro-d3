package top.kzre.krro.d3.core.geometry;

/**
 * 三维法线——不可变值类型。
 *
 * <p>面法线——float3（x, y, z）——单位向量。
 */
public final class Normal {

    public final float x;
    public final float y;
    public final float z;

    public Normal(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Normal of(double x, double y, double z) {
        return new Normal((float) x, (float) y, (float) z);
    }

    public static final Normal UP = new Normal(0f, 1f, 0f);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Normal)) return false;
        Normal n = (Normal) o;
        return Float.compare(x, n.x) == 0
                && Float.compare(y, n.y) == 0
                && Float.compare(z, n.z) == 0;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(x);
        h = 31 * h + Float.floatToIntBits(y);
        h = 31 * h + Float.floatToIntBits(z);
        return h;
    }

    @Override
    public String toString() {
        return "Normal(" + x + ", " + y + ", " + z + ")";
    }
}