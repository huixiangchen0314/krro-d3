package top.kzre.krro.d3.core.geometry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网格分块，，存储一个固定大小顶点组的几何数据。
 * <p>
 * 内部持有 {@link IVertexBuffer} 实例，通过该接口进行数据克隆和资源释放，
 * 完全不暴露底层顶点布局，保证抽象边界。
 */
public final class MeshTrunk {
    private IVertexBuffer buffer;
    private int startVertex;
    private int vertexCount;
    private long morton;
    private AABB aabb;
    private int lod;
    private boolean loaded;
    private final AtomicInteger refCount;

    /**
     * 构造一个分块。
     *
     * @param buffer      顶点缓冲区
     * @param startVertex 全局起始顶点索引
     * @param vertexCount 本块实际包含的顶点数量
     * @param morton      Morton 码（模型空间）
     * @param aabb        模型空间包围盒
     * @param lod         LOD 级别，0 为全细节
     * @param loaded      是否已加载到内存
     */
    public MeshTrunk(IVertexBuffer buffer, int startVertex, int vertexCount,
                     long morton, AABB aabb, int lod, boolean loaded) {
        this.buffer = buffer;
        this.startVertex = startVertex;
        this.vertexCount = vertexCount;
        this.morton = morton;
        this.aabb = aabb;
        this.lod = lod;
        this.loaded = loaded;
        this.refCount = new AtomicInteger(1); // 新建分块独占
    }

    // ────────────── 属性访问（只读）──────────────
    public IVertexBuffer getBuffer() { return buffer; }
    public int getStartVertex() { return startVertex; }
    public int getVertexCount() { return vertexCount; }
    public long getMorton() { return morton; }
    public AABB getAabb() { return aabb; }
    public int getLod() { return lod; }
    public boolean isLoaded() { return loaded; }
    public int getRefCount() { return refCount.get(); }

    // 允许更新元数据（编辑后调用）
    public void setMorton(long morton) { this.morton = morton; }
    public void setAabb(AABB aabb) { this.aabb = aabb; }
    public void setLod(int lod) { this.lod = lod; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }
    public void setStartVertex(int startVertex) { this.startVertex = startVertex; }
    public void setVertexCount(int vertexCount) { this.vertexCount = vertexCount; }

    // ────────────── 引用计数（类似 TileData）──────────────
    public int acquire() {
        return refCount.incrementAndGet();
    }

    public int release() {
        int remaining = refCount.decrementAndGet();
        if (remaining == 0) {
            dispose();
        }
        return remaining;
    }

    /**
     * 克隆当前分块，返回一个新的 MeshTrunk，拥有独立的顶点缓冲区副本。
     * 新分块的引用计数为 1，元数据与原分块一致。
     */
    public MeshTrunk cloneTrunk() {
        IVertexBuffer clonedBuffer = buffer.cloneBuffer(); // 池化克隆
        return new MeshTrunk(clonedBuffer, startVertex, vertexCount,
                morton, aabb, lod, loaded);
    }

    /**
     * 释放内部缓冲区资源（归还池），仅在引用计数归零时调用。
     */
    private void dispose() {
        if (buffer != null) {
            buffer.dispose();
            buffer = null;
        }
    }

    // 可选：方便 Clojure 侧调用的一些辅助方法
    @Override
    public String toString() {
        return "MeshTrunk[start=" + startVertex + ", count=" + vertexCount +
                ", morton=" + morton + ", lod=" + lod + "]";
    }
}