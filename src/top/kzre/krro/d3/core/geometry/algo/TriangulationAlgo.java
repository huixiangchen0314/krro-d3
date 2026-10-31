package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * 三角化算法——输出 corner 索引数组，每三个一组构成一个三角形。
 *
 * <p><b>输入</b>：
 * <ul>
 *   <li>{@code positions}——{@code xyzxyz...}，{@code count() = nverts * 3}</li>
 *   <li>{@code cornerVerts}——{@code count() = ncorners}</li>
 *   <li>{@code faceOffsets}——{@code count() = nfaces + 1}</li>
 *   <li>{@code uv0}——{@code uvuv...}，{@code count() = ncorners * 2}——为 quad
 *       最短对角线的 UV 空间第二级判据提供输入；只用 position 距离的实现可以忽略</li>
 * </ul>
 *
 * <p><b>输出</b>：{@code int[ntri * 3]}——每三个元素一个三角形，值是
 * <b>corner 索引</b>（不是 vertex 索引）。配合 per-corner 顶点缓冲使用
 * （1:1 拆分下，corner 索引 = GPU vertex 索引）。
 *
 * <p><b>三角化规则</b>（MikkTSpace 标准）：
 * <ul>
 *   <li>{@code n == 3}：直接输出 {@code [c0, c1, c2]}</li>
 *   <li>{@code n == 4}：选较短的对角线——比较 {@code |c0-c2|} 和 {@code |c1-c3|}
 *       的 3D 距离；相等时比较 UV 空间的对角线距离</li>
 *   <li>{@code n > 4}：当前 MVP 用 fan（{@code start, start+i, start+i+1}），
 *       后续换成 ear clipping——避免凹多边形产生重叠三角形</li>
 * </ul>
 *
 * <p><b>退化面</b>（{@code n < 3}）：不产生三角形。
 *
 * <p><b>SPI</b>：替换实现只需实现本接口。当前实现是 fan；后续换
 * 最短对角线 + ear clipping，接口不变。
 */
public interface TriangulationAlgo {

    /**
     * 输出元素数——{@code triCount * 3}，{@code triCount} 是遍历
     * {@code faceOffsets} 对每个 face 累加 {@code max(0, n - 2)} 的结果。
     */
    default long count(CopyOnWriteInts faceOffsets) {
        // 默认实现仅支持 2.1G，int 长度限制，如果想流处理更多的顶点，请替换实现， 需要精度开关
        int[] fo   = faceOffsets.getIntsSnapshot();
        int   off  = (int) faceOffsets.intOffset();
        int   faceCount = (int) faceOffsets.count() - 1;
        long  triCount = 0L;
        for (int f = 0; f < faceCount; f++) {
            long n = (long) fo[off + f + 1] - fo[off + f];
            if (n >= 3) triCount += n - 2;
        }
        return triCount * 3L;
    }

    /**
     * 三角化，写入 {@code out}。
     *
     * <p><b>写入契约</b>：{@code out} 必须刚分配、未共享（refCount=1）。
     *
     * @param positions   position 行，{@code count() % 3 == 0}，非 null
     * @param cornerVerts 面角→顶点映射，非 null
     * @param faceOffsets 面→corner 范围，非 null
     * @param uv         UV 行，{@code count() % 2 == 0}，可为 null——
     *                    为 null 时 quad 只按 3D 距离选对角线
     * @param out         输出容器，{@code count() >= count(faceOffsets)}，刚分配、未共享
     * @throws NullPointerException     positions / cornerVerts / faceOffsets / out 为 null
     * @throws IllegalArgumentException 容量不足或维度不匹配
     */
    void compute(CopyOnWriteFloats positions,
                 CopyOnWriteInts cornerVerts,
                 CopyOnWriteInts faceOffsets,
                 CopyOnWriteFloats uv,
                 CopyOnWriteInts out);
}