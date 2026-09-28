package top.kzre.krro.d3.core.geometry;

/**
 * BMesh 角点——"一个面上、一条边上的一侧"。
 *
 * <p>这是径向边结构相比半边结构的关键补充：UV、顶点色、自定义法线
 * 等逐角点数据（同一顶点在不同面上取不同值）挂在 loop 上。
 *
 * <p><b>两条链表</b>：
 * <ul>
 *   <li>{@code next} / {@code prev}——面内环——沿面的边界遍历</li>
 *   <li>{@code radialNext} / {@code radialPrev}——边的径向环——
 *       沿一条边遍历它连接的所有面</li>
 * </ul>
 */
public final class BMLoop {

    /** loop 的起始顶点——沿面方向：{@code vert → next.vert}。 */
    BMVert vert;

    /** loop 所在的边。约定：{@code edge} 连接 {@code vert} 和 {@code next.vert}。 */
    BMEdge edge;

    /** loop 所属的面。 */
    BMFace face;

    /** 面内下一条 loop。 */
    BMLoop next;

    /** 面内上一条 loop。 */
    BMLoop prev;

    /** 径向环下一条——同一 {code edge} 的下一个 loop。 */
    BMLoop radialNext;

    /** 径向环上一条。 */
    BMLoop radialPrev;

    /** 索引。 */
    int index;

    // ── 逐角点数据（可选） ─────────────────────
    // MVP 阶段先内联最常用的——UV。更多数据通过扩展字段或外部
    // 数组挂载。

    /** UV——若此 mesh 带 UV。 */
    float u, v;

    // ── 公开访问器 ─────────────────────────────

    public BMVert vert()  { return vert; }
    public BMEdge edge()  { return edge; }
    public BMFace face()  { return face; }
    public BMLoop next()  { return next; }
    public BMLoop prev()  { return prev; }
    public BMLoop radialNext() { return radialNext; }
    public BMLoop radialPrev() { return radialPrev; }
    public int    index() { return index; }

    public float u() { return u; }
    public float v() { return v; }

    // ── 公开修改器 ─────────────────────────────

    public void setUV(float u, float v) {
        this.u = u;
        this.v = v;
    }

    /** 面内的下一个顶点——沿面方向前进一条边的另一端。 */
    public BMVert nextVert() {
        return next.vert;
    }

    /** 面内的前一个顶点。 */
    public BMVert prevVert() {
        return prev.vert;
    }

    @Override
    public String toString() {
        return "BMLoop#" + index
                + "(v=" + (vert != null ? vert.index : -1)
                + ", f=" + (face != null ? face.index : -1)
                + ")";
    }
}