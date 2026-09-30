package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.Mesh;
import top.kzre.krro.d3.core.util.LongIntMap;

/**
 * 面角 → 边——查表。
 *
 * <p><b>输出</b>：int[ncorners]。
 *
 * <p><b>复用</b>：结果长度 = cornerCount——允许外部传入。
 */
public final class CornerEdgeLookup {

    private CornerEdgeLookup() {}

    public static int length(Mesh mesh) {
        return (int) mesh.cornerCount();
    }

    private static final ThreadLocal<LongIntMap> CORNER_EDGE_MAP =
            ThreadLocal.withInitial(() -> new LongIntMap(1024));

    public static int[] compute(Mesh mesh, int[] edges) {
        return compute(mesh, edges, new int[length(mesh)]);
    }

    public static int[] compute(Mesh mesh, int[] edges, int[] out) {
        int[] cornerVerts = mesh.cornerVerts().getIntsSnapshot();
        int[] faceOffsets = mesh.faceOffsets().getIntsSnapshot();

        long faceCount   = mesh.faceCount();
        long cornerCount = mesh.cornerCount();

        int edgeCount = edges.length / 2;

        LongIntMap edgeMap = CORNER_EDGE_MAP.get();
        edgeMap.ensureCapacity(edgeCount);

        for (int e = 0; e < edgeCount; e++) {
            int  v0  = edges[e * 2];
            int  v1  = edges[e * 2 + 1];
            long key = ((long) v0 << 32) | (v1 & 0xFFFFFFFFL);
            edgeMap.put(key, e);
        }

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
                out[start + i] = edgeMap.get(key);
            }
        }

        return out;
    }
}