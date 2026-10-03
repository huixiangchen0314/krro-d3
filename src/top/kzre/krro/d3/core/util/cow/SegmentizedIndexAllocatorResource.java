package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.d3.core.util.SegmentizedIndexAllocator;

/**
 * 分配器资源——包装 {@link SegmentizedIndexAllocator}——
 * 引用计数 + COW 复制。
 *
 * <p>每个 BMesh 元素类型一个——vert / edge / loop / face。
 */
public final class SegmentizedIndexAllocatorResource extends AbstractResource<SegmentizedIndexAllocatorResource> {

    private final SegmentizedIndexAllocator allocator;

    public SegmentizedIndexAllocatorResource(SegmentizedIndexAllocator allocator) {
        if (allocator == null) throw new NullPointerException("allocator");
        this.allocator = allocator;
    }

    public SegmentizedIndexAllocator getAllocator() {
        return allocator;
    }

    @Override
    public SegmentizedIndexAllocatorResource copy() {
        return new SegmentizedIndexAllocatorResource(allocator.copy());
    }

    @Override
    protected void onDispose() {
        // 分配器无底层资源——GC 处理
    }
}