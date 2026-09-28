package top.kzre.krro.d3.core.geometry;

/**
 * BMesh 顶点。
 *
 * <p>{@code edge} 是"一条出边"——任选一条以本顶点为端点的边。用于
 * 从顶点出发遍历它的所有邻接边 / 面。
 *
 * <p>字段包内可见——同包的操作类直接读写。外部通过 {@link BMesh}
 * 的接口访问。
 */
public final class BMVert {

    /** 位置。 */
    float x, y, z;

    /** 一条出边。{@code null} 表示孤立顶点。 */
    BMEdge edge;

    /** 标志位——选择、可见、临时标记等。语义由上层定义。 */
    int flags;

    /** 索引。构建时按创建顺序赋值；烘焙回 Mesh 时用作顶点下标。 */
    int index;

    // ── 公开访问器（给 Clojure / 外部）─────────

    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
    public int   index() { return index; }
    public int   flags() { return flags; }
    public BMEdge edge() { return edge; }

    // ── 公开修改器 ─────────────────────────────

    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setFlags(int f) { this.flags = f; }

    public void orFlags(int f)  { this.flags |= f; }
    public void andFlags(int f) { this.flags &= f; }

    @Override
    public String toString() {
        return "BMVert#" + index + "(" + x + ", " + y + ", " + z + ")";
    }
}