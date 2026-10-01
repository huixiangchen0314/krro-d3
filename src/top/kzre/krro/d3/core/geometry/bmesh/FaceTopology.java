package top.kzre.krro.d3.core.geometry.bmesh;

/**
 * 面拓扑——不可变值类型。
 *
 * <p>int2（loop, len）——面的环入口 + 环长。
 *
 * <p>三角形 = 3——四边形 = 4——N 边形 = N。
 */
public final class FaceTopology {

    public final int loop;
    public final int len;

    public FaceTopology(int loop, int len) {
        this.loop = loop;
        this.len = len;
    }
    /** 空拓扑——孤立面默认。 */
    public static final FaceTopology NONE = new FaceTopology(-1, 0);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FaceTopology)) return false;
        FaceTopology t = (FaceTopology) o;
        return loop == t.loop && len == t.len;
    }

    @Override
    public int hashCode() {
        return 31 * loop + len;
    }

    @Override
    public String toString() {
        return "FaceTopology(loop=" + loop + ", len=" + len + ")";
    }
}