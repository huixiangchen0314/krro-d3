package top.kzre.krro.d3.core.geometry;

/**
 * 顶点缓冲区的抽象。
 * Java 层仅通过此接口进行复制和资源释放，不感知内部布局。
 */
public interface IVertexBuffer {
    /**
     * 深拷贝当前缓冲区，返回一个新的 IVertexBuffer 实例。
     * 新缓冲区的数据与当前完全独立。
     */
    IVertexBuffer copy();

    /**
     * 释放缓冲区所占用的资源（如归还池）。
     */
    void dispose();
}