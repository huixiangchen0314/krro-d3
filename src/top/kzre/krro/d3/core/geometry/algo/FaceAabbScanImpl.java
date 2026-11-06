package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.FloatArenaView;
import top.kzre.krro.util.arena.IntArenaView;

/**
 * 面 AABB——逐面扫描其 corner 的顶点位置，求 min / max。
 *
 * <p><b>输出</b>：{@code float6[nfaces]}——每个 face
 * {@code [minX, minY, minZ, maxX, maxY, maxZ]}。
 *
 * <p><b>Session 访问</b>：通过 {@link FloatArenaView.ArraySession} /
 * {@link IntArenaView.ArraySession} 访问底层数组与偏移——会话生命周期
 * 由 try-with-resources 保证。{@code out} 用 {@code arrayForWrite()}
 * （写会话，可能触发 COW 复制）；输入用 {@code array()}（读会话，不触发复制）。
 *
 * <p><b>退化面</b>（{@code start >= end}）：写入
 * {@code [+inf, +inf, +inf, -inf, -inf, -inf]}——表示空包围盒，
 * 保持输出长度 = {@code nfaces * 6}。
 *
 * <p><b>线程契约</b>：只读 {@code positions} / {@code cornerVerts} / {@code faceOffsets}，
 * 写 {@code out}。任意线程可调。
 */
public final class FaceAabbScanImpl implements FaceAabbAlgo {

    public FaceAabbScanImpl() {
    }

    // TODO 直接查 边角->边缓存
    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteFloats out) {
        if (positions == null) throw new NullPointerException("positions");
        if (cornerVerts == null) throw new NullPointerException("cornerVerts");
        if (faceOffsets == null) throw new NullPointerException("faceOffsets");
        if (out == null) throw new NullPointerException("out");

        try (FloatArenaView.ArraySession posSess = positions.array();
             IntArenaView.ArraySession cvSess = cornerVerts.array();
             IntArenaView.ArraySession foSess = faceOffsets.array()) {

            int posCount = posSess.count();
            if (posCount % 3 != 0) {
                throw new IllegalArgumentException(
                        "positions count must be multiple of 3, got " + posCount);
            }
            int foCount = foSess.count();
            if (foCount < 1) {
                throw new IllegalArgumentException(
                        "faceOffsets count must be >= 1, got " + foCount);
            }
            int faceCount = foCount - 1;

            long needed = (long) faceCount * 6L;

            // ── 读快照（会话生命周期内稳定）──
            int[] fo = foSess.getInts();
            int foOff = foSess.offset();

            float[] pos = posSess.getFloats();
            int pOff = posSess.offset();
            int[] cv = cvSess.getInts();
            int cvOff = cvSess.offset();

            // ── 写会话：容量校验 + 写 ──
            try (FloatArenaView.ArraySession outSess = out.arrayForWrite()) {
                if (outSess.count() < needed) {
                    throw new IllegalArgumentException(
                            "out needs " + needed + " floats, has " + outSess.count());
                }

                float[] o = outSess.getFloats();
                int oOff = outSess.offset();

                int w = oOff;
                for (int f = 0; f < faceCount; f++) {
                    int start = fo[foOff + f];
                    int end = fo[foOff + f + 1];

                    if (start >= end) {
                        o[w++] = Float.POSITIVE_INFINITY;
                        o[w++] = Float.POSITIVE_INFINITY;
                        o[w++] = Float.POSITIVE_INFINITY;
                        o[w++] = Float.NEGATIVE_INFINITY;
                        o[w++] = Float.NEGATIVE_INFINITY;
                        o[w++] = Float.NEGATIVE_INFINITY;
                        continue;
                    }

                    int v0 = cv[cvOff + start];
                    int b0 = pOff + v0 * 3;

                    float mnx = pos[b0];
                    float mny = pos[b0 + 1];
                    float mnz = pos[b0 + 2];
                    float mxx = mnx;
                    float mxy = mny;
                    float mxz = mnz;

                    for (int c = start + 1; c < end; c++) {
                        int v = cv[cvOff + c];
                        int b = pOff + v * 3;

                        float x = pos[b];
                        float y = pos[b + 1];
                        float z = pos[b + 2];

                        if (x < mnx) mnx = x;
                        if (y < mny) mny = y;
                        if (z < mnz) mnz = z;
                        if (x > mxx) mxx = x;
                        if (y > mxy) mxy = y;
                        if (z > mxz) mxz = z;
                    }

                    o[w++] = mnx;
                    o[w++] = mny;
                    o[w++] = mnz;
                    o[w++] = mxx;
                    o[w++] = mxy;
                    o[w++] = mxz;
                }

                // out 可能大于 needed（视图容量大于需求）——只校验写入量不超过容量，
                // 不强制等于 count。原实现要求 written == out.count() 是过度约束：
                // out 的视图可以比 faceCount * 6 大，多余部分不该视为错误。
                long written = (long) w - oOff;
                if (written > outSess.count()) {
                    throw new IllegalStateException(
                            "faceAabbs overflow: wrote " + written
                                    + ", capacity " + outSess.count());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}