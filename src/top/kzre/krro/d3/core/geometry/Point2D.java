package top.kzre.krro.d3.core.geometry;

public final class Point2D {
    public float x, y;

    public Point2D(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Point2D)) {
            return false;
        }
        Point2D other = (Point2D) obj;
        // 用 Float.compare 而非 == 比较，以正确处理 NaN / -0.0f 等特殊情况：
        // Float.compare(NaN, NaN) == 0  -> NaN 点等于自身
        // Float.compare(0.0f, -0.0f) != 0 -> 0.0 与 -0.0 视为不同
        return Float.compare(this.x, other.x) == 0
                && Float.compare(this.y, other.y) == 0;
    }

    @Override
    public int hashCode() {
        // 31 作为乘子是 Java 中 hashCode 的惯例，能减少碰撞
        int result = Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        return result;
    }

    @Override
    public String toString() {
        // 简洁可读，如 "Point2D(1.0, 2.0)"
        return "Point2D(" + x + ", " + y + ")";
    }
}