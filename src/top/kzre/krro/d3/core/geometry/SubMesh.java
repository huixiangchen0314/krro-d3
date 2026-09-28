package top.kzre.krro.d3.core.geometry;

/**
 * 子网格——一个材质分片。
 *
 * <p>同一 Mesh 内不同区域可以用不同材质。每个 SubMesh 描述一段连续的
 * 索引区间——对应某个材质。渲染时按 SubMesh 切分 draw call。
 *
 * <p><b>纯数据</b>：material 标识 + 索引区间。材质实体本身在别的层。
 */
public final class SubMesh {

    private final Object materialId;
    private final int    startIndex;
    private final int    triangleCount;

    /**
     * @param materialId     材质标识——类型由上层定义
     * @param startIndex     全局 indices 数组中的起始下标
     * @param triangleCount  三角形数——实际索引数 = count * 3
     */
    public SubMesh(Object materialId, int startIndex, int triangleCount) {
        if (triangleCount < 0) {
            throw new IllegalArgumentException("triangleCount must be >= 0");
        }
        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex must be >= 0");
        }
        this.materialId    = materialId;
        this.startIndex    = startIndex;
        this.triangleCount = triangleCount;
    }

    public Object getMaterialId()    { return materialId; }
    public int    getStartIndex()    { return startIndex; }
    public int    getTriangleCount() { return triangleCount; }

    /** 索引区间长度——三角形数 × 3。 */
    public int getIndexCount() { return triangleCount * 3; }
}