package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.d3.core.util.LongIntMap;

/**
 * 面角→边查表——每个 corner 对应哪条边。
 *
 * <p><b>算法</b>：构建 {@code (v0,v1) → edgeIndex} 哈希表，逐 corner
 * 用 {@code (v_curr, v_next)} 查表。边端点按 {@code v0 < v1} 规范化为
 * 无向边 key。
 *
 * <p><b>输出</b>：{@code int[ncorners]}——每个 corner 对应的 edge 索引。
 * 退化面（{@code n < 2}）的 corner 写 {@code -1}——保持输出长度 = {@code ncorners}。
 *
 * <p><b>ThreadLocal</b>：内部 {@link LongIntMap} 走 ThreadLocal，
 * 避免每次分配。同一线程的并发调用会互相干扰——非线程安全。
 *
 * <p><b>直接写底层数组</b>：{@code out.getIntsForWrite()[out.intOffset() + w]}。
 */
public final class CornerEdgeLookupImpl implements CornerEdgeLookupAlgo {

    private static final ThreadLocal<LongIntMap> EDGE_MAP =
            ThreadLocal.withInitial(() -> new LongIntMap(1024));

    public CornerEdgeLookupImpl() {}

    @Override
    public void compute(CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteInts edgeEndpoints,
                        CopyOnWriteInts out) {
        if (cornerVerts == null)   throw new NullPointerException("cornerVerts");
        if (faceOffsets == null)   throw new NullPointerException("faceOffsets");
        if (edgeEndpoints == null) throw new NullPointerException("edgeEndpoints");
        if (out == null)           throw new NullPointerException("out");

        long edgeCount = edgeEndpoints.count();
        if (edgeCount % 2 != 0) {
            throw new IllegalArgumentException(
                    "edgeEndpoints count must be multiple of 2, got " + edgeCount);
        }
        long ncorners = cornerVerts.count();
        if (out.count() < ncorners) {
            throw new IllegalArgumentException(
                    "out needs " + ncorners + " ints, has " + out.count());
        }

        // ── 读 ──
        int[] cv   = cornerVerts.getIntsSnapshot();
        int   cvOff = (int) cornerVerts.intOffset();
        int[] fo   = faceOffsets.getIntsSnapshot();
        int   foOff = (int) faceOffsets.intOffset();
        int[] ee   = edgeEndpoints.getIntsSnapshot();
        int   eeOff = (int) edgeEndpoints.intOffset();
        int   nedges = (int) (edgeCount / 2);
        int   faceCount = (int) faceOffsets.count() - 1;

        // ── 构建 edgeMap——v0 < v1 规范化 ──
        LongIntMap edgeMap = EDGE_MAP.get();
        edgeMap.ensureCapacity(nedges);
        // TODO 过大的不保留，以及用户要保证总是在计算线程支持，不要随机线程执行

        for (int e = 0; e < nedges; e++) {
            int  v0  = ee[eeOff + e * 2];
            int  v1  = ee[eeOff + e * 2 + 1];
            int  a   = v0 < v1 ? v0 : v1;
            int  b   = v0 < v1 ? v1 : v0;
            long key = ((long) a << 32) | (b & 0xFFFFFFFFL);
            edgeMap.put(key, e);
        }

        // ── 写——先 ForWrite，再取 offset ──
        int[] o    = out.getIntsForWrite();
        int   oOff = (int) out.intOffset();

        int w = oOff;
        for (int f = 0; f < faceCount; f++) {
            int start = fo[foOff + f];
            int n     = fo[foOff + f + 1] - start;

            if (n < 2) {
                // 退化面——写 n 个 -1，保持输出长度
                for (int i = 0; i < n; i++) o[w++] = -1;
                continue;
            }

            for (int i = 0; i < n; i++) {
                int v0 = cv[cvOff + start + i];
                int v1 = cv[cvOff + start + (i + 1) % n];

                int  a   = v0 < v1 ? v0 : v1;
                int  b   = v0 < v1 ? v1 : v0;
                long key = ((long) a << 32) | (b & 0xFFFFFFFFL);

                o[w++] = edgeMap.get(key);
            }
        }

        long written = (long) w - oOff;
        if (written != out.count()) {
            throw new IllegalStateException(
                    "cornerEdges count mismatch: expected " + out.count()
                            + ", wrote " + written);
        }
    }
}