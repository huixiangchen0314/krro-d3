package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.AABB;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;

/**
 * MinMax 扫描——默认实现。
 *
 * <p><b>写底层数组</b>：{@code out.getFloatsForWrite()[out.floatOffset() + i]}，
 * 不经过 {@code FloatBuffer}，不碰 {@code ArenaView}。
 *
 * <p><b>写契约</b>：{@code out} 是刚分配的、未共享的 COW（refCount=1）——
 * {@code getFloatsForWrite()} 不触发 COW 复制，直接返回底层数组。
 * {@code floatOffset()} 必须在 {@code getFloatsForWrite()} 之后取——
 * 如果 COW 实现触发复制，offset 会变。
 *
 * <p><b>线程契约</b>：只读 {@code positions}，写 {@code out}。任意线程可调。
 */
public final class MinMaxScanImpl implements MinMaxScanAlgo {

    public MinMaxScanImpl() {}

    @Override
    public void compute(CopyOnWriteFloats positions, AABB out) {
        if (positions == null) throw new NullPointerException("positions");
        if (out == null)       throw new NullPointerException("out");

        long posCount = positions.count();
        if (posCount % 3 != 0) {
            throw new IllegalArgumentException(
                    "positions count must be multiple of 3, got " + posCount);
        }


        float[] pos    = positions.getFloatsSnapshot();
        int     posOff = (int) positions.floatOffset();
        int     nverts = (int) (posCount / 3);

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
    }
}