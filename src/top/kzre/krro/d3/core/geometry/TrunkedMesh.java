package top.kzre.krro.d3.core.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分块网格。
 *
 * <p>由多个 trunk（Morton 前缀块）组成——每个 trunk 持有一段顶点数据。
 * indices 数组全局共享——引用各 trunk 内的顶点。
 *
 * <p>submeshes 按材质分片。edge-table 记录拓扑边（可选，由编辑操作填充）。
 * lbvh-nodes 缓存空间索引——dirty 时重建。
 *
 * <p><b>纯数据</b>：本类不持有"选中""可见"等编辑器状态——那些由上层
 * 用外部 map / set 管理。生命周期（池化、释放、COW）由资源层管理。
 */
public final class TrunkedMesh implements IMesh {

    private final List<TrunkHandle> chunks;
    private final int[] indices;
    private final Map<Long, Integer> edgeTable;
    private final List<SubMesh>      submeshes;
    private final float[] lbvhNodes;
    private final boolean lbvhDirty;

    public TrunkedMesh(List<TrunkHandle> chunks,
                       int[] indices,
                       Map<Long, Integer> edgeTable,
                       List<SubMesh> submeshes,
                       float[] lbvhNodes,
                       boolean lbvhDirty) {
        if (chunks == null)    throw new IllegalArgumentException("chunks must not be null");
        if (indices == null)   throw new IllegalArgumentException("indices must not be null");
        if (submeshes == null) throw new IllegalArgumentException("submeshes must not be null");
        if (edgeTable == null) throw new IllegalArgumentException("edgeTable must not be null");

        this.chunks    = Collections.unmodifiableList(new ArrayList<>(chunks));
        this.indices   = indices;
        this.edgeTable = Collections.unmodifiableMap(new HashMap<>(edgeTable));
        this.submeshes = Collections.unmodifiableList(new ArrayList<>(submeshes));
        this.lbvhNodes = lbvhNodes;
        this.lbvhDirty = lbvhDirty;
    }

    // ── 访问器 ─────────────────────────────────

    public List<TrunkHandle>  getChunks()    { return chunks; }
    public int[]              getIndices()   { return indices; }
    public Map<Long, Integer> getEdgeTable() { return edgeTable; }
    public List<SubMesh>      getSubmeshes() { return submeshes; }
    public float[]            getLbvhNodes() { return lbvhNodes; }
    public boolean            isLbvhDirty()  { return lbvhDirty; }

    // ── 便捷查询 ───────────────────────────────

    /** 三角形总数（所有 submesh 之和）。 */
    public int getTriangleCount() {
        int n = 0;
        for (SubMesh s : submeshes) n += s.getTriangleCount();
        return n;
    }
}