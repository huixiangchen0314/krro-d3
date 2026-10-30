package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.Mesh;
import top.kzre.krro.util.arena.FloatArenaView;

import java.nio.FloatBuffer;

/**
 * 面角三角化——把面角模型的 {@link Mesh} 展开成
 * {@code glDrawArrays(GL_TRIANGLES)} 需要的三角形顶点序列。
 *
 * <p><b>三角化策略</b>：每个面 fan 三角化——以第 0 个面角为扇心，
 * 把 n 边形拆成 n-2 个三角形。对凸多边形（cube 的四边形面）正确；
 * 对凹多边形可能产生退化三角形——MVP 假设所有面是凸多边形。
 *
 * <p><b>输出布局</b>：每个顶点 3 个 float（x, y, z），按三角形顺序
 * 连续排列。总 float 数 = {@code triangleCount(mesh) * 9}。
 *
 * <p><b>零拷贝输入</b>：读 COW 字段的 snapshot + 元素偏移，不复制顶点数据。
 * 元素偏移通过 {@link FloatArenaView#floatOffset()} /
 * {@link top.kzre.krro.util.arena.IntArenaView#intOffset()} 获取——
 * 它们把字节偏移转换成元素偏移。
 */
public final class MeshTriangulation {

    private MeshTriangulation() {}

    /**
     * 三角化后的三角形数量。
     *
     * <p>每个 n 边形面贡献 n-2 个三角形（n ≥ 3）。n &lt; 3 的面被忽略。
     *
     * @param mesh 网格，非 null
     * @return 三角形数量
     */
    public static int triangleCount(Mesh mesh) {
        if (mesh == null) throw new NullPointerException("mesh");

        int[] faceOffsets = mesh.faceOffsets().getIntsSnapshot();
        int foBase = (int) mesh.faceOffsets().intOffset();
        int faceCount = (int) mesh.faceCount();

        int triCount = 0;
        for (int f = 0; f < faceCount; f++) {
            int n = faceOffsets[foBase + f + 1] - faceOffsets[foBase + f];
            // 多边形 = n-2 个三角形
            if (n >= 3) triCount += n - 2;
        }
        return triCount;
    }

    public static void expand(Mesh mesh, FloatArenaView arenaView) {
        expand(mesh, arenaView.floatBuffer());
    }

    /**
     * 三角化展开——把顶点位置按三角形顺序写入 {@code buffer}。
     *
     * <p><b>写入量</b>：{@code triangleCount(mesh) * 9} 个 float。
     * 从 {@code buffer} 的当前 position 开始写，写完 position 前进
     * 该数量。不 flip——调用方负责后续操作。
     *
     * @param mesh   网格，非 null
     * @param buffer 目标 buffer，剩余容量至少 {@code triangleCount * 9}
     * @throws NullPointerException     mesh / buffer 为 null
     * @throws IllegalArgumentException buffer 剩余容量不足
     */
    private static void expand(Mesh mesh, FloatBuffer buffer) {
        if (mesh == null) throw new NullPointerException("mesh");
        if (buffer == null) throw new NullPointerException("buffer");

        int needed = triangleCount(mesh) * 9;
        if (buffer.remaining() < needed) {
            throw new IllegalArgumentException(
                    "buffer needs " + needed + " floats, has " + buffer.remaining());
        }

        float[] positions = mesh.positions().getFloatsSnapshot();
        int posBase = (int) mesh.positions().floatOffset();
        int[] cornerVerts = mesh.cornerVerts().getIntsSnapshot();
        int cvBase = (int) mesh.cornerVerts().intOffset();
        int[] faceOffsets = mesh.faceOffsets().getIntsSnapshot();
        int foBase = (int) mesh.faceOffsets().intOffset();
        int faceCount = (int) mesh.faceCount();

        for (int f = 0; f < faceCount; f++) {
            int start = faceOffsets[foBase + f];
            int end = faceOffsets[foBase + f + 1];
            int n = end - start;
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
        int base = posBase + vertexIndex * 3;
        buffer.put(positions[base]);
        buffer.put(positions[base + 1]);
        buffer.put(positions[base + 2]);
    }
}