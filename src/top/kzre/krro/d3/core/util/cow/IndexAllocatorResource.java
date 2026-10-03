package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.d3.core.util.IndexAllocator;

/**
 * 分配器资源——包装 {@link IndexAllocator}——
 * 引用计数 + COW 复制。
 *
 * <p>与 {@link SegmentizedIndexAllocatorResource} 的差别：
 * 底层是无分段跟踪的轻量分配器。
 */
public final class IndexAllocatorResource extends AbstractResource<IndexAllocatorResource> {

    private final IndexAllocator allocator;

    public IndexAllocatorResource(IndexAllocator allocator) {
        if (allocator == null) throw new NullPointerException("allocator");
        this.allocator = allocator;
    }

    public IndexAllocator getAllocator() {
        return allocator;
    }

    @Override
    public IndexAllocatorResource copy() {
        return new IndexAllocatorResource(allocator.copy());
    }

    @Override
    protected void onDispose() {
        // 分配器无底层资源——GC 处理
    }
}