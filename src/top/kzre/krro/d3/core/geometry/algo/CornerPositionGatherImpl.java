package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * 面角位置 gather——把 per-vertex 的 positions 重排为 per-corner 的
 * cornerPositions。
 *
 * <p><b>算法</b>：{@code cornerPositions[c] = positions[cornerVerts[c]]}。
 *
 * <p><b>直接写底层数组</b>：{@code out.getFloatsForWrite()[out.floatOffset() + i]}，
 * 不经过 {@code FloatBuffer}，不碰 {@code ArenaView}。
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

        long posCount = positions.count();
        if (posCount % 3 != 0) {
            throw new IllegalArgumentException(
                    "positions count must be multiple of 3, got " + posCount);
        }

        long ncorners = cornerVerts.count();
        long needed   = ncorners * 3L;
        if (out.count() < needed) {
            throw new IllegalArgumentException(
                    "out needs " + needed + " floats, has " + out.count());
        }

        // ── 读 ──
        float[] pos    = positions.getFloatsSnapshot();
        int     posOff = (int) positions.floatOffset();
        int[]   cv     = cornerVerts.getIntsSnapshot();
        int     cvOff  = (int) cornerVerts.intOffset();

        // ── 写——先 ForWrite，再取 offset ──
        float[] o    = out.getFloatsForWrite();
        int     oOff = (int) out.floatOffset();

        for (int c = 0; c < ncorners; c++) {
            int v  = cv[cvOff + c];
            int pb = posOff + v * 3;
            int cb = oOff + c * 3;
            o[cb]     = pos[pb];
            o[cb + 1] = pos[pb + 1];
            o[cb + 2] = pos[pb + 2];
        }
    }
}