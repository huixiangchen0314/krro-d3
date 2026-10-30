package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * 贝塞尔曲线定义——本质数据。
 *
 * <p>存储形式：控制点 + 手柄 + 连续性约束。
 * 不含缓存数据（采样点、切线、包围盒等由 {@code BezierAsset} 层组合）。
 *
 * <p><b>数据约束</b>（由构造方保证）：
 * <ul>
 *   <li>{@code points.length}       = N * 3</li>
 *   <li>{@code handles.length}      = N * 6，每点两个手柄 (dx1,dy1,dz1,dx2,dy2,dz2)</li>
 *   <li>{@code continuities.length} = N，每点一个连续性标记</li>
 * </ul>
 *
 * <p><b>连续性</b>：{@code continuities} 是意图，{@code handles} 是当前状态。
 * 二者不强制同步——上层负责在合适的时机应用约束。
 */
public final class Bezier implements CopyOnWrite<Bezier> {

    /** 每个控制点的 x,y,z 坐标。长度 = N * 3。 */
    private final CopyOnWriteFloats points;

    /** 每个控制点的两个手柄 (dx1,dy1,dz1, dx2,dy2,dz2)。长度 = N * 6。 */
    private final CopyOnWriteFloats handles;

    /**
     * 每个控制点的连续性约束。
     *
     * <p>不一定立刻反映到 {@code handles}——上层看着同步。
     * 长度 = N。
     */
    private final CopyOnWriteInts continuities;

    /** 是否闭合。 */
    private final boolean closed;

    public Bezier(CopyOnWriteFloats points,
                  CopyOnWriteFloats handles,
                  CopyOnWriteInts continuities,
                  boolean           closed) {
        this.points       = points;
        this.handles      = handles;
        this.continuities = continuities;
        this.closed       = closed;
    }

    // ═══════════════════════════════════════════════
    // 访问
    // ═══════════════════════════════════════════════

    public CopyOnWriteFloats points()       { return points; }
    public CopyOnWriteFloats handles()      { return handles; }
    public CopyOnWriteInts continuities() { return continuities; }
    public boolean           closed()       { return closed; }

    // ═══════════════════════════════════════════════
    // COW
    // ═══════════════════════════════════════════════

    @Override
    public Bezier shared() {
        return new Bezier(
                points.shared(),
                handles.shared(),
                continuities.shared(),
                closed);
    }

    @Override
    public void close() throws Exception {
        RuntimeException ex = ResourceUtils.closeDelayError(points, handles, continuities);
        if (ex != null) throw ex;
    }
}