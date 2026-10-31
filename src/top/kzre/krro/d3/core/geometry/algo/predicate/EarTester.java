package top.kzre.krro.d3.core.geometry.algo.predicate;

/**
 * 耳检测器——判断候选耳内是否有其他剩余顶点。
 *
 * <p>由 {@link EarTestStrategy#create} 创建，绑定一个 face 的投影坐标和绕序。
 * {@link #isEar} 在裁剪过程中多次调用，{@code remaining} / {@code count} /
 * {@code i} / {@code prev} / {@code curr} / {@code next} 随裁剪动态变化。
 */
public interface EarTester {

    /**
     * 判断候选耳 {@code (prev, curr, next)} 内是否有其他剩余顶点。
     *
     * @param remaining 剩余顶点索引，{@code int[count]}
     * @param count     剩余顶点数
     * @param i         {@code curr} 在 {@code remaining} 中的位置
     * @param prev      前一个顶点索引（局部 0..n-1）
     * @param curr      当前顶点索引（候选耳尖）
     * @param next      后一个顶点索引
     * @return true 表示是耳——三角形内没有其他剩余顶点
     */
    boolean isEar(int[] remaining, int count, int i, int prev, int curr, int next);
}