package top.kzre.krro.d3.core.geometry;

/**
 * 基础网格分块——承载顶点位置、法线、UV、蒙皮。
 *
 * <p>所有逻辑由 {@link AbstractMeshTrunk} 提供——本类只负责构造
 * 与克隆工厂。
 */
public final class DefaultMeshTrunk extends AbstractMeshTrunk {

    public DefaultMeshTrunk(IVertexBuffer buffer, int startVertex,
                     int vertexCount, long morton) {
        super(buffer, startVertex, vertexCount, morton);
    }

    @Override
    protected IMeshTrunk createClone(IVertexBuffer buffer, int startVertex,
                                     int vertexCount, long morton) {
        return new DefaultMeshTrunk(buffer, startVertex, vertexCount, morton);
    }

    @Override
    public String toString() {
        return "MeshTrunk[start=" + startVertex
                + ", count=" + vertexCount
                + ", morton=" + morton + "]";
    }
}