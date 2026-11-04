package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.FloatArenaView;
import top.kzre.krro.util.arena.IntArenaView;

/**
 * 面角位置 gather——把 per-vertex 的 positions 重排为 per-corner 的
 * cornerPositions。
 *
 * <p><b>算法</b>：{@code cornerPositions[c] = positions[cornerVerts[c]]}。
 *
 * <p><b>Session 访问</b>：通过 {@link FloatArenaView.ArraySession} /
 * {@link IntArenaView.ArraySession} 访问底层数组与偏移——
 * 不直接碰 {@code ArenaView}，会话生命周期由 try-with-resources 保证。
 *
 * <p><b>读 vs 写</b>：{@code positions} / {@code cornerVerts} 用
 * {@code array()}（读会话）；{@code out} 用 {@code arrayForWrite()}
 * （写会话）——后者可能触发 COW 复制，写入反映到正确的新视图。
 *
 * <p><b>线程契约</b>：只读 {@code positions} / {@code cornerVerts}，
 * 写 {@code out}。任意线程可调。
 */
public final class CornerPositionGatherImpl implements CornerPositionGatherAlgo {

    public CornerPositionGatherImpl() {}

    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteFloats out) {
        if (positions == null)   throw new NullPointerException("positions");
        if (cornerVerts == null) throw new NullPointerException("cornerVerts");
        if (out == null)         throw new NullPointerException("out");

        // 三个会话一次性打开——close 顺序由 try-with-resources 逆序保证
        try (FloatArenaView.ArraySession posSess = positions.array();
             IntArenaView.ArraySession  cvSess   = cornerVerts.array();
             FloatArenaView.ArraySession outSess = out.arrayForWrite()) {

            // ── 前置校验 ──
            int posCount = posSess.count();
            if (posCount % 3 != 0) {
                throw new IllegalArgumentException(
                        "positions count must be multiple of 3, got " + posCount);
            }

            int ncorners = cvSess.count();

            int needed = ncorners * 3;
            if (outSess.count() < needed) {
                throw new IllegalArgumentException(
                        "out needs " + needed + " floats, has " + outSess.count());
            }

            // ── 取数组与元素偏移（快照在会话生命周期内稳定）──
            float[] pos    = posSess.getFloats();
            int     posOff = posSess.offset();
            int[]   cv     = cvSess.getInts();
            int     cvOff  = cvSess.offset();
            float[] o      = outSess.getFloats();
            int     oOff   = outSess.offset();

            // ── gather ──
            for (int c = 0; c < ncorners; c++) {
                int v  = cv[cvOff + c];
                int pb = posOff + v * 3;
                int cb = oOff + c * 3;
                o[cb]     = pos[pb];
                o[cb + 1] = pos[pb + 1];
                o[cb + 2] = pos[pb + 2];
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}