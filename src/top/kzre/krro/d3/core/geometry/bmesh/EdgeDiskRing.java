package top.kzre.krro.d3.core.geometry.bmesh;

public final class EdgeDiskRing {
    public final int v0Next, v0Prev, v1Next, v1Prev;
    public static final EdgeDiskRing NONE = new EdgeDiskRing(-1, -1, -1, -1);

    public EdgeDiskRing(int v0Next, int v0Prev, int v1Next, int v1Prev) {
        this.v0Next = v0Next;
        this.v0Prev = v0Prev;
        this.v1Next = v1Next;
        this.v1Prev = v1Prev;
    }
}