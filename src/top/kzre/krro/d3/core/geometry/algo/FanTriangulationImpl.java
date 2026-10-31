package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.Mesh;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.FloatArenaView;

import java.nio.FloatBuffer;

/**
 * 面角三角化——fan 三角化，输出 corner 索引。
 *
 * <p><b>三角化策略</b>：每个面 fan 三角化——以第 0 个面角为扇心，
 * 把 n 边形拆成 n-2 个三角形。对凸多边形（cube 的四边形面）正确；
 * 对凹多边形可能产生重叠三角形——MVP 假设所有面是凸多边形，
 * 后续换 {@code MikkTriangulationImpl}（最短对角线 + ear clipping）。
 *
 * <p><b>输出</b>：corner 索引，{@code int[ntri * 3]}——每三个一组一个三角形。
 * 配合 per-corner 顶点缓冲（{@code cornerPositions} / {@code cornerNormals} /
 * {@code cornerUvs}）使用 {@code glDrawElements}。
 *
 * <p><b>非索引展开</b>：{@link #expand(Mesh, FloatArenaView)} 提供
 * {@code glDrawArrays} 路径——把三角形顶点的 position 展开成
 * {@code float3[ntri * 3]}。不走 {@link TriangulationAlgo} 接口——
 * 它不是 corner 索引输出。
 */
public final class FanTriangulationImpl implements TriangulationAlgo {

    public FanTriangulationImpl() {}

    /**
     * fan 三角化，输出 corner 索引到 {@code out}。
     *
     * <p><b>写入契约</b>：{@code out} 必须刚分配、未共享（refCount=1），
     * 且 {@code out.count() == count(faceOffsets)}——由调用方用
     * {@link #count(CopyOnWriteInts)} 分配。
     *
     * <p><b>uv0</b>：fan 不依赖 UV——参数接收但不用。
     *
     * @param positions   position 行，可为 null——fan 不读 position
     * @param cornerVerts 面角→顶点映射，可为 null——fan 只输出 corner 索引，不查 vertex
     * @param faceOffsets 面→corner 范围，非 null
     * @param uv0         UV 行，可为 null——fan 不读 UV
     * @param out         输出容器，{@code count() == count(faceOffsets)}，刚分配、未共享
     * @throws NullPointerException     faceOffsets / out 为 null
     * @throws IllegalStateException    写入量 != {@code out.count()}——调用方没有用 count 分配
     */
    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteFloats uv0,
                        CopyOnWriteInts out) {
        if (faceOffsets == null) throw new NullPointerException("faceOffsets");
        if (out == null)         throw new NullPointerException("out");

        int[] fo    = faceOffsets.getIntsSnapshot();
        int   foOff = (int) faceOffsets.intOffset();
        int   faceCount = (int) faceOffsets.count() - 1;

        // 写——先 ForWrite，再取 offset
        int[] o    = out.getIntsForWrite();
        int   oOff = (int) out.intOffset();

        int w = oOff;
        for (int f = 0; f < faceCount; f++) {
            int start = fo[foOff + f];
            int n     = fo[foOff + f + 1] - start;
            if (n < 3) continue;

            for (int i = 1; i < n - 1; i++) {
                o[w++] = start;
                o[w++] = start + i;
                o[w++] = start + i + 1;
            }
        }

        long written = (long) w - oOff;
        if (written != out.count()) {
            throw new IllegalStateException(
                    "triangulation count mismatch: expected " + out.count()
                            + ", wrote " + written);
        }
    }

    /**
     * 非索引展开——把三角形顶点的 position 按三角形顺序写入
     * {@code arenaView}，布局 {@code float3[ntri * 3]}。
     *
     * <p>glDrawArrays 路径。不属于 {@link TriangulationAlgo} 接口——
     * 接口输出 corner 索引，这个方法输出展开的顶点 position。
     *
     * <p><b>写入量</b>：{@code triangleCount(mesh) * 9} 个 float。
     *
     * @param mesh      网格，非 null
     * @param arenaView 输出容器，剩余容量至少 {@code triangleCount * 9}
     * @throws NullPointerException     mesh / arenaView 为 null
     * @throws IllegalArgumentException arenaView 剩余容量不足
     */
    private static void expand(Mesh mesh, FloatArenaView arenaView) {
        if (mesh == null)      throw new NullPointerException("mesh");
        if (arenaView == null) throw new NullPointerException("arenaView");

        int[] fo     = mesh.faceOffsets().getIntsSnapshot();
        int   foOff  = (int) mesh.faceOffsets().intOffset();
        int   faceCount = (int) mesh.faceCount();

        // 算 triCount——内联，不依赖接口的 count 实例方法
        long triCount = 0L;
        for (int f = 0; f < faceCount; f++) {
            long n = (long) fo[foOff + f + 1] - fo[foOff + f];
            if (n >= 3) triCount += n - 2;
        }
        long needed = triCount * 9L;

        FloatBuffer buffer = arenaView.floatBuffer();
        if (buffer.remaining() < needed) {
            throw new IllegalArgumentException(
                    "arenaView needs " + needed + " floats, has " + buffer.remaining());
        }

        float[] positions   = mesh.positions().getFloatsSnapshot();
        int     posBase     = (int) mesh.positions().floatOffset();
        int[]   cornerVerts = mesh.cornerVerts().getIntsSnapshot();
        int     cvBase      = (int) mesh.cornerVerts().intOffset();

        for (int f = 0; f < faceCount; f++) {
            int start = fo[foOff + f];
            int n     = fo[foOff + f + 1] - start;
            if (n < 3) continue;

            int c0 = cornerVerts[cvBase + start];
            for (int i = 1; i < n - 1; i++) {
                int c1 = cornerVerts[cvBase + start + i];
                int c2 = cornerVerts[cvBase + start + i + 1];
                writeVertex(buffer, positions, posBase, c0);
                writeVertex(buffer, positions, posBase, c1);
                writeVertex(buffer, positions, posBase, c2);
            }
        }
    }

    private static void writeVertex(FloatBuffer buffer, float[] positions,
                                    int posBase, int vertexIndex) {
        int b = posBase + vertexIndex * 3;
        buffer.put(positions[b]);
        buffer.put(positions[b + 1]);
        buffer.put(positions[b + 2]);
    }
}