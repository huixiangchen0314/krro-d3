package top.kzre.krro.d3.core.geometry.algo.predicate;

/**
 * 耳检测策略——为每个 face 创建一个 {@link EarTester}。
 *
 * <p><b>为什么是工厂</b>：耳检测可能需要 per-face 的预计算（KD-Tree 构建）。
 * 每个 face 独立创建 tester，并行时每线程无共享状态。
 *
 * <p><b>生命周期</b>：{@code earClip} 开始时调 {@link #create} 一次，
 * 裁剪过程中用返回的 {@link EarTester} 多次查询。tester 不跨 face 复用。
 *
 * <p><b>实现</b>：
 * <ul>
 *   <li>{@link BruteForceEarTest}——无预计算，O(k) 遍历，小多边形默认</li>
 *   <li>{@code KdTreeEarTest}——构建 KD-Tree，O(log k) 查询，大多边形凹多边形</li>
 * </ul>
 *
 * <p><b>线程契约</b>：{@link #create} 和返回的 {@link EarTester} 都无共享状态，
 * 不同 face 的 tester 可以在不同线程上并行运行。
 */
public interface EarTestStrategy {

    /**
     * 为一个 face 创建耳检测器。
     *
     * @param proj 投影后的 2D 坐标，{@code float2[n]}
     * @param n    face 的顶点数
     * @param ccw  多边形绕序
     * @return 该 face 的耳检测器，非 null
     */
    EarTester create(float[] proj, int n, boolean ccw);
}