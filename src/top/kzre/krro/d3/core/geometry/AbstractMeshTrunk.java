package top.kzre.krro.d3.core.geometry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网格分块的抽象基类——承载通用字段与生命周期实现。
 *
 * <p><b>不可变视图</b>：所有字段 {@code final}——构造后不再修改。
 *
 * <p><b>引用计数</b>：{@link #acquire()} / {@link #release()} 管理
 * COW 引用。归零时触发 {@link #dispose()} 释放资源。
 *
 * <p><b>独占数据</b>：分块独占自己的顶点缓冲区——没有"全局顶点数组"
 * 或"全局编号空间"的概念。跨块引用由上层在索引层解决。
 */
public abstract class AbstractMeshTrunk implements IMeshTrunk {

    protected final IVertexBuffer buffer;
    protected final int startVertex;
    protected final int vertexCount;
    protected final long morton;
    protected final AtomicInteger refCount;

    protected AbstractMeshTrunk(IVertexBuffer buffer, int startVertex, int vertexCount, long morton) {
        this.buffer      = buffer;
        this.startVertex = startVertex;
        this.vertexCount = vertexCount;
        this.morton      = morton;
        this.refCount    = new AtomicInteger(1);
    }

    @Override
    public IVertexBuffer getBuffer()      { return buffer; }

    @Override
    public int getStartVertex() {
        return startVertex;
    }

    @Override
    public int           getVertexCount() { return vertexCount; }

    @Override
    public long          getMorton()      { return morton; }

    @Override
    public int           refCount()    { return refCount.get(); }

    @Override
    public int acquire() {
        return refCount.incrementAndGet();
    }

    @Override
    public int release() {
        int remaining = refCount.decrementAndGet();
        if (remaining == 0) {
            dispose();
        }
        return remaining;
    }

    @Override
    public IMeshTrunk cloneTrunk() {
        IVertexBuffer cloned = buffer.copy();
        return createClone(cloned, startVertex, vertexCount, morton);
    }

    protected abstract IMeshTrunk createClone(
            IVertexBuffer buffer, int startVertex, int vertexCount, long morton);

    protected void dispose() {
        buffer.dispose();
    }
}