package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.IntArenaView;

/**
 * 切线算法——输出 per-corner 切线，xyz + 手性 w。
 *
 * <p><b>输入</b>：
 * <ul>
 *   <li>{@code positions}——{@code xyzxyz...}，{@code count() = nverts * 3}</li>
 *   <li>{@code cornerVerts}——{@code count() = ncorners}</li>
 *   <li>{@code faceOffsets}——{@code count() = nfaces + 1}——退化面判断可能用到；
 *       三角化不再依赖它</li>
 *   <li>{@code uv0}——{@code uvuv...}，{@code count() = ncorners * 2}，非 null</li>
 *   <li>{@code cornerNormals}——{@code xyzxyz...}，{@code count() = ncorners * 3}</li>
 *   <li>{@code triangleIndices}——<b>corner 索引</b>，{@code int[ntri * 3]}，
 *       每三个一组一个三角形。由 {@link TriangulationAlgo} 产出——切线计算
 *       直接遍历这份三角化结果，<b>不自己再三角化</b>，保证切线空间和渲染
 *       几何用的是同一份三角形列表</li>
 * </ul>
 *
 * <p><b>输出</b>：{@code float4[ncorners]}——{@code xyzwxyzw...}，xyz 是切线，
 * w 是手性（{@code +1} 或 {@code -1}），{@code count() = ncorners * 4}。
 *
 * <p><b>语义（MikkTSpace）</b>：{@code tan1/tan2} 按 {@code (position, normal, uv)}
 * 焊接组累加——同一 vertex 的两个 corner 如果 UV 不同，视为两个焊接组，
 * 各自独立平均。退化三角形的 corner 如果所在焊接组有非退化贡献，自动继承。
 *
 * <p><b>SPI</b>：替换实现只需实现本接口。
 */
public interface TangentAlgo {

    /** 输出元素数——{@code cornerVerts.count() * 4}。 */
    default long count(CopyOnWriteInts cornerVerts) {
        int count;
        try (IntArenaView.ArraySession array = cornerVerts.array()) {
            count = array.count();
        }
        return count * 4L;
    }

    /**
     * 计算切线，写入 {@code out}。
     *
     * <p><b>写入契约</b>：{@code out} 必须刚分配、未共享（refCount=1）。
     *
     * @param positions       position 行，{@code count() % 3 == 0}，非 null
     * @param cornerVerts     面角→顶点映射，非 null
     * @param faceOffsets     面→corner 范围，非 null——当前实现可能不用，保留给
     *                        “退化面判断 / 面级切线策略”等未来实现
     * @param uv0             UV 行，{@code count() % 2 == 0}，非 null
     * @param cornerNormals   面角法线，{@code count() % 3 == 0}，非 null
     * @param triangleIndices corner 索引，{@code count() % 3 == 0}，非 null——
     *                        {@link TriangulationAlgo} 的输出，切线计算直接遍历
     * @param out             输出容器，{@code count() >= cornerVerts.count() * 4}，刚分配、未共享
     * @throws NullPointerException    任一参数为 null
     * @throws IllegalArgumentException 容量不足或维度不匹配
     */
    void compute(CopyOnWriteFloats positions,
                 CopyOnWriteInts cornerVerts,
                 CopyOnWriteInts faceOffsets,
                 CopyOnWriteFloats uv0,
                 CopyOnWriteFloats cornerNormals,
                 CopyOnWriteInts triangleIndices, // 三角化结果
                 CopyOnWriteFloats out);
}