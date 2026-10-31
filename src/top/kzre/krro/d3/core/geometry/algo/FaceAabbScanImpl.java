package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * 面 AABB——逐面扫描其 corner 的顶点位置，求 min / max。
 *
 * <p><b>输出</b>：{@code float6[nfaces]}——每个 face
 * {@code [minX, minY, minZ, maxX, maxY, maxZ]}。
 *
 * <p><b>直接写底层数组</b>：{@code out.getFloatsForWrite()[out.floatOffset() + w]}，
 * 不经过 {@code FloatBuffer}，不碰 {@code ArenaView}。
 *
 * <p><b>退化面</b>（{@code start >= end}）：写入
 * {@code [+inf, +inf, +inf, -inf, -inf, -inf]}——表示空包围盒，
 * 保持输出长度 = {@code nfaces * 6}。
 *
 * <p><b>线程契约</b>：只读 {@code positions} / {@code cornerVerts} / {@code faceOffsets}，
 * 写 {@code out}。任意线程可调。
 */
public final class FaceAabbScanImpl implements FaceAabbAlgo {

    public FaceAabbScanImpl() {}

    // TODO 直接查 边角->边缓存
    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteFloats out) {
        if (positions == null)   throw new NullPointerException("positions");
        if (cornerVerts == null) throw new NullPointerException("cornerVerts");
        if (faceOffsets == null) throw new NullPointerException("faceOffsets");
        if (out == null)         throw new NullPointerException("out");

        long posCount = positions.count();
        if (posCount % 3 != 0) {
            throw new IllegalArgumentException(
                    "positions count must be multiple of 3, got " + posCount);
        }
        if (faceOffsets.count() < 1) {
            throw new IllegalArgumentException(
                    "faceOffsets count must be >= 1, got " + faceOffsets.count());
        }

        int[] fo    = faceOffsets.getIntsSnapshot();
        int   foOff = (int) faceOffsets.intOffset();
        int   faceCount = (int) faceOffsets.count() - 1;

        // 容量校验——必须在 ForWrite 之前
        long needed = (long) faceCount * 6L;
        if (out.count() < needed) {
            throw new IllegalArgumentException(
                    "out needs " + needed + " floats, has " + out.count());
        }

        // ── 读 ──
        float[] pos = positions.getFloatsSnapshot();
        int     pOff = (int) positions.floatOffset();
        int[]   cv   = cornerVerts.getIntsSnapshot();
        int     cvOff = (int) cornerVerts.intOffset();

        // ── 写——先 ForWrite，再取 offset ──
        float[] o    = out.getFloatsForWrite();
        int     oOff = (int) out.floatOffset();

        int w = oOff;
        for (int f = 0; f < faceCount; f++) {
            int start = fo[foOff + f];
            int end   = fo[foOff + f + 1];

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

        long written = (long) w - oOff;
        if (written != out.count()) {
            throw new IllegalStateException(
                    "faceAabbs count mismatch: expected " + out.count()
                            + ", wrote " + written);
        }
    }
}