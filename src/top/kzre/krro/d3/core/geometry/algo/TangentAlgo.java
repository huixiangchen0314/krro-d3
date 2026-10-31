package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * 切线算法——输出 per-corner 切线，xyz + 手性 w。
 *
 * <p><b>输入</b>：
 * <ul>
 *   <li>{@code positions}——{@code xyzxyz...}，{@code count() = nverts * 3}</li>
 *   <li>{@code cornerVerts}——{@code count() = ncorners}</li>
 *   <li>{@code faceOffsets}——{@code count() = nfaces + 1}</li>
 *   <li>{@code uv0}——{@code uvuv...}，{@code count() = ncorners * 2}</li>
 *   <li>{@code cornerNormals}——{@code xyzxyz...}，{@code count() = ncorners * 3}</li>
 * </ul>
 *
 * <p><b>输出</b>：{@code float4[ncorners]}——{@code xyzwxyzw...}，xyz 是切线，
 * w 是手性（{@code +1} 或 {@code -1}），{@code count() = ncorners * 4}。
 *
 * <p><b>语义（MikkTSpace）</b>：切线空间的插值受三角化影响。当前实现按
 * vertex 累加 {@code tan1/tan2}，近似了焊接但遗漏了 UV 接缝处的焊接组区分——
 * 同一 vertex 的两个 corner 如果 UV 不同，应视为两个焊接组，各自独立平均。
 * 后续实现应按 {@code hash(position, normal, uv)} 分组，按组累加 / 平均，
 * 退化三角形的 corner 从所在焊接组继承切线。
 *
 * <p><b>退化三角形</b>（{@code |r| < ε}）：不贡献 {@code tan1/tan2}。
 * corner 如果所在焊接组没有任何非退化贡献，fallback 到 {@code (1, 0, 0, 1)}。
 *
 * <p><b>SPI</b>：替换实现只需实现本接口。jMonkeyEngine 的纯 Java
 * MikkTSpace 翻译可以作为参考实现。
 */
public interface TangentAlgo {

    /** 输出元素数——{@code cornerVerts.count() * 4}。 */
    default long count(CopyOnWriteInts cornerVerts) {
        return cornerVerts.count() * 4L;
    }

    /**
     * 计算切线，写入 {@code out}。
     *
     * <p><b>写入契约</b>：{@code out} 必须刚分配、未共享（refCount=1）。
     *
     * @param positions     position 行，{@code count() % 3 == 0}，非 null
     * @param cornerVerts   面角→顶点映射，非 null
     * @param faceOffsets   面→corner 范围，非 null
     * @param uv0 UV 行，{@code count() % 2 == 0}，<b>一般非 null，但工程上仍然要兼容null</b>——
     *            MikkTSpace 规范要求 UV 存在；缺失 UV 有策略实现处理，
     *            算法层不降级
     * @param cornerNormals 面角法线，{@code count() % 3 == 0}，非 null
     * @param out           输出容器，{@code count() >= cornerVerts.count() * 4}，刚分配、未共享
     * @throws NullPointerException     positions / cornerVerts / faceOffsets / uv0 / cornerNormals / out 为 null
     * @throws IllegalArgumentException 容量不足或维度不匹配
     */
    void compute(CopyOnWriteFloats positions,
                 CopyOnWriteInts cornerVerts,
                 CopyOnWriteInts faceOffsets,
                 CopyOnWriteFloats uv0,
                 CopyOnWriteFloats cornerNormals,
                 CopyOnWriteFloats out);
}