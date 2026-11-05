package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.algo.predicate.BruteForceEarTest;
import top.kzre.krro.d3.core.geometry.algo.predicate.EarTester;
import top.kzre.krro.d3.core.geometry.algo.predicate.EarTestStrategy;
import top.kzre.krro.d3.core.geometry.algo.predicate.GeometryPredicates;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.FloatArenaView;
import top.kzre.krro.util.arena.IntArenaView;
import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.pool.FloatsHolder;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.PoolManagers;

import java.util.stream.IntStream;

/**
 * 几何边法向三角化——UV 无关，只依赖 3D 位置。
 *
 * <p><b>适用场景</b>：UV 为 null 时的候选三角化策略，与
 * {@link MikkTriangulationImpl} 互为替代。调用方按"是否有 UV"
 * 二选一：有 UV 走 Mikk（最短对角线 + UV 兜底），无 UV 走本策略
 * （三角形法向一致性）。
 *
 * <p><b>规则</b>：
 * <ul>
 *   <li>{@code n == 3}：直接输出 {@code [c0, c1, c2]}</li>
 *   <li>{@code n == 4}：比较两条对角线产生的两个三角形的<b>法向一致性</b>——
 *       叉积得到每侧三角形法向，归一化后取点积，选点积更大（更同向、
 *       折叠更少）的对角线</li>
 *   <li>{@code n > 4}：ear clipping——与 {@link MikkTriangulationImpl} 一致</li>
 * </ul>
 *
 * <p><b>池化</b>：叉积计算使用 {@link KMath#cross(float[], float[], float[])}，
 * 三个 vec3 缓冲区（edge1 / edge2 / normal）全部走全局 {@link FloatsPool}——
 * 稳态零分配。每个 quad 计算期间 acquire 4 个（两个三角形法向需要
 * n1 / n2 同时存在），try-finally 释放。<b>依赖 {@link FloatsPool}
 * 自身的线程安全</b>——并行分支中多个 task 会并发 acquire / release。
 *
 * <p><b>输出</b>：corner 索引，{@code int[ntri * 3]}。与 Mikk 完全相同的输出契约。
 */
public final class GeometryEdgeTangentsImpl implements TriangulationAlgo {

    /** 并行阈值——faceCount 低于此值串行，避免并行开销。 */
    private static final int PARALLEL_THRESHOLD = 4096;

    /** 法向模长平方低于此值视为退化。 */
    private static final float NORMAL_EPS = 1e-12f;

    /** 池——static final，与 BinnedSelect 一致的用法。 */
    private static final FloatsHolder FLOATS     = PoolManagers.floats().getHolder();
    private static final FloatsPool   VEC3_POOL  = FLOATS.getPool(3);

    private final EarTestStrategy earTest;

    public GeometryEdgeTangentsImpl() {
        this(new BruteForceEarTest());
    }

    public GeometryEdgeTangentsImpl(EarTestStrategy earTest) {
        if (earTest == null) throw new NullPointerException("earTest");
        this.earTest = earTest;
    }

    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteFloats uv,
                        CopyOnWriteInts out) {
        if (positions == null)   throw new NullPointerException("positions");
        if (cornerVerts == null) throw new NullPointerException("cornerVerts");
        if (faceOffsets == null) throw new NullPointerException("faceOffsets");
        if (out == null)         throw new NullPointerException("out");
        // uv 在本策略中刻意忽略——不打开会话，不读取

        try (FloatArenaView.ArraySession posSess = positions.array();
             IntArenaView.ArraySession  cvSess  = cornerVerts.array();
             IntArenaView.ArraySession  foSess  = faceOffsets.array()) {

            if (posSess.count() % 3 != 0) {
                throw new IllegalArgumentException(
                        "positions count must be multiple of 3, got " + posSess.count());
            }

            // ── 读快照 + 前缀和 ──
            int[] fo    = foSess.getInts();
            int   foOff = foSess.offset();
            int   faceCount = foSess.count() - 1;

            int[] faceTriStart = new int[faceCount + 1];
            for (int f = 0; f < faceCount; f++) {
                int n = fo[foOff + f + 1] - fo[foOff + f];
                faceTriStart[f + 1] = faceTriStart[f] + (n >= 3 ? n - 2 : 0);
            }
            int triCount = faceTriStart[faceCount];

            // ── 写会话：容量校验 + 写 ──
            try (IntArenaView.ArraySession outSess = out.arrayForWrite()) {
                if ((long) triCount * 3L != outSess.count()) {
                    throw new IllegalStateException(
                            "triangulation count mismatch: expected " + outSess.count()
                                    + ", prefix sum gives " + ((long) triCount * 3L));
                }

                float[] pos   = posSess.getFloats();
                int     pOff  = posSess.offset();
                int[]   cv    = cvSess.getInts();
                int     cvOff = cvSess.offset();
                int[]   o     = outSess.getInts();
                int     oOff  = outSess.offset();

                fill(pos, pOff, cv, cvOff, fo, foOff,
                        o, oOff, faceCount, faceTriStart);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void fill(float[] pos, int pOff,
                      int[] cv, int cvOff,
                      int[] fo, int foOff,
                      int[] o, int oOff,
                      int faceCount, int[] faceTriStart) {
        if (faceCount == 0) return;

        if (faceCount < PARALLEL_THRESHOLD) {
            for (int f = 0; f < faceCount; f++) {
                int start = fo[foOff + f];
                int n     = fo[foOff + f + 1] - start;
                if (n < 3) continue;
                triangulateFace(start, n, pos, pOff, cv, cvOff,
                        o, oOff + faceTriStart[f] * 3);
            }
        } else {
            IntStream.range(0, faceCount).parallel().forEach(f -> {
                int start = fo[foOff + f];
                int n     = fo[foOff + f + 1] - start;
                if (n < 3) return;
                triangulateFace(start, n, pos, pOff, cv, cvOff,
                        o, oOff + faceTriStart[f] * 3);
            });
        }
    }

    // ── per-face 分发 ──

    private void triangulateFace(int start, int n,
                                 float[] pos, int pOff,
                                 int[] cv, int cvOff,
                                 int[] o, int w) {
        if (n == 3) {
            o[w++] = start;
            o[w++] = start + 1;
            o[w++] = start + 2;
        } else if (n == 4) {
            triangulateQuad(start, pos, pOff, cv, cvOff, o, w);
        } else {
            earClip(start, n, pos, pOff, cv, cvOff, o, w);
        }
    }

    // ═══════════════════════════════════════════════
    // quad——按三角形法向一致性选择对角线
    // ═══════════════════════════════════════════════

    /**
     * 选择使两个三角形法向更同向的对角线。
     *
     * <p>池化：acquire 4 个 vec3（e1, e2 用于两条边的临时向量，
     * n1, n2 用于两侧三角形法向），全部计算完成后 finally 释放。
     * 一次 acquire 覆盖 AC 和 BD 两条对角线的全部叉积——无 per-face
     * 的中间分配。
     */
    private static void triangulateQuad(int start,
                                        float[] pos, int pOff,
                                        int[] cv, int cvOff,
                                        int[] o, int w) {
        int c0 = start, c1 = start + 1, c2 = start + 2, c3 = start + 3;
        int b0 = pOff + cv[cvOff + c0] * 3;
        int b1 = pOff + cv[cvOff + c1] * 3;
        int b2 = pOff + cv[cvOff + c2] * 3;
        int b3 = pOff + cv[cvOff + c3] * 3;

        float[] e1 = VEC3_POOL.acquire();
        float[] e2 = VEC3_POOL.acquire();
        float[] n1 = VEC3_POOL.acquire();
        float[] n2 = VEC3_POOL.acquire();
        float alignAC, alignBD;
        try {
            // 对角线 AC（c0-c2）→ (c0,c1,c2) + (c0,c2,c3)
            alignAC = alignmentOf(pos, b0, b1, b2, b0, b2, b3, e1, e2, n1, n2);
            // 对角线 BD（c1-c3）→ (c1,c2,c3) + (c1,c3,c0)
            alignBD = alignmentOf(pos, b1, b2, b3, b1, b3, b0, e1, e2, n1, n2);
        } finally {
            VEC3_POOL.release(e1);
            VEC3_POOL.release(e2);
            VEC3_POOL.release(n1);
            VEC3_POOL.release(n2);
        }

        boolean acValid = alignAC > Float.NEGATIVE_INFINITY;
        boolean bdValid = alignBD > Float.NEGATIVE_INFINITY;

        boolean useAC;
        if (!acValid && !bdValid) {
            float d02 = GeometryPredicates.squaredDist3(pos, b0, b2);
            float d13 = GeometryPredicates.squaredDist3(pos, b1, b3);
            useAC = d02 <= d13;
        } else if (!acValid) {
            useAC = false;
        } else if (!bdValid) {
            useAC = true;
        } else {
            useAC = alignAC >= alignBD;
        }

        if (useAC) {
            o[w++] = c0; o[w++] = c1; o[w++] = c2;
            o[w++] = c0; o[w++] = c2; o[w++] = c3;
        } else {
            o[w++] = c1; o[w++] = c2; o[w++] = c3;
            o[w++] = c1; o[w++] = c3; o[w++] = c0;
        }
    }

    /**
     * 计算两个三角形法向的归一化点积。
     *
     * <p>三角形 1 = {@code (p0, p1, p2)}，三角形 2 = {@code (q0, q1, q2)}，
     * 参数均为 {@code pos} 数组中的元素偏移（已含 {@code pOff} 与
     * 顶点索引乘 3）。
     *
     * <p>交叉计算用 {@link KMath#cross(float[], float[], float[])}——
     * 输入 edge 向量由本方法填入 {@code e1/e2}，输出法向写入 {@code n1/n2}。
     * 复用 4 个外部缓冲，无分配。
     *
     * @return 两法向归一化点积；任一模长平方 &lt; {@link #NORMAL_EPS}
     *         时返回 {@link Float#NEGATIVE_INFINITY} 表示该侧退化
     */
    private static float alignmentOf(float[] pos,
                                     int p0, int p1, int p2,
                                     int q0, int q1, int q2,
                                     float[] e1, float[] e2,
                                     float[] n1, float[] n2) {
        // n1 = (P1 - P0) × (P2 - P0)
        e1[0] = pos[p1]     - pos[p0];
        e1[1] = pos[p1 + 1] - pos[p0 + 1];
        e1[2] = pos[p1 + 2] - pos[p0 + 2];
        e2[0] = pos[p2]     - pos[p0];
        e2[1] = pos[p2 + 1] - pos[p0 + 1];
        e2[2] = pos[p2 + 2] - pos[p0 + 2];
        KMath.cross(e1, e2, n1);

        // n2 = (Q1 - Q0) × (Q2 - Q0)
        e1[0] = pos[q1]     - pos[q0];
        e1[1] = pos[q1 + 1] - pos[q0 + 1];
        e1[2] = pos[q1 + 2] - pos[q0 + 2];
        e2[0] = pos[q2]     - pos[q0];
        e2[1] = pos[q2 + 1] - pos[q0 + 1];
        e2[2] = pos[q2 + 2] - pos[q0 + 2];
        KMath.cross(e1, e2, n2);

        return normalAlignment(n1, n2);
    }

    /**
     * 两个法向的归一化点积。
     *
     * @return 点积 / (|n1|·|n2|)；任一模长平方 &lt; {@link #NORMAL_EPS}
     *         时返回 {@link Float#NEGATIVE_INFINITY} 表示该侧退化
     */
    private static float normalAlignment(float[] n1, float[] n2) {
        float l1 = n1[0] * n1[0] + n1[1] * n1[1] + n1[2] * n1[2];
        float l2 = n2[0] * n2[0] + n2[1] * n2[1] + n2[2] * n2[2];
        if (l1 < NORMAL_EPS || l2 < NORMAL_EPS) {
            return Float.NEGATIVE_INFINITY;
        }
        float dot = n1[0] * n2[0] + n1[1] * n2[1] + n1[2] * n2[2];
        return (float) (dot / Math.sqrt((double) l1 * (double) l2));
    }

    // ═══════════════════════════════════════════════
    // n > 4——ear clipping（与 Mikk 完全一致）
    // ═══════════════════════════════════════════════

    private void earClip(int start, int n,
                         float[] pos, int pOff,
                         int[] cv, int cvOff,
                         int[] o, int w) {
        // 1. Newell 法线——确定投影平面
        float nx = 0f, ny = 0f, nz = 0f;
        for (int i = 0; i < n; i++) {
            int vCurr = cv[cvOff + start + i];
            int vNext = cv[cvOff + start + (i + 1) % n];
            int bCurr = pOff + vCurr * 3;
            int bNext = pOff + vNext * 3;

            float x0 = pos[bCurr],     y0 = pos[bCurr + 1], z0 = pos[bCurr + 2];
            float x1 = pos[bNext],     y1 = pos[bNext + 1], z1 = pos[bNext + 2];

            nx += (y0 - y1) * (z0 + z1);
            ny += (z0 - z1) * (x0 + x1);
            nz += (x0 - x1) * (y0 + y1);
        }

        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        int dropAxis;
        if (ax >= ay && ax >= az)      dropAxis = 0;
        else if (ay >= az)             dropAxis = 1;
        else                           dropAxis = 2;

        // 2. 投影到 2D
        float[] proj = new float[n * 2];
        for (int i = 0; i < n; i++) {
            int v = cv[cvOff + start + i];
            int b = pOff + v * 3;
            switch (dropAxis) {
                case 0:
                    proj[i * 2]     = pos[b + 1];
                    proj[i * 2 + 1] = pos[b + 2];
                    break;
                case 1:
                    proj[i * 2]     = pos[b];
                    proj[i * 2 + 1] = pos[b + 2];
                    break;
                default:
                    proj[i * 2]     = pos[b];
                    proj[i * 2 + 1] = pos[b + 1];
                    break;
            }
        }

        // 3. 有符号面积——确定绕序
        float area2 = 0f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area2 += proj[i * 2] * proj[j * 2 + 1] - proj[j * 2] * proj[i * 2 + 1];
        }
        boolean ccw = area2 > 0f;

        // 4. 创建该 face 的耳检测器
        EarTester tester = earTest.create(proj, n, ccw);

        // 5. 剩余顶点索引（局部 0..n-1）
        int[] remaining = new int[n];
        for (int i = 0; i < n; i++) remaining[i] = i;
        int count = n;

        // 6. 逐耳裁剪
        while (count > 3) {
            boolean earFound = false;
            for (int i = 0; i < count; i++) {
                int prev = remaining[(i - 1 + count) % count];
                int curr = remaining[i];
                int next = remaining[(i + 1) % count];

                if (!GeometryPredicates.isConvex(proj, prev, curr, next, ccw)) continue;
                if (!tester.isEar(remaining, count, i, prev, curr, next)) continue;

                o[w++] = start + prev;
                o[w++] = start + curr;
                o[w++] = start + next;

                for (int k = i; k < count - 1; k++) remaining[k] = remaining[k + 1];
                count--;
                earFound = true;
                break;
            }

            if (!earFound) {
                // 退化——回退 fan 输出剩余部分
                for (int i = 1; i < count - 1; i++) {
                    o[w++] = start + remaining[0];
                    o[w++] = start + remaining[i];
                    o[w++] = start + remaining[i + 1];
                }
                return;
            }
        }

        o[w++] = start + remaining[0];
        o[w++] = start + remaining[1];
        o[w++] = start + remaining[2];
    }
}