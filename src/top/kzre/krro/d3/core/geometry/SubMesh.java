package top.kzre.krro.d3.core.geometry;

/**
 * 子网格——材质分组。
 *
 * <p>按面范围划分——范围 + 材质引用。
 * 面角范围由 {@code face_offsets} 派生。
 */
public final class SubMesh {

    private final Object materialId;
    private final int    startFace;
    private final int    faceCount;

    public SubMesh(Object materialId, int startFace, int faceCount) {
        if (materialId == null) throw new NullPointerException("materialId");
        if (startFace < 0 || faceCount < 0) {
            throw new IllegalArgumentException("startFace/faceCount must be >= 0");
        }
        this.materialId = materialId;
        this.startFace  = startFace;
        this.faceCount  = faceCount;
    }

    public Object materialId() { return materialId; }
    public int    startFace()  { return startFace; }
    public int    faceCount()  { return faceCount; }

    /** 面角范围起点——从 face_offsets 派生。 */
    public int startCorner(int[] faceOffsets) {
        return faceOffsets[startFace];
    }

    /** 面角范围终点（不含）。 */
    public int endCorner(int[] faceOffsets) {
        return faceOffsets[startFace + faceCount];
    }
}