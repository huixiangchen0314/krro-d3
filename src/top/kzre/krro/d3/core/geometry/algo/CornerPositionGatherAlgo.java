package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.IntArenaView;

/**
 * 面角位置 gather——把 per-vertex 的 positions 重排为 per-corner 的
 * cornerPositions。
 *
 * <p><b>算法</b>：{@code cornerPositions[c] = positions[cornerVerts[c]]}。
 *
 * <p><b>输入</b>：
 * <ul>
 *   <li>{@code positions}——{@code xyzxyz...}，{@code count() = nverts * 3}</li>
 *   <li>{@code cornerVerts}——{@code count() = ncorners}，每元素是 vertex 索引</li>
 * </ul>
 *
 * <p><b>输出</b>：{@code float3[ncorners]}——{@code xyzxyz...}，
 * {@code count() = ncorners * 3}。
 *
 * <p><b>用途</b>：GPU 顶点是 per-corner 的——同一位置在不同 corner 可能有
 * 不同的 UV / 法线。position VBO 必须存 per-corner 的 position。
 *
 * <p><b>SPI</b>：替换实现只需实现本接口。
 */
public interface CornerPositionGatherAlgo {

    /** 输出元素数——{@code cornerVerts.count() * 3}。 */
    default long count(CopyOnWriteInts cornerVerts) {
        int count;
        try (IntArenaView.ArraySession array = cornerVerts.array()) {
            count = array.count();
        }
        return count * 3L;
    }

    /**
     * 重排 positions 为 per-corner 的 cornerPositions，写入 {@code out}。
     *
     * <p><b>写入契约</b>：{@code out} 必须刚分配、未共享（refCount=1）。
     *
     * @param positions   position 行，{@code count() % 3 == 0}，非 null
     * @param cornerVerts 面角→顶点映射，非 null
     * @param out         输出容器，{@code count() >= cornerVerts.count() * 3}，刚分配、未共享
     * @throws NullPointerException     positions / cornerVerts / out 为 null
     * @throws IllegalArgumentException 容量不足或维度不匹配
     */
    void compute(CopyOnWriteFloats positions,
                 CopyOnWriteInts cornerVerts,
                 CopyOnWriteFloats out);
}