package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.IntArenaView;

/**
 * 面角法线算法——按角度阈值聚类，输出 per-corner 法线。
 *
 * <p><b>输入</b>：
 * <ul>
 *   <li>{@code positions}——{@code xyzxyz...}，{@code count() = nverts * 3}</li>
 *   <li>{@code cornerVerts}——{@code count() = ncorners}</li>
 *   <li>{@code faceOffsets}——{@code count() = nfaces + 1}</li>
 *   <li>{@code edgeEndpoints}——{@code int2[nedges]}，{@code count() = nedges * 2}——
 *       为边邻接聚类提供邻接信息；只做顶点级全对比较的实现可以忽略</li>
 * </ul>
 *
 * <p><b>输出</b>：{@code float3[ncorners]}——{@code xyzxyz...}，
 * {@code count() = ncorners * 3}。
 *
 * <p><b>语义</b>：同一顶点周围，面法线夹角 ≤ 阈值的 corner 被合并到同一
 * 平滑组，组内法线为面法线按面积加权累加后归一化。退化面法线为
 * {@code (0, 1, 0)}。
 *
 * <p><b>SPI</b>：当前实现用“同顶点全对比较 + 并查集”，后续可换成
 * “边邻接并查集”——只合并共享一条边的 corner，修正“共享顶点不等于
 * 共享边”的语义错误，并降低高 valence 顶点的复杂度。接口不变。
 */
public interface CornerNormalsAlgo {

    /** 输出元素数——{@code cornerVerts.count() * 3}。 */
    default long count(CopyOnWriteInts cornerVerts) {
        int count;
        try (IntArenaView.ArraySession array = cornerVerts.array()) {
            count = array.count();
        }
        return count * 3L;
    }

    /**
     * 计算面角法线，写入 {@code out}。
     *
     * <p><b>写入契约</b>：{@code out} 必须刚分配、未共享（refCount=1）。
     *
     * @param positions              position 行，{@code count() % 3 == 0}，非 null
     * @param cornerVerts            面角→顶点映射，非 null
     * @param faceOffsets            面→corner 范围，非 null
     * @param edgeEndpoints          边→顶点对，可为 null——为 null 时实现退化为
     *                               顶点级全对比较
     * @param angleThresholdRadians  平滑角度阈值——弧度，{@code >= 0}
     * @param out                    输出容器，{@code count() >= cornerVerts.count() * 3}，刚分配、未共享
     * @throws NullPointerException     positions / cornerVerts / faceOffsets / out 为 null
     * @throws IllegalArgumentException 容量不足、维度不匹配、或阈值 < 0
     */
    void compute(CopyOnWriteFloats positions,
                 CopyOnWriteInts cornerVerts,
                 CopyOnWriteInts faceOffsets,
                 CopyOnWriteInts edgeEndpoints,
                 float angleThresholdRadians,
                 CopyOnWriteFloats out);
}