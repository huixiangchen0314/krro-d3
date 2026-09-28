package top.kzre.krro.d3.core.geometry;

/**
 * BMesh 面。由一圈 loop 组成——首尾相接的边界。
 *
 * <p>{@code loop} 是任意一条起点的 loop。从它沿 {@code next} 遍历
 * 一圈回到它，就是整个面的边界。
 *
 * <p>{@code len} 缓存环长——避免每次遍历。
 *
 * <p>法线在编辑操作后可能失效——上层按需重算。这里只存字段，
 * 不做自动维护。
 */
public final class BMFace {

    /** 环的入口——任一条 loop。 */
    BMLoop loop;

    /** 环长（顶点数）。 */
    int len;

    /** 法线——可能无效，由上层维护。 */
    float nx, ny, nz;

    /** 标志位。 */
    int flags;

    /** 索引。 */
    int index;

    // ── 公开访问器 ─────────────────────────────

    public BMLoop loop() { return loop; }
    public int    len()  { return len; }
    public float  nx()   { return nx; }
    public float  ny()   { return ny; }
    public float  nz()   { return nz; }
    public int    index() { return index; }
    public int    flags() { return flags; }

    // ── 公开修改器 ─────────────────────────────

    public void setNormal(float nx, float ny, float nz) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
    }

    public void setFlags(int f) { this.flags = f; }

    /** 遍历面的所有 loop。回调形式——避免分配迭代器。 */
    public void forEachLoop(java.util.function.Consumer<BMLoop> fn) {
        if (loop == null) return;
        BMLoop l = loop;
        do {
            fn.accept(l);
            l = l.next;
        } while (l != loop);
    }

    @Override
    public String toString() {
        return "BMFace#" + index + "(len=" + len + ")";
    }
}