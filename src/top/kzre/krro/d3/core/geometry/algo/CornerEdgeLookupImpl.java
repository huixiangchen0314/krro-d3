package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.LongIntMap;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.IntArenaView;

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
 * <p><b>Session 访问</b>：通过 {@link IntArenaView.ArraySession} 访问
 * 底层数组与偏移。{@code out} 用 {@code arrayForWrite()}（写会话，
 * 可能触发 COW 复制）；输入用 {@code array()}（读会话，不触发复制）。
 * 空输入时不打开写会话。
 *
 * <p><b>ThreadLocal</b>：内部 {@link LongIntMap} 走 ThreadLocal，
 * 避免每次分配。同一线程的并发调用会互相干扰——非线程安全。
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

        try (IntArenaView.ArraySession cvSess = cornerVerts.array();
             IntArenaView.ArraySession foSess = faceOffsets.array();
             IntArenaView.ArraySession eeSess = edgeEndpoints.array()) {

            int edgeCount2 = eeSess.count();
            if (edgeCount2 % 2 != 0) {
                throw new IllegalArgumentException(
                        "edgeEndpoints count must be multiple of 2, got " + edgeCount2);
            }
            int ncorners = cvSess.count();
            if (ncorners == 0) return;

            // ── 读快照（会话生命周期内稳定）──
            int[] cv    = cvSess.getInts();
            int   cvOff = cvSess.offset();
            int[] fo    = foSess.getInts();
            int   foOff = foSess.offset();
            int[] ee    = eeSess.getInts();
            int   eeOff = eeSess.offset();
            int   nedges = edgeCount2 / 2;
            int   faceCount = foSess.count() - 1;

            // ── 写会话：容量校验 + 写 ──
            try (IntArenaView.ArraySession outSess = out.arrayForWrite()) {

                if (outSess.count() < ncorners) {
                    throw new IllegalArgumentException(
                            "out needs " + ncorners + " ints, has " + outSess.count());
                }

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

                int[] o    = outSess.getInts();
                int   oOff = outSess.offset();

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

                // 校验实际写入量 == 预期 corner 数。
                // 不强制 out 容量恰好相等——out 视图可能大于 ncorners。
                int written = w - oOff;
                if (written != ncorners) {
                    throw new IllegalStateException(
                            "cornerEdges count mismatch: expected " + ncorners
                                    + ", wrote " + written);
                }
            }
        }
    }
}