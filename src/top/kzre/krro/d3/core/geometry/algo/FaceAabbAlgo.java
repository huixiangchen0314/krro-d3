package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.IntArenaView;

/**
 * 面 AABB 算法——逐面扫描其 corner 的顶点位置，求 min / max。
 *
 * <p><b>输入</b>：
 * <ul>
 *   <li>{@code positions}——{@code xyzxyz...}，{@code count() = nverts * 3}</li>
 *   <li>{@code cornerVerts}——{@code count() = ncorners}</li>
 *   <li>{@code faceOffsets}——{@code count() = nfaces + 1}，CSR offset</li>
 * </ul>
 *
 * <p><b>输出</b>：{@code float6[nfaces]}——每个 face
 * {@code [minX, minY, minZ, maxX, maxY, maxZ]}。
 *
 * <p><b>退化面</b>（{@code faceOffsets[f+1] - faceOffsets[f] < 1}）：
 * 写入 {@code [+inf, +inf, +inf, -inf, -inf, -inf]}——表示空包围盒。
 *
 * <p><b>SPI</b>：替换实现只需实现本接口。
 */
public interface FaceAabbAlgo {

    /**
     * 输出元素数——{@code faceCount * 6}，
     * {@code faceCount = faceOffsets.count() - 1}。
     */
    default long count(CopyOnWriteInts faceOffsets) {
        int count;
        try (IntArenaView.ArraySession array = faceOffsets.array()) {
            count = array.count();
        }

        return (count - 1L) * 6L;
    }

    /**
     * 计算每个面的 AABB，写入 {@code out}。
     *
     * <p><b>写入契约</b>：{@code out} 必须刚分配、未共享（refCount=1）。
     *
     * @param positions   position 行，{@code count() % 3 == 0}，非 null
     * @param cornerVerts 面角→顶点映射，非 null
     * @param faceOffsets 面→corner 范围，非 null
     * @param out         输出容器，{@code count() >= count(faceOffsets)}，刚分配、未共享
     * @throws NullPointerException     positions / cornerVerts / faceOffsets / out 为 null
     * @throws IllegalArgumentException 容量不足或维度不匹配
     */
    void compute(CopyOnWriteFloats positions,
                 CopyOnWriteInts cornerVerts,
                 CopyOnWriteInts faceOffsets,
                 CopyOnWriteFloats out);
}