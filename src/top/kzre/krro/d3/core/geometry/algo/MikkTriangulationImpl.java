package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.algo.predicate.BruteForceEarTest;
import top.kzre.krro.d3.core.geometry.algo.predicate.EarTester;
import top.kzre.krro.d3.core.geometry.algo.predicate.EarTestStrategy;
import top.kzre.krro.d3.core.geometry.algo.predicate.GeometryPredicates;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

import java.util.stream.IntStream;

/**
 * Mikk 三角化——quad 按最短对角线拆分，n > 4 用 ear clipping。
 *
 * <p><b>规则</b>（MikkTSpace 标准）：
 * <ul>
 *   <li>{@code n == 3}：直接输出 {@code [c0, c1, c2]}</li>
 *   <li>{@code n == 4}：比较对角线 {@code |pos[c0]-pos[c2]|²} 和
 *       {@code |pos[c1]-pos[c3]|²}，选较短者；距离相等时（相对 ε 内）
 *       比较 UV 空间的对角线距离；{@code uv0 == null} 时默认 0–2</li>
 *   <li>{@code n > 4}：ear clipping——投影到 Newell 法线确定的 2D 平面，
 *       逐耳裁剪，保证不产生重叠三角形</li>
 * </ul>
 *
 * <p><b>并行</b>：{@code faceCount >= PARALLEL_THRESHOLD} 时用
 * {@link IntStream#parallel()} 并行处理 face——每个 face 写入
 * {@code faceTriStart[f] * 3} 开始的独立区间，无锁。前缀和阶段串行，
 * O(nfaces)；填充阶段并行，每 face 独立。
 *
 * <p><b>耳检测可替换</b>：{@link EarTestStrategy} 决定
 * “候选耳内是否有其他顶点”的检测方式——默认 {@link BruteForceEarTest}。
 *
 * <p><b>输出</b>：corner 索引，{@code int[ntri * 3]}。三角形数 = 每个
 * {@code n >= 3} 的 face 贡献 {@code n - 2}——{@code count} 继承接口
 * {@code default}。
 *
 * <p><b>ear clipping 退化回退</b>：如果剩余多边形中找不到任何耳
 * （全部共线或退化），回退到 fan 输出剩余部分——保证输出数量不变。
 *
 * <p><b>直接写底层数组</b>：{@code out.getIntsForWrite()[out.intOffset() + w]}。
 *
 * <p><b>线程契约</b>：{@code getIntsForWrite()} 在并行之前单线程调用；
 * 并行任务只写 {@code int[]} 的不同区间，无锁安全。{@link EarTester}
 * 是 per-face 的——不同 face 的 {@code earClip} 可以在不同线程上并行。
 */
public final class MikkTriangulationImpl implements TriangulationAlgo {

    /** 3D 对角线距离相等判断的相对 epsilon。 */
    private static final float DIST_EPS = 1e-6f;

    /** 并行阈值——faceCount 低于此值串行，避免并行开销。 */
    private static final int PARALLEL_THRESHOLD = 4096;

    private final EarTestStrategy earTest;

    public MikkTriangulationImpl() {
        this(new BruteForceEarTest());
    }

    public MikkTriangulationImpl(EarTestStrategy earTest) {
        if (earTest == null) throw new NullPointerException("earTest");
        this.earTest = earTest;
    }

    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteFloats uv0,
                        CopyOnWriteInts out) {
        if (positions == null)   throw new NullPointerException("positions");
        if (cornerVerts == null) throw new NullPointerException("cornerVerts");
        if (faceOffsets == null) throw new NullPointerException("faceOffsets");
        if (out == null)         throw new NullPointerException("out");

        long posCount = positions.count();
        if (posCount % 3 != 0) {
            throw new IllegalArgumentException(
                    "positions count must be multiple of 3, got " + posCount);
        }
        if (uv0 != null && uv0.count() % 2 != 0) {
            throw new IllegalArgumentException(
                    "uv0 count must be multiple of 2, got " + uv0.count());
        }

        // ── 读 ──
        float[] pos   = positions.getFloatsSnapshot();
        int     pOff  = (int) positions.floatOffset();
        int[]   cv    = cornerVerts.getIntsSnapshot();
        int     cvOff = (int) cornerVerts.intOffset();
        int[]   fo    = faceOffsets.getIntsSnapshot();
        int     foOff = (int) faceOffsets.intOffset();
        int     faceCount = (int) faceOffsets.count() - 1;

        float[] uv;
        int     uvOff;
        if (uv0 != null) {
            uv    = uv0.getFloatsSnapshot();
            uvOff = (int) uv0.floatOffset();
        } else {
            uvOff = 0;
            uv = null;
        }

        // ── 阶段 1：前缀和，求得每个面的起始角点位置──
        int[] faceTriStart = new int[faceCount + 1];
        for (int f = 0; f < faceCount; f++) {
            int n = fo[foOff + f + 1] - fo[foOff + f];
            faceTriStart[f + 1] = faceTriStart[f] + (n >= 3 ? n - 2 : 0);
        }
        int triCount = faceTriStart[faceCount];

        if ((long) triCount * 3L != out.count()) {
            throw new IllegalStateException(
                    "triangulation count mismatch: expected " + out.count()
                            + ", prefix sum gives " + (triCount * 3L));
        }

        if (faceCount == 0) return;

        // ── 取底层数组——并行之前单线程调用 ──
        int[] o    = out.getIntsForWrite();
        int   oOff = (int) out.intOffset();

        // ── 阶段 2：填充——串行或并行 ──
        if (faceCount < PARALLEL_THRESHOLD) {
            for (int f = 0; f < faceCount; f++) {
                int start = fo[foOff + f];
                int n     = fo[foOff + f + 1] - start;
                if (n < 3) continue;
                triangulateFace(start, n, pos, pOff, cv, cvOff, uv, uvOff,
                        o, oOff + faceTriStart[f] * 3);
            }
        } else {
            IntStream.range(0, faceCount).parallel().forEach(f -> {
                int start = fo[foOff + f];
                int n     = fo[foOff + f + 1] - start;
                if (n < 3) return;
                triangulateFace(start, n, pos, pOff, cv, cvOff, uv, uvOff,
                        o, oOff + faceTriStart[f] * 3);
            });
        }
    }

    // ── per-face 分发 ──

    private void triangulateFace(int start, int n,
                                 float[] pos, int pOff,
                                 int[] cv, int cvOff,
                                 float[] uv, int uvOff,
                                 int[] o, int w) {
        if (n == 3) {
            o[w++] = start;
            o[w++] = start + 1;
            o[w++] = start + 2;
        } else if (n == 4) {
            triangulateQuad(start, pos, pOff, cv, cvOff, uv, uvOff, o, w);
        } else {
            earClip(start, n, pos, pOff, cv, cvOff, o, w);
        }
    }

    // ── quad：最短对角线 ──

    private static void triangulateQuad(int start,
                                        float[] pos, int pOff,
                                        int[] cv, int cvOff,
                                        float[] uv, int uvOff,
                                        int[] o, int w) {
        int c0 = start, c1 = start + 1, c2 = start + 2, c3 = start + 3;
        int v0 = cv[cvOff + c0], v1 = cv[cvOff + c1];
        int v2 = cv[cvOff + c2], v3 = cv[cvOff + c3];

        float d02 = GeometryPredicates.squaredDist3(pos, pOff + v0 * 3, pOff + v2 * 3);
        float d13 = GeometryPredicates.squaredDist3(pos, pOff + v1 * 3, pOff + v3 * 3);

        boolean useDiag02;
        float maxD = Math.max(d02, d13);
        if (Math.abs(d02 - d13) > DIST_EPS * Math.max(1f, maxD)) {
            useDiag02 = d02 < d13;
        } else if (uv != null) {
            float uv02 = GeometryPredicates.squaredDist2(uv, uvOff + c0 * 2, uvOff + c2 * 2);
            float uv13 = GeometryPredicates.squaredDist2(uv, uvOff + c1 * 2, uvOff + c3 * 2);
            useDiag02 = uv02 < uv13;
        } else {
            useDiag02 = true;   // 等距且无 UV——默认 0–2
        }

        if (useDiag02) {
            o[w++] = c0; o[w++] = c1; o[w++] = c2;
            o[w++] = c0; o[w++] = c2; o[w++] = c3;
        } else {
            o[w++] = c1; o[w++] = c2; o[w++] = c3;
            o[w++] = c1; o[w++] = c3; o[w++] = c0;
        }
    }

    // ── n > 4：ear clipping ──

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