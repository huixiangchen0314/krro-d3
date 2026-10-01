package top.kzre.krro.d3.core.geometry;

/**
 * 三维位置——不可变值类型。
 *
 * <p><b>用途</b>：段访问层的参数 / 返回值——避免分散的
 * double 参数——减少装箱——语义清晰。
 *
 * <p><b>不可变</b>：字段 final——构造后不变——线程安全。
 *
 * <p><b>为什么不用 double[3]</b>：
 * <ul>
 *   <li>数组可变——语义模糊</li>
 *   <li>无类型区分——float3 / int3 混淆</li>
 *   <li>长度检查运行时</li>
 * </ul>
 *
 * <p><b>装箱分析</b>：一次 Position 装箱 = 一个对象。
 * 三个 double 分开传 = 三个 Double 装箱。Position 减少装箱次数。
 */
public final class Position {

    public final float x;
    public final float y;
    public final float z;

    public Position(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Position of(double x, double y, double z) {
        return new Position((float) x, (float) y, (float) z);
    }

    /** 原点 (0, 0, 0)——复用单例——避免频繁构造。 */
    public static final Position ORIGIN = new Position(0f, 0f, 0f);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position)) return false;
        Position p = (Position) o;
        return Float.compare(x, p.x) == 0
                && Float.compare(y, p.y) == 0
                && Float.compare(z, p.z) == 0;
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
        return "Position(" + x + ", " + y + ", " + z + ")";
    }
}