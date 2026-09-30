package top.kzre.krro.d3.core.geometry.bmesh;

import top.kzre.krro.d3.core.util.cow.CopyOnWrite;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteObject;
import top.kzre.krro.d3.core.util.cow.ListResource;
import top.kzre.krro.util.arena.AllocateResult;
import top.kzre.krro.util.arena.ArenaTemplate;
import top.kzre.krro.util.arena.FloatArenaView;
import top.kzre.krro.util.arena.IntArenaView;

import java.util.List;

/**
 * BMesh 编辑上下文——实现 {@link CopyOnWrite} 契约。
 *
 * <p><b>角色</b>——只是上下文：
 * <ul>
 *   <li>持有 {@link BMesh}——纯值容器</li>
 *   <li>持有 float / int 段模板——用于分配底层</li>
 *   <li>提供分配 / 释放索引的入口——自动确保段存在</li>
 *   <li>提供段大小 / 活跃计数查询</li>
 * </ul>
 *
 * <p><b>不做高层操作</b>：insertOrphanVertex / addFace / extrude 等
 * 拓扑操作不在本类——在单独的 ops 命名空间——它们使用 Editor 的
 * alloc / access 完成工作。
 *
 * <p><b>COW 契约</b>：
 * <ul>
 *   <li>{@link #shared()} —— 返回共享底层 BMesh 的新 Editor</li>
 *   <li>{@link #close()} —— 释放 BMesh 引用</li>
 * </ul>
 *
 * <p><b>段分配——拉模式</b>：
 * <ul>
 *   <li>不用监听器——监听器与 COW 冲突</li>
 *   <li>分配索引时显式 ensureSegment</li>
 *   <li>BMesh.getForWrite() 触发 COW——隔离</li>
 * </ul>
 *
 * <p><b>线程契约</b>：单 Editor 实例 = 单线程。
 */
public final class BMeshEditor implements CopyOnWrite<BMeshEditor> {

    // ═══════════════════════════════════════════════
    // 字段
    // ═══════════════════════════════════════════════

    private final BMesh bm;

    private final ArenaTemplate<?, FloatArenaView> floatTemplate;
    private final ArenaTemplate<?, IntArenaView> intTemplate;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public BMeshEditor(
            BMesh bm,
            ArenaTemplate<?, FloatArenaView> floatTemplate,
            ArenaTemplate<?, IntArenaView>   intTemplate) {
        if (bm == null) throw new NullPointerException("bm");
        if (floatTemplate == null) throw new NullPointerException("floatTemplate");
        if (intTemplate == null) throw new NullPointerException("intTemplate");
        this.bm = bm;
        this.floatTemplate = floatTemplate;
        this.intTemplate = intTemplate;
    }

    // ═══════════════════════════════════════════════
    // BMesh / 模板访问
    // ═══════════════════════════════════════════════

    /** 底层 BMesh——只读用途。 */
    public BMesh getBMesh() { return bm; }

    /** float 段模板——给 ops 层用。 */
    public ArenaTemplate<?, FloatArenaView> getFloatTemplate() {
        return floatTemplate;
    }

    /** int 段模板——给 ops 层用。 */
    public ArenaTemplate<?, IntArenaView> getIntTemplate() {
        return intTemplate;
    }

    // ═══════════════════════════════════════════════
    // 段大小
    // ═══════════════════════════════════════════════

    public int vertSegmentSize() { return allocatorOf(bm.vertAllocator()).getSegmentSize(); }
    public int edgeSegmentSize() { return allocatorOf(bm.edgeAllocator()).getSegmentSize(); }
    public int loopSegmentSize() { return allocatorOf(bm.loopAllocator()).getSegmentSize(); }
    public int faceSegmentSize() { return allocatorOf(bm.faceAllocator()).getSegmentSize(); }

    // ═══════════════════════════════════════════════
    // 分配索引——供 ops 层调用
    // ═══════════════════════════════════════════════

    /**
     * 分配顶点索引——确保顶点段存在。
     */
    public int allocVert() {
        SegmentizedIndexAllocator alloc =
                bm.vertAllocator().getForWrite().getAllocator();
        int index = alloc.allocate();
        int segId = alloc.segmentOf(index);

        ensureFloatSegment(bm.vertPositions(), segId, vertSegmentSize(), 3);
        ensureIntSegment(bm.vertOutEdges(), segId, vertSegmentSize(), 1);

        return index;
    }

    public int allocEdge() {
        SegmentizedIndexAllocator alloc =
                bm.edgeAllocator().getForWrite().getAllocator();
        int index = alloc.allocate();
        int segId = alloc.segmentOf(index);

        ensureIntSegment(bm.edgeEndpoints(), segId, edgeSegmentSize(), 2);
        ensureIntSegment(bm.edgeLoops(), segId, edgeSegmentSize(), 1);

        return index;
    }

    public int allocLoop() {
        SegmentizedIndexAllocator alloc =
                bm.loopAllocator().getForWrite().getAllocator();
        int index = alloc.allocate();
        int segId = alloc.segmentOf(index);

        ensureFloatSegment(bm.loopUvs(), segId, loopSegmentSize(), 2);
        ensureIntSegment(bm.loopOwnership(), segId, loopSegmentSize(), 3);
        ensureIntSegment(bm.loopRing(), segId, loopSegmentSize(), 2);
        ensureIntSegment(bm.loopRadialRing(), segId, loopSegmentSize(), 2);

        return index;
    }

    public int allocFace() {
        SegmentizedIndexAllocator alloc =
                bm.faceAllocator().getForWrite().getAllocator();
        int index = alloc.allocate();
        int segId = alloc.segmentOf(index);

        ensureFloatSegment(bm.faceNormals(), segId, faceSegmentSize(), 3);
        ensureIntSegment(bm.faceSubmeshIds(), segId, faceSegmentSize(), 1);
        ensureIntSegment(bm.faceTopology(), segId, faceSegmentSize(), 2);

        return index;
    }

// ═══════════════════════════════════════════════
// 释放索引——含段释放
// ═══════════════════════════════════════════════

    public void freeVert(int v) {
        SegmentizedIndexAllocator alloc =
                bm.vertAllocator().getForWrite().getAllocator();
        int segId = alloc.segmentOf(v);
        alloc.free(v);

        // 段空 → 释放该段的全部属性段
        if (!alloc.isSegmentActive(segId)) {
            releaseFloatSegment(bm.vertPositions(), segId);
            releaseIntSegment(bm.vertOutEdges(), segId);
        }
    }

    public void freeEdge(int e) {
        SegmentizedIndexAllocator alloc =
                bm.edgeAllocator().getForWrite().getAllocator();
        int segId = alloc.segmentOf(e);
        alloc.free(e);

        if (!alloc.isSegmentActive(segId)) {
            releaseIntSegment(bm.edgeEndpoints(), segId);
            releaseIntSegment(bm.edgeLoops(), segId);
        }
    }

    public void freeLoop(int l) {
        SegmentizedIndexAllocator alloc =
                bm.loopAllocator().getForWrite().getAllocator();
        int segId = alloc.segmentOf(l);
        alloc.free(l);

        if (!alloc.isSegmentActive(segId)) {
            releaseFloatSegment(bm.loopUvs(), segId);
            releaseIntSegment(bm.loopOwnership(), segId);
            releaseIntSegment(bm.loopRing(), segId);
            releaseIntSegment(bm.loopRadialRing(), segId);
        }
    }

    public void freeFace(int f) {
        SegmentizedIndexAllocator alloc =
                bm.faceAllocator().getForWrite().getAllocator();
        int segId = alloc.segmentOf(f);
        alloc.free(f);

        if (!alloc.isSegmentActive(segId)) {
            releaseFloatSegment(bm.faceNormals(), segId);
            releaseIntSegment(bm.faceSubmeshIds(), segId);
            releaseIntSegment(bm.faceTopology(), segId);
        }
    }
    // ═══════════════════════════════════════════════
    // 活跃计数
    // ═══════════════════════════════════════════════

    public int vertCount() { return bm.vertAllocator().getSnapshot().getAllocator().getActiveCount(); }
    public int edgeCount() { return bm.edgeAllocator().getSnapshot().getAllocator().getActiveCount(); }
    public int loopCount() { return bm.loopAllocator().getSnapshot().getAllocator().getActiveCount(); }
    public int faceCount() { return bm.faceAllocator().getSnapshot().getAllocator().getActiveCount(); }

    // ═══════════════════════════════════════════════
    // CopyOnWrite 契约
    // ═══════════════════════════════════════════════

    @Override
    public BMeshEditor shared() {
        return new BMeshEditor(bm.shared(), floatTemplate, intTemplate);
    }

    @Override
    public void close() {
        bm.close();
    }


    // ═══════════════════════════════════════════════
    // 内部——段确保
    // ═══════════════════════════════════════════════

    private static SegmentizedIndexAllocator allocatorOf(
            CopyOnWriteObject<AllocatorResource> cow) {
        return cow.getSnapshot().getAllocator();
    }

    private void ensureFloatSegment(
            CopyOnWriteObject<ListResource<CopyOnWriteFloats>> attr,
            int segId, int segSize, int fieldsPerElement) {

        List<CopyOnWriteFloats> list = attr.getForWrite().getList();
        while (list.size() <= segId) list.add(null);
        if (list.get(segId) != null) return;

        int byteSize = segSize * fieldsPerElement * Float.BYTES;
        AllocateResult<FloatArenaView> r = floatTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "Float segment allocation failed: " + byteSize);
        }
        list.set(segId, new CopyOnWriteFloats(r.getView()));
    }

    private void ensureIntSegment(
            CopyOnWriteObject<ListResource<CopyOnWriteInts>> attr,
            int segId, int segSize, int fieldsPerElement) {

        List<CopyOnWriteInts> list = attr.getForWrite().getList();
        while (list.size() <= segId) list.add(null);
        if (list.get(segId) != null) return;

        int byteSize = segSize * fieldsPerElement * Integer.BYTES;
        AllocateResult<IntArenaView> r = intTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "Int segment allocation failed: " + byteSize);
        }
        list.set(segId, new CopyOnWriteInts(r.getView()));
    }

    // ═══════════════════════════════════════════════
// 段释放——辅助
// ═══════════════════════════════════════════════

    /**
     * 释放 float 段——段槽置 null——触发 CopyOnWriteFloats.close。
     *
     * <p>释放后——段列表该槽为 null——下次 allocVert 时——
     * ensureFloatSegment 检测 null——重新分配。
     */
    private static void releaseFloatSegment(
            CopyOnWriteObject<ListResource<CopyOnWriteFloats>> attr,
            int segId) {

        List<CopyOnWriteFloats> list = attr.getForWrite().getList();
        if (segId >= list.size()) return;

        CopyOnWriteFloats seg = list.get(segId);
        if (seg == null) return;

        // close——refCount 归零——底层 Arena 段归还
        seg.close();
        list.set(segId, null);
    }

    private static void releaseIntSegment(
            CopyOnWriteObject<ListResource<CopyOnWriteInts>> attr,
            int segId) {

        List<CopyOnWriteInts> list = attr.getForWrite().getList();
        if (segId >= list.size()) return;

        CopyOnWriteInts seg = list.get(segId);
        if (seg == null) return;

        seg.close();
        list.set(segId, null);
    }
}