package top.kzre.krro.d3.core.geometry;

/**
 * 子网格——材质分组。
 *
 * <p><b>领域定位</b>：面的一段连续范围 + 材质槽号。用于"这段面用第几号
 * 材质槽"的标记。不引用材质资产——槽号到材质的映射在物体的材质槽列表里。
 *
 * <p><b>表达方式</b>：范围表达。三个 int 描述一个子网格，不持有面索引数组。
 *
 * <p><b>前提约束</b>（由 Mesh 的构造方保证）：
 * <ul>
 *   <li><b>面排序</b>——同一材质槽的面在 {@code faceOffsets} 中必须连续排列。
 *       这是范围表达能成立的前提。烘焙时按材质槽重排面。</li>
 *   <li><b>槽号非负</b>——{@code materialSlot >= 0}。</li>
 *   <li><b>范围合法</b>——{@code startFace >= 0}、{@code faceCount >= 0}，
 *       且 {@code startFace + faceCount <= faceCount}（指 Mesh 的总面数）。</li>
 *   <li><b>不重叠</b>——同一 Mesh 内多个 SubMesh 的面范围互不重叠。</li>
 *   <li><b>全覆盖</b>——多个 SubMesh 的面范围合起来覆盖 Mesh 的所有面。
 *       如果存在"无槽归属"的面，需要显式表达（比如用一个特殊槽号，
 *       或允许范围不覆盖全部）。</li>
 *   <li><b>面数一致性</b>——{@code faceCount == 0} 的 SubMesh 是否允许，
 *       由构造方决定。空 SubMesh 无意义，通常应省略。</li>
 * </ul>
 *
 * <p><b>面角范围</b>：由 {@code faceOffsets} 派生——不在此存储。
 * {@link #startCorner(int[])} 和 {@link #endCorner(int[])} 通过传入
 * {@code faceOffsets} 数组查询。这样 SubMesh 不依赖 Mesh，保持纯粹。
 *
 * <p><b>不实现 COW</b>：三个 final int，天然不可变。无需 CopyOnWrite。
 */
public final class SubMesh {

    private final int materialSlot;
    private final int startFace;
    private final int faceCount;

    /**
     * @param materialSlot 材质槽号——非负
     * @param startFace    起始面索引——非负，含
     * @param faceCount    面数——非负
     */
    public SubMesh(int materialSlot, int startFace, int faceCount) {
        if (materialSlot < 0) {
            throw new IllegalArgumentException("materialSlot must be >= 0: " + materialSlot);
        }
        if (startFace < 0 || faceCount < 0) {
            throw new IllegalArgumentException("startFace/faceCount must be >= 0");
        }
        this.materialSlot = materialSlot;
        this.startFace    = startFace;
        this.faceCount    = faceCount;
    }

    /** 材质槽号。 */
    public int materialSlot() { return materialSlot; }

    /** 起始面索引——含。 */
    public int startFace() { return startFace; }

    /** 面数。 */
    public int faceCount() { return faceCount; }

    /**
     * 面角范围起点——从 {@code faceOffsets} 派生。
     *
     * @param faceOffsets Mesh 的面角偏移数组。长度 = faceCount + 1。
     */
    public int startCorner(int[] faceOffsets) {
        return faceOffsets[startFace];
    }

    /**
     * 面角范围终点（不含）——从 {@code faceOffsets} 派生。
     *
     * @param faceOffsets Mesh 的面角偏移数组。长度 = faceCount + 1。
     */
    public int endCorner(int[] faceOffsets) {
        return faceOffsets[startFace + faceCount];
    }
}