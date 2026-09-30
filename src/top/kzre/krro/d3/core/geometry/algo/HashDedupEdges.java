package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.Mesh;
import top.kzre.krro.d3.core.util.LongIntMap;

/**
 * 边表——按 (min, max) 归一化——哈希去重。
 *
 * <p><b>输出</b>：int2[nedges]——两端点交替。
 *
 * <p><b>复用</b>：输出长度取决于拓扑——不定——每次返回精确大小新数组。
 */
public final class HashDedupEdges {

    private HashDedupEdges() {}

    private static final ThreadLocal<LongIntMap> EDGE_MAP =
            ThreadLocal.withInitial(() -> new LongIntMap(1024));

    public static int[] compute(Mesh mesh) {
        int[] cornerVerts = mesh.cornerVerts().getIntsSnapshot();
        int[] faceOffsets = mesh.faceOffsets().getIntsSnapshot();
        long  faceCount   = mesh.faceCount();

        int estimatedEdges = Math.max(16, (int) (mesh.cornerCount() / 2));

        LongIntMap edgeMap = EDGE_MAP.get();
        edgeMap.ensureCapacity(estimatedEdges);

        int[] edges     = new int[estimatedEdges * 2];
        int   edgeCount = 0;

        for (long f = 0; f < faceCount; f++) {
            int start = faceOffsets[(int) f];
            int end   = faceOffsets[(int) f + 1];
            int n     = end - start;

            if (n < 2) continue;

            for (int i = 0; i < n; i++) {
                int v0 = cornerVerts[start + i];
                int v1 = cornerVerts[start + (i + 1) % n];

                int a = v0 < v1 ? v0 : v1;
                int b = v0 < v1 ? v1 : v0;

                long key = ((long) a << 32) | (b & 0xFFFFFFFFL);

                if (!edgeMap.containsKey(key)) {
                    if ((edgeCount + 1) * 2 > edges.length) {
                        int[] newEdges = new int[edges.length * 2];
                        System.arraycopy(edges, 0, newEdges, 0, edgeCount * 2);
                        edges = newEdges;
                    }
                    edgeMap.put(key, edgeCount);
                    edges[edgeCount * 2]     = a;
                    edges[edgeCount * 2 + 1] = b;
                    edgeCount++;
                }
            }
        }

        int[] result = new int[edgeCount * 2];
        System.arraycopy(edges, 0, result, 0, edgeCount * 2);
        return result;
    }
}