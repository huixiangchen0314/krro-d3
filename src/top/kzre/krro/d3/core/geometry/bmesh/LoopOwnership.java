package top.kzre.krro.d3.core.geometry.bmesh;

/**
 * 环归属——不可变值类型。
 *
 * <p>int3（vert, edge, face）——环所属的顶点 / 边 / 面。
 */
public final class LoopOwnership {

    public final int vert;
    public final int edge;
    public final int face;

    public LoopOwnership(int vert, int edge, int face) {
        this.vert = vert;
        this.edge = edge;
        this.face = face;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoopOwnership)) return false;
        LoopOwnership l = (LoopOwnership) o;
        return vert == l.vert && edge == l.edge && face == l.face;
    }

    @Override
    public int hashCode() {
        int h = vert;
        h = 31 * h + edge;
        h = 31 * h + face;
        return h;
    }

    @Override
    public String toString() {
        return "LoopOwnership(vert=" + vert + ", edge=" + edge + ", face=" + face + ")";
    }
}