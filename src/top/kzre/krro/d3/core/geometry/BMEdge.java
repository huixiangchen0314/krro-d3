package top.kzre.krro.d3.core.geometry;

/**
 * BMesh 边。连接两个顶点。
 *
 * <p>{@code loop} 是径向环的入口——任选一条围绕本边的 loop。从它
 * 出发沿 {@code radialNext} 遍历可以访问边的所有邻接面。
 *
 * <p>允许非流形：一条边可以连接任意数量的面——径向环是双向链表，
 * 不限于两项。
 *
 * <p><b>纯数据</b>：不带编辑器标记、不带烘焙索引。
 */
public final class BMEdge {

    /** 端点 v0。约定：loop.vert 为 v0 时，loop.next.vert 为 v1。 */
    BMVert v0, v1;

    /** 径向环入口。{@code null} 表示无边界面（孤立边）。 */
    BMLoop loop;

    // ── 公开访问器 ─────────────────────────────

    public BMVert v0()   { return v0; }
    public BMVert v1()   { return v1; }
    public BMLoop loop() { return loop; }

    /** 另一个端点。 */
    public BMVert other(BMVert v) {
        if (v == v0) return v1;
        if (v == v1) return v0;
        throw new IllegalArgumentException("vert is not an endpoint");
    }

    /** 是否包含指定顶点。 */
    public boolean has(BMVert v) { return v == v0 || v == v1; }

    @Override
    public String toString() {
        return "BMEdge(" + v0 + " - " + v1 + ")";
    }
}