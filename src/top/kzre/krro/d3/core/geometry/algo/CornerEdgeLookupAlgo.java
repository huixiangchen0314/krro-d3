package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * 面角→边查表——每个 corner 对应哪条边。
 *
 * <p><b>输入</b>：
 * <ul>
 *   <li>{@code cornerVerts}——{@code count() = ncorners}</li>
 *   <li>{@code faceOffsets}——{@code count() = nfaces + 1}</li>
 *   <li>{@code edgeEndpoints}——{@code int2[nedges]}，每元素两个顶点索引，
 *       {@code count() = nedges * 2}</li>
 * </ul>
 *
 * <p><b>输出</b>：{@code int[ncorners]}——每个 corner 对应的 edge 索引。
 * 退化面（{@code n < 2}）的 corner 写 {@code -1}——保持输出长度 = {@code ncorners}。
 *
 * <p><b>边来源</b>：{@code edgeEndpoints} 是显式存储的，不走 HashDedupEdges 派生。
 *
 * <p><b>SPI</b>：替换实现只需实现本接口。当前实现内部用 ThreadLocal
 * {@code LongIntMap} 做 {@code (v0,v1) → edgeIndex} 查询；后续可换成
 * CSR / 开放寻址哈希，接口不变。
 */
public interface CornerEdgeLookupAlgo {

    /** 输出元素数——{@code cornerVerts.count()}。 */
    default long count(CopyOnWriteInts cornerVerts) {
        return cornerVerts.count();
    }

    /**
     * 计算 corner→edge 映射，写入 {@code out}。
     *
     * <p><b>写入契约</b>：{@code out} 必须刚分配、未共享（refCount=1）。
     *
     * @param cornerVerts   面角→顶点映射，非 null
     * @param faceOffsets   面→corner 范围，非 null
     * @param edgeEndpoints 边→顶点对，{@code count() % 2 == 0}，非 null
     * @param out           输出容器，{@code count() >= cornerVerts.count()}，刚分配、未共享
     * @throws NullPointerException     cornerVerts / faceOffsets / edgeEndpoints / out 为 null
     * @throws IllegalArgumentException 容量不足或 edgeEndpoints 的 count 不是 2 的倍数
     */
    void compute(CopyOnWriteInts cornerVerts,
                 CopyOnWriteInts faceOffsets,
                 CopyOnWriteInts edgeEndpoints,
                 CopyOnWriteInts out);
}