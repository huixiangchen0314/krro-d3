package top.kzre.krro.d3.core.geometry.algo.predicate;

/**
 * 暴力耳检测——O(k) 遍历剩余顶点。
 *
 * <p><b>无预计算</b>：{@link #create} 只捕获 {@code proj} / {@code ccw}，
 * 不分配 KD-Tree。小多边形（n ≤ 16）默认用这个。
 *
 * <p><b>线程契约</b>：捕获的 {@code proj} 只读，无共享可变状态。
 */
public final class BruteForceEarTest implements EarTestStrategy {

    public BruteForceEarTest() {}

    @Override
    public EarTester create(float[] proj, int n, boolean ccw) {
        return new BruteForceEarTester(proj, ccw);
    }

    private static final class BruteForceEarTester implements EarTester {
        private final float[] proj;
        private final boolean ccw;

        BruteForceEarTester(float[] proj, boolean ccw) {
            this.proj = proj;
            this.ccw  = ccw;
        }

        @Override
        public boolean isEar(int[] remaining, int count,
                             int i, int prev, int curr, int next) {
            float ax = proj[prev * 2], ay = proj[prev * 2 + 1];
            float bx = proj[curr * 2], by = proj[curr * 2 + 1];
            float cx = proj[next * 2], cy = proj[next * 2 + 1];

            for (int k = 0; k < count; k++) {
                if (k == i) continue;
                int p = remaining[k];
                if (p == prev || p == curr || p == next) continue;

                if (GeometryPredicates.pointInTriangle(
                        proj[p * 2], proj[p * 2 + 1],
                        ax, ay, bx, by, cx, cy, ccw)) {
                    return false;
                }
            }
            return true;
        }
    }
}