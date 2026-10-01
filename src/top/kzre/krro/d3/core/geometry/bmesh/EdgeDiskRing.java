package top.kzre.krro.d3.core.geometry.bmesh;

/**
 * 边磁盘环——不可变值类型。
 *
 * <p>int4（v0Next, v0Prev, v1Next, v1Prev）——边在两端点周围各参与一个
 * 双向出边环。
 *
 * <p>单元素环——next == prev == 边自身。
 * 孤立边——四个字段都是 -1。
 *
 * <p>与 Blender 的 edge-disk-link 语义一致——绕顶点遍历不依赖 loop 方向。
 */
public final class EdgeDiskRing {

    public final int v0Next;
    public final int v0Prev;
    public final int v1Next;
    public final int v1Prev;

    public EdgeDiskRing(int v0Next, int v0Prev, int v1Next, int v1Prev) {
        this.v0Next = v0Next;
        this.v0Prev = v0Prev;
        this.v1Next = v1Next;
        this.v1Prev = v1Prev;
    }

    /** 空环——孤立边默认。 */
    public static final EdgeDiskRing NONE = new EdgeDiskRing(-1, -1, -1, -1);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EdgeDiskRing)) return false;
        EdgeDiskRing r = (EdgeDiskRing) o;
        return v0Next == r.v0Next
                && v0Prev == r.v0Prev
                && v1Next == r.v1Next
                && v1Prev == r.v1Prev;
    }

    @Override
    public int hashCode() {
        int h = v0Next;
        h = 31 * h + v0Prev;
        h = 31 * h + v1Next;
        h = 31 * h + v1Prev;
        return h;
    }

    @Override
    public String toString() {
        return "EdgeDiskRing(v0Next=" + v0Next
                + ", v0Prev=" + v0Prev
                + ", v1Next=" + v1Next
                + ", v1Prev=" + v1Prev + ")";
    }
}