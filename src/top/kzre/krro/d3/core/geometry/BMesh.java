package top.kzre.krro.d3.core.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑内核——径向边结构（radial edge mesh）。
 *
 * <p><b>用途</b>：拓扑操作的临时表示。用户进入编辑模式时，从
 * {@code Mesh}（deflayout 数组）构建 BMesh；编辑操作（挤出、切分、
 * 倒角……）在 BMesh 上执行；退出或提交时烘焙回 Mesh。
 *
 * <p><b>不在持久化路径上</b>：BMesh 只存在于编辑会话内——单线程
 * 持有、不进序列化、不进 GPU、不需要 COW。
 *
 * <p><b>结构</b>：四个实体通过引用互联——
 * <ul>
 *   <li>{@link BMVert} —— 顶点</li>
 *   <li>{@link BMEdge} —— 边（两个顶点）</li>
 *   <li>{@link BMLoop} —— 角点（一条边上、属于一个面的一侧）</li>
 *   <li>{@link BMFace} —— 面（由一圈 loop 组成）</li>
 * </ul>
 *
 * <p><b>径向环</b>：每条边周围的所有 loop 通过 {@code radialNext} /
 * {@code radialPrev} 组成环形双向链表。允许非流形（一条边连接 3+
 * 面）——这是径向边结构相比半边结构的核心优势。
 *
 * <p><b>面的环</b>：每个面的所有 loop 通过 {@code next} / {@code prev}
 * 组成环形双向链表。
 *
 * <p><b>线程契约</b>：非线程安全。编辑会话单线程持有。
 *
 * <h2>与 Mesh 的关系</h2>
 *
 * <p>{@code Mesh} 是持久化 / 渲染的紧凑数组表示。{@code BMesh} 是
 * 编辑期的对象图表示。两者通过构建 / 烘焙互转——转换方向可逆，
 * 但结构完全不同：
 * <ul>
 *   <li>Mesh —— 数组、deflayout、SoA 风格、跨会话持久</li>
 *   <li>BMesh —— 对象、Java 引用、图结构、会话级临时</li>
 * </ul>
 */
public final class BMesh {

    /** 所有顶点。顺序即创建顺序——烘焙回 Mesh 时用作顶点索引。 */
    private final List<BMVert> verts = new ArrayList<>();

    /** 所有边。 */
    private final List<BMEdge> edges = new ArrayList<>();

    /** 所有角点。 */
    private final List<BMLoop> loops = new ArrayList<>();

    /** 所有面。 */
    private final List<BMFace> faces = new ArrayList<>();

    // ═══════════════════════════════════════════════
    // 工厂
    // ═══════════════════════════════════════════════

    /**
     * 新建一个孤立顶点。不关联任何边或面。
     */
    public BMVert addVert(float x, float y, float z) {
        BMVert v = new BMVert();
        v.x = x;
        v.y = y;
        v.z = z;
        v.index = verts.size();
        verts.add(v);
        return v;
    }

    /**
     * 新建一条边。两个顶点不同、且这条边尚不存在。
     *
     * <p>同时更新两个顶点的 {@code edge} 指针——如果它们还没有出边，
     * 或当前出边指向的地方不合适——这里简单处理：仅当顶点无出边时设置。
     * 邻接关系的精确维护由上层操作负责。
     */
    public BMEdge addEdge(BMVert v0, BMVert v1) {
        if (v0 == v1) {
            throw new IllegalArgumentException("self-loop not allowed");
        }
        BMEdge e = new BMEdge();
        e.v0 = v0;
        e.v1 = v1;
        e.index = edges.size();
        edges.add(e);

        // 顶点的出边指针——若未设置则指向本边
        if (v0.edge == null) v0.edge = e;
        if (v1.edge == null) v1.edge = e;

        return e;
    }

    /**
     * 新建一个面。顶点数组必须构成一个简单环——每对相邻顶点之间
     * 自动建边（若不存在）。
     *
     * <p>面的 loop 顺序与传入的顶点顺序一致，方向由顶点顺序决定。
     *
     * @param vs 至少 3 个顶点，连续顶点之间建边
     */
    public BMFace addFace(BMVert... vs) {
        if (vs == null || vs.length < 3) {
            throw new IllegalArgumentException("face needs >= 3 verts");
        }

        BMFace f = new BMFace();
        f.len = vs.length;
        f.index = faces.size();

        // 为每条边创建 loop，并串成面内环
        BMLoop first = null;
        BMLoop prevLoop = null;
        for (int i = 0; i < vs.length; i++) {
            BMVert a = vs[i];
            BMVert b = vs[(i + 1) % vs.length];

            BMEdge e = findEdge(a, b);
            if (e == null) e = addEdge(a, b);

            BMLoop loop = new BMLoop();
            loop.vert = a;
            loop.edge = e;
            loop.face = f;
            loop.index = loops.size();
            loops.add(loop);

            if (first == null) first = loop;
            if (prevLoop != null) {
                prevLoop.next = loop;
                loop.prev = prevLoop;
            }
            prevLoop = loop;

            // 挂到边的径向环——简单追加到链头
            attachRadial(e, loop);
        }
        // 收尾：闭环
        prevLoop.next = first;
        first.prev = prevLoop;

        f.loop = first;
        faces.add(f);
        return f;
    }

    // ═══════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════

    /**
     * 查找一条边。O(deg) —— 遍历 v0 的出边径向环。
     *
     * <p>如果性能敏感，上层可缓存邻接表。
     */
    public BMEdge findEdge(BMVert v0, BMVert v1) {
        BMEdge start = v0.edge;
        if (start == null) return null;

        BMEdge e = start;
        do {
            if (e.has(v1)) return e;

            // disk cycle：找 e 上"以 v0 为起点"的 loop
            BMLoop l = loopOf(e, v0);
            if (l == null) break;

            // 沿径向环切换到 v0 的另一侧——落到 e 的邻居 loop
            // 约定：loop.radialNext 是"同边的下一条 loop"，且它的起点
            // 通常是径向翻转后的 v0（另一侧）
            BMLoop r = l.radialNext;
            if (r == l) break;              // 边界边：v0 只有 e 这一条出边
            e = r.edge;
        } while (e != start);

        return null;
    }

    /** 取边 e 上以 v 为起点的 loop；没有返回 null。 */
    private static BMLoop loopOf(BMEdge e, BMVert v) {
        if (e.loop == null) return null;
        BMLoop l = e.loop;
        do {
            if (l.vert == v) return l;
            l = l.radialNext;
        } while (l != e.loop);
        return null;
    }

    /** 顶点总数。 */
    public int vertCount() { return verts.size(); }

    /** 边总数。 */
    public int edgeCount() { return edges.size(); }

    /** 面总数。 */
    public int faceCount() { return faces.size(); }

    /** 角点总数。 */
    public int loopCount() { return loops.size(); }

    // ═══════════════════════════════════════════════
    // 遍历（只读）
    // ═══════════════════════════════════════════════

    /** 顶点列表——只读访问。顺序即创建顺序。 */
    public List<BMVert> verts() { return verts; }

    /** 边列表——只读访问。 */
    public List<BMEdge> edges() { return edges; }

    /** 角点列表——只读访问。 */
    public List<BMLoop> loops() { return loops; }

    /** 面列表——只读访问。 */
    public List<BMFace> faces() { return faces; }

    // ═══════════════════════════════════════════════
    // 内部
    // ═══════════════════════════════════════════════

    /**
     * 把一条新 loop 挂到边的径向环。
     *
     * <p>如果边还没有 loop——新 loop 自成一个只有一项的环。
     * 否则——插入到链尾。
     */
    private static void attachRadial(BMEdge e, BMLoop newLoop) {
        if (e.loop == null) {
            e.loop = newLoop;
            newLoop.radialNext = newLoop;
            newLoop.radialPrev = newLoop;
        } else {
            BMLoop head = e.loop;
            BMLoop tail = head.radialPrev;

            tail.radialNext = newLoop;
            newLoop.radialPrev = tail;
            newLoop.radialNext = head;
            head.radialPrev = newLoop;
        }
    }
}