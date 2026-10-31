package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;

/**
 * 包围盒算法——遍历顶点位置，取最小 / 最大。
 *
 * <p><b>输入</b>：{@code positions}——布局 {@code xyzxyz...}，
 * {@code count() = nverts * 3}，dimension=3。
 *
 * <p><b>输出</b>：{@code float[6]}——[minX, minY, minZ, maxX, maxY, maxZ]。
 *
 * <p><b>SPI</b>：替换实现只需实现本接口，{@code ExtendedMesh} 持有实例。
 */
public interface MinMaxScanAlgo {
     int LENGTH = 6;

    /** 输出元素数——固定 6。 */
    default long count() {
        return LENGTH;
    }

    /**
     * 计算包围盒，直接写入 {@code out} 的底层数组。
     *
     * <p><b>写入契约</b>：{@code out} 必须刚分配、未共享（refCount=1）。
     * 实现用 {@code out.getFloatsForWrite()} 取数组，
     * {@code out.floatOffset()} 在 {@code ForWrite} 之后取。
     *
     * @param positions position 行，{@code count() % 3 == 0}，非 null
     * @param out       输出容器，{@code count() >= 6}，刚分配、未共享
     * @throws NullPointerException     positions / out 为 null
     * @throws IllegalArgumentException positions 的 count 不是 3 的倍数，或 out 容量不足
     */
    void compute(CopyOnWriteFloats positions, CopyOnWriteFloats out);
}