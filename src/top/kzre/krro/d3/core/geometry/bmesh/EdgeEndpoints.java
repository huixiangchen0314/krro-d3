package top.kzre.krro.d3.core.geometry.bmesh;

/**
 * 边端点——不可变值类型。
 *
 * <p>int2（v0, v1）——边的两个顶点索引。
 *
 * <p>约定：loop.vert 为 v0 时——loop.next.vert 为 v1。
 */
public final class EdgeEndpoints {

    public final int v0;
    public final int v1;

    public EdgeEndpoints(int v0, int v1) {
        this.v0 = v0;
        this.v1 = v1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EdgeEndpoints)) return false;
        EdgeEndpoints e = (EdgeEndpoints) o;
        return v0 == e.v0 && v1 == e.v1;
    }
    /** 空端点——孤立边默认。 */
    public static final EdgeEndpoints NONE = new EdgeEndpoints(-1, -1);

    @Override
    public int hashCode() {
        return 31 * v0 + v1;
    }

    @Override
    public String toString() {
        return "EdgeEndpoints(" + v0 + ", " + v1 + ")";
    }
}