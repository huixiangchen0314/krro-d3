package top.kzre.krro.d3.core.geometry;

/**
 * BMesh 顶点。
 *
 * <p>{@code edge} 是"一条出边"——任选一条以本顶点为端点的边。用于
 * 从顶点出发遍历它的所有邻接边 / 面。
 *
 * <p><b>纯数据</b>：只承载几何与拓扑。选择、可见、临时标记等编辑器
 * 状态由外部（Clojure 层）持有——不入侵本类。烘焙时的索引由烘焙器
 * 内部临时分配——不持久化在此。
 *
 * <p>字段包内可见——同包的操作类直接读写。外部通过本类的公开访问器。
 */
public final class BMVert {

    /** 位置。 */
    float x, y, z;

    /** 一条出边。{@code null} 表示孤立顶点。 */
    BMEdge edge;

    // ── 公开访问器（给 Clojure / 外部）─────────

    public float  x() { return x; }
    public float  y() { return y; }
    public float  z() { return z; }
    public BMEdge edge() { return edge; }

    // ── 公开修改器 ─────────────────────────────

    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public String toString() {
        return "BMVert(" + x + ", " + y + ", " + z + ")";
    }
}