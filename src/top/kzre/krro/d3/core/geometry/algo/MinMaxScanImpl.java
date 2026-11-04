package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.AABB;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.util.arena.FloatArenaView;

/**
 * MinMax 扫描——默认实现。
 *
 * <p><b>Session 访问</b>：通过 {@link FloatArenaView.ArraySession}
 * 读取 {@code positions} 的底层数组与元素偏移——会话生命周期由
 * try-with-resources 保证。读会话不触发 COW 复制。
 *
 * <p><b>输出</b>：写入 {@link AABB}——{@code out.set(minX, minY, minZ, maxX, maxY, maxZ)}，
 * 不经过 {@code FloatArenaView}。
 *
 * <p><b>空顶点集</b>：{@code nverts == 0} 时 min 保持 {@code +inf}、
 * max 保持 {@code -inf}，AABB 被写为空包围盒——与「无顶点」语义一致。
 *
 * <p><b>线程契约</b>：只读 {@code positions}，写 {@code out}。任意线程可调。
 */
public final class MinMaxScanImpl implements MinMaxScanAlgo {

    public MinMaxScanImpl() {}

    @Override
    public void compute(CopyOnWriteFloats positions, AABB out) {
        if (positions == null) throw new NullPointerException("positions");
        if (out == null)       throw new NullPointerException("out");

        try (FloatArenaView.ArraySession posSess = positions.array()) {

            int posCount = posSess.count();
            if (posCount % 3 != 0) {
                throw new IllegalArgumentException(
                        "positions count must be multiple of 3, got " + posCount);
            }

            float[] pos    = posSess.getFloats();
            int     posOff = posSess.offset();
            int     nverts = posCount / 3;

            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;

            for (int v = 0; v < nverts; v++) {
                int b = posOff + v * 3;
                float x = pos[b];
                float y = pos[b + 1];
                float z = pos[b + 2];

                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (z < minZ) minZ = z;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                if (z > maxZ) maxZ = z;
            }

            out.set(minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}