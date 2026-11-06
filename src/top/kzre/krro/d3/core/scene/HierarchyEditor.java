package top.kzre.krro.d3.core.scene;

import top.kzre.krro.d3.core.scene.algo.PreMultiTransform;
import top.kzre.krro.d3.core.util.LongList;
import top.kzre.krro.d3.core.util.LongLongMap;
import top.kzre.krro.d3.core.util.LongObjectMap;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.*;
import top.kzre.krro.util.arena.ArenaTemplate;
import top.kzre.krro.util.arena.FloatArenaView;
import top.kzre.krro.util.arena.AllocateResult;
import top.kzre.krro.util.math.KMath;

import java.util.List;

/**
 * 场景编辑器——编辑操作 + 派生缓存 + 脏标记。
 *
 * <p><b>职责</b>：
 * <ul>
 *   <li>所有编辑操作走本类——写 {@link Hierarchy} 的本质数据</li>
 *   <li>编辑操作触发脏标记，{@link #getGlobalTransforms()} 时全量重算脏子树</li>
 *   <li>持有 preTransforms / invTransforms 缓存，生命周期和编辑器一致</li>
 *   <li>对外的查询接口——{@link Hierarchy} 不再暴露</li>
 * </ul>
 *
 * <p><b>两种刷新语义</b>：
 * <ul>
 *   <li><b>局部刷新</b>：{@link #getLocalTransform} / {@link #getGlobalTransform} /
 *       {@link #getInvGlobalTransform}。查询一个节点时只刷新它所在的脏子树，
 *       不清空其他脏标记——查一个节点不触发全部重算。</li>
 *   <li><b>全量刷新</b>：{@link #getGlobalTransforms()} / {@link #getInvTransforms()}。
 *       显式调用 = 全量刷新所有脏子树。渲染前统一刷新走这个。</li>
 * </ul>
 *
 * <p><b>全局与逆缓存的刷新策略不同</b>：
 * <ul>
 *   <li><b>全局变换</b>：刷新时重算——「全量查询」是常态。</li>
 *   <li><b>逆变换</b>：<b>懒初始化 + 按需回填 + 刷新时只失效不重算</b>。
 *       首次 {@link #getInvGlobalTransform} 调用时建立一个<b>空</b>缓存
 *       （不对任何节点做 {@code mat4Inv}），之后每个被查询的节点在
 *       缓存未命中时单节点现算并回填。全局刷新时只 remove + close
 *       脏子树内的条目，不做求逆——成本推迟到真正查询的节点上。
 *       因此「逆只查几个节点」的工作流不会因为全局刷新触发整棵脏子树的
 *       {@code mat4Inv}，而「逆查询频繁」的节点在首次查询后命中缓存。</li>
 * </ul>
 *
 * <p><b>不可逆</b>：全局变换不可逆视为数据错误。{@link KMath#mat4Inv} 在
 * 不可逆时直接抛异常，本类不捕获——编辑器无法从不可逆的变换中恢复，
 * 所有涉及求逆的路径（invTransforms 全量初始化、单节点逆查询、
 * reparentKeepTransform）都让异常自然传播。
 *
 * <p><b>脏标记不变量</b>：{@link #markDirty} 保证 dirtyNodes 里任意两个节点
 * 互不为祖先。因此查询 X 时沿父链找到的最近 dirty 节点 D 唯一——
 * 它就是包含 X 的最小脏子树的根。</p>
 *
 * <p><b>删除语义</b>：{@link #removeNode} 从 Scene 中移除节点及其子树，
 * 返回被移除的 {@link SceneObject} 列表——<b>由调用者决定如何释放</b>。
 *
 * <p><b>线程契约</b>：非线程安全。多线程通过 {@link #shared()} 获取副本，
 * 各自的脏标记独立。
 */
public final class HierarchyEditor implements CopyOnWrite<HierarchyEditor> {
    private static final float[] MAT4_IDENTITY = KMath.mat4Identity();
    private static final Object DIRTY_MARKER = new Object();
    public static final int MAT4_LENGTH = 16;

    private final Hierarchy hierarchy;
    private final ArenaTemplate<?, FloatArenaView> floatArena;

    /** 全局变换缓存。null 表示未计算。 */
    private CopyOnWriteObject<LongObjectMapResource<CopyOnWriteFloats>> globalTransforms;

    /**
     * 全局逆变换缓存。null 表示尚未懒初始化。
     *
     * <p><b>懒初始化</b>：首次 {@link #getInvGlobalTransform} 调用时建为空缓存，
     * 之后按查询逐步回填。全局刷新时只失效条目，不重算。
     */
    private CopyOnWriteObject<LongObjectMapResource<CopyOnWriteFloats>> invTransforms;

    private final LongObjectMap<Object> dirtyNodes = new LongObjectMap<>();

    private final float[] mat4Scratch0 = new float[MAT4_LENGTH];
    private final float[] mat4Scratch1 = new float[MAT4_LENGTH];

    private HierarchyEditor(Hierarchy hierarchy, ArenaTemplate<?, FloatArenaView> floatArena) {
        if (hierarchy == null) throw new NullPointerException("hierarchy");
        if (floatArena == null) throw new NullPointerException("floatTemplate");
        this.hierarchy = hierarchy;
        this.floatArena = floatArena;
    }

    public static HierarchyEditor create(
            long sceneId,
            ArenaTemplate<?, FloatArenaView> floatArena) {
        return new HierarchyEditor(
                Hierarchy.create(sceneId),
                floatArena
        );
    }

    public long getSceneId() {
        return hierarchy.getSceneId();
    }

    /**
     * 只读约定
     */
    public LongObjectMap<SceneObject> objects(){
      return  hierarchy.objects().getSnapshot().getMap();
    }


    /**
     * 可写的 COW 副本
     */
    public LongObjectMap<SceneObject> objectsForWrite(){
        return  hierarchy.objects().getForWrite().getMap();
    }

    /**
     * 只读约定
     */
    public LongObjectMap<CopyOnWriteFloats> globalTransforms(){
        return getGlobalTransforms().getSnapshot().getMap();
    }

    /**
     * 只读约定
     */
    public LongObjectMap<CopyOnWriteFloats> invTransforms(){
        return getInvTransforms().getSnapshot().getMap();
    }

    // ═══════════════════════════════════════════════
    // 查询——拓扑
    // ═══════════════════════════════════════════════

    public long getParent(long nodeId) {
        return hierarchy.parent().getSnapshot().getMap().get(nodeId);
    }

    public boolean isRoot(long nodeId) {
        return LongLongMap.isUnitValue(getParent(nodeId));
    }

    public long getFirstChild(long nodeId) {
        return hierarchy.firstChild().getSnapshot().getMap().get(nodeId);
    }

    public long getNextSibling(long nodeId) {
        return hierarchy.nextSibling().getSnapshot().getMap().get(nodeId);
    }

    public long getPrevSibling(long nodeId) {
        return hierarchy.prevSibling().getSnapshot().getMap().get(nodeId);
    }

    public SceneObject getObject(long nodeId) {
        return hierarchy.objects().getSnapshot().getMap().get(nodeId);
    }

    public boolean containsNode(long nodeId) {
        return hierarchy.objects().getSnapshot().getMap().containsKey(nodeId);
    }

    public int getRootCount() {
        return hierarchy.roots().getSnapshot().getList().size();
    }

    public long getRoot(int index) {
        return hierarchy.roots().getSnapshot().getList().get(index);
    }

    public int getChildCount(long parentId) {
        LongLongMap firstChild = hierarchy.firstChild().getSnapshot().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getSnapshot().getMap();
        int count = 0;
        long cur = firstChild.get(parentId);

        while (!LongLongMap.isUnitValue(cur)) {
            count++;
            cur = nextSibling.get(cur);
        }
        return count;
    }

    public boolean hasChildren(long nodeId) {
        long head = hierarchy.firstChild().getSnapshot().getMap().get(nodeId);
        return !LongLongMap.isUnitValue(head);
    }

    /**
     * 收集 {@code nodeId} 的<b>直接子节点</b> id（不含孙节点），写入 {@code out}。
     *
     * <p>与 {@link #collectSubtreeIds(long, LongList)} 的区别：
     * <ul>
     *   <li>{@code collectSubtreeIds}：收集整个子树（含自身）</li>
     *   <li>{@code collectChildIds}：只收集一层直接子节点（不含自身）</li>
     * </ul>
     *
     * <p>顺序与场景图中的兄弟顺序一致（{@code firstChild} 起沿 {@code nextSibling} 链）。
     *
     * <p><b>nodeId 不存在</b>：{@code firstChild.get(nodeId)} 返回
     * {@link LongLongMap} 的 unit value，循环不进入，{@code out} 被清空后返回。
     * 与 {@link #getChildCount(long)} 对不存在节点的语义一致（0 个子节点）。
     *
     * <p>复杂度：O(k)，k = 直接子节点数。
     *
     * @param nodeId 父节点 id；不存在时得到空列表
     * @param out    输出列表，非 null，会被 {@code clear()}
     * @return {@code out} 本身，便于链式调用
     */
    public LongList collectChildIds(long nodeId, LongList out) {
        out.clear();

        LongLongMap firstChild = hierarchy.firstChild().getSnapshot().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getSnapshot().getMap();

        long childId = firstChild.get(nodeId);
        while (!LongLongMap.isUnitValue(childId)) {
            out.add(childId);
            childId = nextSibling.get(childId);
        }
        return out;
    }

    public LongList collectRootIds(LongList out) {
        out.clear();
        LongList roots = hierarchy.roots().getSnapshot().getList();
        for (int i = 0, n = roots.size(); i < n; i++) {
            out.add(roots.get(i));
        }
        return out;
    }

    public int getDepth(long nodeId) {
        LongLongMap parentMap = hierarchy.parent().getSnapshot().getMap();
        if (!containsNode(nodeId)) return -1;
        int depth = 0;
        long p = parentMap.get(nodeId);
        while (!LongLongMap.isUnitValue(p)) {
            depth++;
            p = parentMap.get(p);
        }
        return depth;
    }

    public LongList collectSubtreeIds(long nodeId, LongList out) {
        out.clear();
        collectSubtree(nodeId, out);
        return out;
    }

    public LongList collectAllIds(LongList out) {
        out.clear();
        int n = getRootCount();
        for (int i = 0; i < n; i++) {
            collectSubtree(getRoot(i), out);
        }
        return out;
    }

    // ═══════════════════════════════════════════════
    // 查询——变换（out 写法，局部刷新）
    // ═══════════════════════════════════════════════

    /**
     * 读取局部变换到 {@code out}。节点不存在时返回 false，out 不变。
     */
    public boolean getLocalTransform(long nodeId, float[] out) {
        if (out == null || out.length < MAT4_LENGTH) {
            throw new IllegalArgumentException("out must be float[>=16]");
        }
        CopyOnWriteFloats cow = hierarchy.transforms().getSnapshot().getMap().get(nodeId);
        if (cow == null) return false;
        try (FloatArenaView.ArraySession session = cow.array()) {
            System.arraycopy(session.getFloats(), session.offset(), out, 0, MAT4_LENGTH);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        return true;
    }

    /**
     * 读取全局变换到 {@code out}。节点不存在时返回 false，out 不变。
     *
     * <p><b>局部刷新</b>：只刷新 nodeId 所在的脏子树，不清空其他脏标记。
     * 虽然更新范围还是比实际的大，但globalTransform 是几乎每帧必计算，所以可接受
     * <br>
     * 渲染前需要统一刷新时，显式调 {@link #getGlobalTransforms()}。
     */
    public boolean getGlobalTransform(long nodeId, float[] out) {
        if (out == null || out.length < MAT4_LENGTH) {
            throw new IllegalArgumentException("out must be float[>=16]");
        }
        ensureGlobalFresh(nodeId);
        CopyOnWriteFloats cow = globalTransforms.getSnapshot().getMap().get(nodeId);
        if (cow == null) return false;
        try (FloatArenaView.ArraySession session = cow.array()) {
            System.arraycopy(session.getFloats(), session.offset(), out, 0, MAT4_LENGTH);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        return true;
    }

    /**
     * 读取全局逆变换到 {@code out}。节点不存在时返回 false，out 不变。
     *
     * <p><b>懒初始化 + 按需回填</b>：
     * <ul>
     *   <li>invTransforms 尚未初始化（null）：建立<b>空</b>缓存——不对任何节点做
     *       {@code mat4Inv}——然后走下面的「未命中→现算回填」路径。
     *       首次查询即是缓存建立的时刻，此后缓存持续生效。</li>
     *   <li>缓存命中：O(1) 读。</li>
     *   <li>缓存未命中（首次查该节点、或全局刷新后失效）：从最新全局变换
     *       单节点现算，写回缓存。同一节点在下一次全局刷新前再次查询即命中。</li>
     * </ul>
     *
     * <p>因此本方法<b>不依赖</b> {@link #getInvTransforms()} 的显式调用——
     * 后者是全量预热的可选入口，日常局部查询走本方法即可让缓存工作。
     *
     * <p><b>不可逆直接抛</b>：{@link KMath#mat4Inv} 的异常原样传播，
     * 不写缓存、不返回 false。
     */
    public boolean getInvGlobalTransform(long nodeId, float[] out) {
        if (out == null || out.length < MAT4_LENGTH) {
            throw new IllegalArgumentException("out must be float[>=16]");
        }
        ensureGlobalFresh(nodeId);

        CopyOnWriteFloats globalCow = globalTransforms.getSnapshot().getMap().get(nodeId);
        if (globalCow == null) return false;

        // 懒初始化：空缓存，不预先对任何节点做 mat4Inv
        if (invTransforms == null) {
            invTransforms = new DefaultCopyOnWriteObject<>(
                    new LongObjectMapResource<>(new LongObjectMap<>()));
        }

        CopyOnWriteFloats invCow = invTransforms.getSnapshot().getMap().get(nodeId);
        if (invCow != null) {
            try (FloatArenaView.ArraySession session = invCow.array()) {
                System.arraycopy(session.getFloats(), session.offset(), out, 0, MAT4_LENGTH);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }

            return true;
        }

        // 未命中：从最新全局单节点现算并回填。先算后替换——不可逆时
        // out 不被污染、缓存不被写入。
        float[] g = mat4Scratch0;
        try (FloatArenaView.ArraySession session = globalCow.array()) {
            System.arraycopy(session.getFloats(), session.offset(), g, 0, MAT4_LENGTH);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        KMath.mat4Inv(g, out);  // 不可逆直接抛

        LongObjectMap<CopyOnWriteFloats> invOut = invTransforms.getForWrite().getMap();
        CopyOnWriteFloats raced = invOut.get(nodeId);
        if (raced != null) {
            // 快照边界：已被填充，用已有的
            try (FloatArenaView.ArraySession session = raced.array()) {
                System.arraycopy(session.getFloats(), session.offset(),
                        out, 0, MAT4_LENGTH);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }

        } else {
            float[] store = mat4Scratch1;
            System.arraycopy(out, 0, store, 0, MAT4_LENGTH);
            invOut.put(nodeId, allocFloats(store));
        }
        return true;
    }

    /**
     * {@code node} 是否是 {@code ancestor} 的后代（含自身）。
     *
     * <p>拖拽 reparent 前用它做合法性预览，避免触发异常。
     */
    public boolean isDescendantOf(long node, long ancestor) {
        return isDescendant(node, ancestor);
    }

    /**
     * {@code ancestor} 是否是 {@code node} 的祖先（含自身）。
     */
    public boolean isAncestorOf(long ancestor, long node) {
        return isDescendant(node, ancestor);
    }

    /**
     * 返回节点在兄弟中的下标。根节点返回其在 roots 中的下标。
     *
     * @return {@code [0, siblingCount)}；节点不存在返回 -1
     */
    public int getSiblingIndex(long nodeId) {
        if (!containsNode(nodeId)) return -1;

        long parentId = hierarchy.parent().getSnapshot().getMap().get(nodeId);
        if (LongLongMap.isUnitValue(parentId)) {
            LongList roots = hierarchy.roots().getSnapshot().getList();
            for (int i = 0, n = roots.size(); i < n; i++) {
                if (roots.get(i) == nodeId) return i;
            }
            return -1;
        }

        LongLongMap firstChild = hierarchy.firstChild().getSnapshot().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getSnapshot().getMap();
        int index = 0;
        long cur = firstChild.get(parentId);
        while (!LongLongMap.isUnitValue(cur)) {
            if (cur == nodeId) return index;
            index++;
            cur = nextSibling.get(cur);
        }
        return -1;
    }


    // ═══════════════════════════════════════════════
    // 编辑操作
    // ═══════════════════════════════════════════════

    public void setLocalTransform(long nodeId, float[] mat4) {
        if (mat4 == null || mat4.length != MAT4_LENGTH) {
            throw new IllegalArgumentException("mat4 must be float[16]");
        }
        if (!containsNode(nodeId)) {
            throw new IllegalStateException("node not found: " + nodeId);
        }
        LongObjectMap<CopyOnWriteFloats> t = hierarchy.transforms().getForWrite().getMap();
        CopyOnWriteFloats old = t.get(nodeId);
        if (old != null) {
            try {
                old.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        t.put(nodeId, allocFloats(mat4));
        markDirty(nodeId);
    }

    /**
     * 设置节点的全局变换，内部换算成局部变换。
     *
     * <p>公式：{@code local = inv(parentGlobal) * global}。根节点父全局取单位阵。
     *
     * <p><b>父全局不可逆直接抛</b>：{@link KMath#mat4Inv} 的异常原样传播，
     * 局部变换不被修改。
     *
     * @param nodeId 节点 id，必须存在
     * @param mat4   目标全局变换，float[16]
     * @throws IllegalStateException    nodeId 不存在
     * @throws IllegalArgumentException mat4 为 null 或长度不是 16
     */
    public void setGlobalTransform(long nodeId, float[] mat4) {
        if (mat4 == null || mat4.length != MAT4_LENGTH) {
            throw new IllegalArgumentException("mat4 must be float[16]");
        }
        if (!containsNode(nodeId)) {
            throw new IllegalStateException("node not found: " + nodeId);
        }

        long parentId = hierarchy.parent().getSnapshot().getMap().get(nodeId);

        float[] parentInv;
        if (LongLongMap.isUnitValue(parentId)) {
            parentInv = MAT4_IDENTITY;   // 根：local = global
        } else {
            float[] parentGlobal = new float[MAT4_LENGTH];
            if (!getGlobalTransform(parentId, parentGlobal)) {
                throw new IllegalStateException("parent not found: " + parentId);
            }
            parentInv = new float[MAT4_LENGTH];
            KMath.mat4Inv(parentGlobal, parentInv);  // 不可逆直接抛
        }

        float[] local = new float[MAT4_LENGTH];
        KMath.mat4Mul(parentInv, mat4, local);
        setLocalTransform(nodeId, local);
    }

    public void addRoot(SceneObject obj) {
        addRoot(obj, MAT4_IDENTITY);
    }

    public void addRoot(SceneObject obj, float[] mat4) {
        addOrphanNode(obj);
        assocRoot(obj.getId(), mat4);
    }

    public void addChild(long parentId, SceneObject obj) {
        addChild(parentId, obj, MAT4_IDENTITY);
    }

    public void addChild(long parentId, SceneObject obj, float[] mat4) {
        if (!containsNode(parentId)) {
            throw new IllegalStateException("parent not found: " + parentId);
        }

        addOrphanNode(obj);
        assocChild(parentId, obj.getId(), mat4);
    }

    private void addOrphanNode(SceneObject obj) {
        long objId = obj.getId();
        LongObjectMap<SceneObject> map = hierarchy.objects().getForWrite().getMap();
        if (map.get(objId) != null) {
            throw new IllegalStateException("Scene object already exists: " + objId);
        }
        map.put(objId, obj);
    }

    private void assocRoot(long nodeId, float[] mat4) {
        if (mat4 == null || mat4.length != MAT4_LENGTH) {
            throw new IllegalArgumentException("mat4 must be float[16]");
        }
        hierarchy.transforms().getForWrite().getMap().put(nodeId, allocFloats(mat4));
        hierarchy.roots().getForWrite().getList().add(nodeId);
        markDirty(nodeId);
    }

    private void assocChild(long parentId, long childId, float[] mat4) {
        if (mat4 == null || mat4.length != MAT4_LENGTH) {
            throw new IllegalArgumentException("mat4 must be float[16]");
        }
        hierarchy.transforms().getForWrite().getMap().put(childId, allocFloats(mat4));
        hierarchy.parent().getForWrite().getMap().put(childId, parentId);

        LongLongMap firstChild = hierarchy.firstChild().getForWrite().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getForWrite().getMap();
        LongLongMap prevSibling = hierarchy.prevSibling().getForWrite().getMap();

        long head = firstChild.get(parentId);
        if (LongLongMap.isUnitValue(head)) {
            firstChild.put(parentId, childId);
            prevSibling.put(childId, LongLongMap.UNIT_VALUE);
        } else {
            long last = head;
            long next = nextSibling.get(last);
            while (!LongLongMap.isUnitValue(next)) {
                last = next;
                next = nextSibling.get(last);
            }
            nextSibling.put(last, childId);
            prevSibling.put(childId, last);
        }
        nextSibling.put(childId, LongLongMap.UNIT_VALUE);

        markDirty(childId);
    }

    public List<SceneObject> removeNode(long nodeId, List<SceneObject> removed) {
        if (!containsNode(nodeId)) {
            throw new IllegalStateException("node not found: " + nodeId);
        }

        LongList subtreeIds = new LongList();
        collectSubtree(nodeId, subtreeIds);

        LongObjectMap<SceneObject> objects = hierarchy.objects().getForWrite().getMap();
        for (int i = 0; i < subtreeIds.size(); i++) {
            SceneObject obj = objects.get(subtreeIds.get(i));
            if (obj != null) removed.add(obj);
        }

        removeSubtreeFromScene(nodeId);

        if (globalTransforms != null) {
            LongObjectMap<CopyOnWriteFloats> out = globalTransforms.getForWrite().getMap();
            for (int i = 0; i < subtreeIds.size(); i++) {
                CopyOnWriteFloats old = out.remove(subtreeIds.get(i));
                if (old != null) {
                    try {
                        old.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        if (invTransforms != null) {
            LongObjectMap<CopyOnWriteFloats> inv = invTransforms.getForWrite().getMap();
            for (int i = 0; i < subtreeIds.size(); i++) {
                CopyOnWriteFloats old = inv.remove(subtreeIds.get(i));
                if (old != null) {
                    try {
                        old.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return removed;
    }

    /**
     * 删除节点自身，但保留其直接子节点——子节点上提到被删节点的父级
     * （或变为根节点），并<b>保持各自的全局变换不变</b>。
     *
     * <p><b>语义</b>：
     * <ul>
     *   <li>只有 {@code nodeId} 自身被删除，{@code removed} 里只含它一个对象</li>
     *   <li>直接子节点按原顺序插入到 {@code nodeId} 原来的位置</li>
     *   <li>子节点的新局部变换 = {@code local(nodeId) * local(child)}</li>
     *   <li>子节点及其子树标记为脏，下次刷新重算全局</li>
     * </ul>
     *
     * <p><b>为什么不用求逆</b>：设 N 的父为 P，子为 C。
     * <pre>
     *   G_C = G_P * L_N * L_C
     *   C 提升到 P 后要求 G_C 不变，即 G_P * L_C' = G_P * L_N * L_C
     *   ⇒ L_C' = L_N * L_C
     * </pre>
     * 由矩阵乘法结合律直接消去 {@code G_P}，<b>不需要 inv(G_P)</b>，
     * 因此父全局不可逆时也能正确保持全局变换。
     *
     * <p><b>nodeId 不存在直接抛</b>——编辑操作 fail-fast。
     *
     * @param nodeId  要删除的节点 id
     * @param removed 被删除的对象写入此列表（只含 nodeId 自身）
     * @return {@code removed}
     * @throws IllegalStateException nodeId 不存在
     */
    public List<SceneObject> removeNodeKeepChildren(long nodeId, List<SceneObject> removed) {
        if (!containsNode(nodeId)) {
            throw new IllegalStateException("node not found: " + nodeId);
        }

        // ── 1. 收集直接子节点（保持顺序）与 nodeId 的局部变换 ──
        LongList children = collectChildIds(nodeId, new LongList());
        int childCount = children.size();

        float[] nodeLocal = null;
        if (childCount > 0) {
            nodeLocal = new float[MAT4_LENGTH];
            if (!getLocalTransform(nodeId, nodeLocal)) {
                throw new IllegalStateException("local transform missing for node: " + nodeId);
            }
        }

        // ── 2. 拓扑：把子链接到 nodeId 原来的位置，摘下 nodeId ──
        LongLongMap parentMap = hierarchy.parent().getForWrite().getMap();
        LongLongMap firstChild = hierarchy.firstChild().getForWrite().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getForWrite().getMap();
        LongLongMap prevSibling = hierarchy.prevSibling().getForWrite().getMap();

        long parentId = parentMap.get(nodeId);
        long prev = prevSibling.get(nodeId);
        long next = nextSibling.get(nodeId);

        if (childCount == 0) {
            // 无子节点：等价于 removeNode 的单节点情况
            detachFromParent(nodeId);
        } else if (LongLongMap.isUnitValue(parentId)) {
            // nodeId 是根：子节点按原顺序替换它在 roots 中的位置
            LongList roots = hierarchy.roots().getForWrite().getList();
            int n = roots.size();
            int idx = -1;
            for (int i = 0; i < n; i++) {
                if (roots.get(i) == nodeId) { idx = i; break; }
            }
            if (idx < 0) {
                throw new IllegalStateException("node not in roots: " + nodeId);
            }
            roots.removeAt(idx);
            for (int i = childCount - 1; i >= 0; i--) {
                long c = children.get(i);
                roots.addAt(idx, c);          // 逆序插入以保持原顺序
                parentMap.remove(c);          // 子节点变根
                nextSibling.remove(c);        // 根不维护兄弟链
                prevSibling.remove(c);
            }
        } else {
            // nodeId 是子节点：把子链接到 nodeId 的 prev/next 之间
            long first = firstChild.get(nodeId);
            long last = first;
            while (!LongLongMap.isUnitValue(nextSibling.get(last))) {
                last = nextSibling.get(last);
            }

            for (int i = 0; i < childCount; i++) {
                parentMap.put(children.get(i), parentId);
            }

            // 前驱接入
            if (LongLongMap.isUnitValue(prev)) {
                firstChild.put(parentId, first);
                prevSibling.remove(first);
            } else {
                nextSibling.put(prev, first);
                prevSibling.put(first, prev);
            }
            // 后继接入
            if (LongLongMap.isUnitValue(next)) {
                nextSibling.remove(last);
            } else {
                nextSibling.put(last, next);
                prevSibling.put(next, last);
            }
        }

        // nodeId 自身的链接清理（幂等，覆盖所有分支）
        parentMap.remove(nodeId);
        nextSibling.remove(nodeId);
        prevSibling.remove(nodeId);
        firstChild.remove(nodeId);

        // ── 3. 重算并写入子节点新局部：L_child' = L_node * L_child ──
        //    此时拓扑已改，markDirty 从新父链走，不会误挂到 nodeId 的脏标记上
        if (childCount > 0) {
            for (int i = 0; i < childCount; i++) {
                long c = children.get(i);
                // 清除子节点子树内可能冲突的脏标记：这些节点会被 c 的刷新覆盖，
                // 若与 c 同时留在 dirtyNodes 会破坏「互不为祖先」不变量，
                // 导致 flushDirty 的 forEach 顺序不确定时用旧父全局算错。
                if (!dirtyNodes.isEmpty()) {
                    clearDirtyInSubtree(c);
                }
                float[] oldLocal = new float[MAT4_LENGTH];
                if (!getLocalTransform(c, oldLocal)) {
                    throw new IllegalStateException("local transform missing for child: " + c);
                }
                float[] newLocal = new float[MAT4_LENGTH];
                KMath.mat4Mul(nodeLocal, oldLocal, newLocal);
                setLocalTransform(c, newLocal);   // 内部 markDirty
            }
        }

        // ── 4. 清理 nodeId 自身：transforms / objects / 缓存 / 脏标记 ──
        CopyOnWriteFloats oldT = hierarchy.transforms().getForWrite().getMap().remove(nodeId);
        if (oldT != null) {
            try { oldT.close(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        SceneObject obj = hierarchy.objects().getForWrite().getMap().remove(nodeId);
        if (obj != null) removed.add(obj);

        if (globalTransforms != null) {
            CopyOnWriteFloats old = globalTransforms.getForWrite().getMap().remove(nodeId);
            if (old != null) {
                try { old.close(); } catch (Exception e) { throw new RuntimeException(e); }
            }
        }
        if (invTransforms != null) {
            CopyOnWriteFloats old = invTransforms.getForWrite().getMap().remove(nodeId);
            if (old != null) {
                try { old.close(); } catch (Exception e) { throw new RuntimeException(e); }
            }
        }
        dirtyNodes.remove(nodeId);

        return removed;
    }

    /** 递归清除子树内所有脏标记。拓扑修改后调用，用当前 firstChild/nextSibling 链。 */
    private void clearDirtyInSubtree(long nodeId) {
        dirtyNodes.remove(nodeId);
        LongLongMap firstChild = hierarchy.firstChild().getSnapshot().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getSnapshot().getMap();
        long c = firstChild.get(nodeId);
        while (!LongLongMap.isUnitValue(c)) {
            clearDirtyInSubtree(c);
            c = nextSibling.get(c);
        }
    }

    private void collectSubtree(long nodeId, LongList out) {
        out.add(nodeId);
        LongLongMap firstChild = hierarchy.firstChild().getSnapshot().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getSnapshot().getMap();
        long childId = firstChild.get(nodeId);
        while (!LongLongMap.isUnitValue(childId)) {
            collectSubtree(childId, out);
            childId = nextSibling.get(childId);
        }
    }


    /**
     * 设置节点的兄弟位置。
     *
     * <p>局部变换不变，全局变换不变——<b>不</b> markDirty。
     *
     * @param nodeId 节点 id，可能为 Root
     * @param index  目标下标 [0, nsiblings)
     */
    public void setSiblingIndex(long nodeId, int index) {
        if (!containsNode(nodeId)) {
            throw new IllegalStateException("node not found: " + nodeId);
        }

        long parentId = hierarchy.parent().getSnapshot().getMap().get(nodeId);

        if (LongLongMap.isUnitValue(parentId)) {
            LongList roots = hierarchy.roots().getForWrite().getList();
            int n = roots.size();
            int currentIndex = -1;
            for (int i = 0; i < n; i++) {
                if (roots.get(i) == nodeId) { currentIndex = i; break; }
            }
            if (currentIndex < 0) {
                throw new IllegalStateException("node not in roots: " + nodeId);
            }
            if (index < 0 || index >= n) {
                throw new IllegalArgumentException("index out of range [0, " + n + "): " + index);
            }
            if (index == currentIndex) return;
            roots.removeAt(currentIndex);
            roots.addAt(index, nodeId);
            return;
        }

        LongLongMap firstChild = hierarchy.firstChild().getForWrite().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getForWrite().getMap();
        LongLongMap prevSibling = hierarchy.prevSibling().getForWrite().getMap();

        LongList all = new LongList();
        long cur = firstChild.get(parentId);
        while (!LongLongMap.isUnitValue(cur)) {
            all.add(cur);
            cur = nextSibling.get(cur);
        }
        int n = all.size();
        int currentIndex = -1;
        for (int i = 0; i < n; i++) {
            if (all.get(i) == nodeId) { currentIndex = i; break; }
        }
        if (currentIndex < 0) {
            throw new IllegalStateException("node not in parent " + parentId + "'s children: " + nodeId);
        }
        if (index < 0 || index >= n) {
            throw new IllegalArgumentException("index out of range [0, " + n + "): " + index);
        }
        if (index == currentIndex) return;

        long prevId = LongLongMap.UNIT_VALUE;
        for (int i = 0; i <= n; i++) {
            long id;
            if (i == index) {
                id = nodeId;
            } else if (i < index) {
                int src = i < currentIndex ? i : i + 1;
                id = all.get(src);
            } else {
                int src = (i - 1) < currentIndex ? (i - 1) : i;
                id = all.get(src);
            }
            if (LongLongMap.isUnitValue(prevId)) {
                firstChild.put(parentId, id);
                prevSibling.remove(id);
            } else {
                nextSibling.put(prevId, id);
                prevSibling.put(id, prevId);
            }
            prevId = id;
        }
        nextSibling.remove(prevId);
    }

    public void detach(long nodeId) {
        reparent(nodeId, LongLongMap.UNIT_VALUE);
    }

    public void detachKeepTransform(long nodeId) {
        reparentKeepTransform(nodeId, LongLongMap.UNIT_VALUE);
    }

    /**
     * 重设父节点，保持全局变换。
     *
     * <p>{@code isUnitValue(newParentId)} 表示<b>变根节点</b>（无父），
     * 是合法编辑操作，不是非法输入——父全局取单位阵，
     * {@code newLocal = inv(I) * global = global}，全局变换保持不变。
     *
     * <p>新父全局变换不可逆时，{@link KMath#mat4Inv} 直接抛——无法保持
     * 全局变换，原样传播。
     */
    public void reparentKeepTransform(long nodeId, long newParentId) {
        float[] global = new float[MAT4_LENGTH];
        if (!getGlobalTransform(nodeId, global)) {
            throw new IllegalStateException("node not found: " + nodeId);
        }

        float[] parentGlobal;

        if (LongLongMap.isUnitValue(newParentId)) {
            // 无父节点 = 变根节点：父全局取单位阵
            parentGlobal = MAT4_IDENTITY;
        } else {
            parentGlobal = new float[MAT4_LENGTH];
            if (!getGlobalTransform(newParentId, parentGlobal)) {
                throw new IllegalStateException("new parent not found: " + newParentId);
            }
        }

        float[] parentInv = mat4Scratch0;
        KMath.mat4Inv(parentGlobal, parentInv);  // 不可逆直接抛

        float[] newLocal = mat4Scratch1;
        KMath.mat4Mul(parentInv, global, newLocal);

        reparent(nodeId, newParentId);
        setLocalTransform(nodeId, newLocal);
    }

    /**
     * 重设父节点。标记 nodeId 的子树为脏。
     *
     * <p>不变量校验：nodeId == newParentId 或 newParentId 在 nodeId 子树内
     * → 抛异常（否则成环）。
     */
    public void reparent(long nodeId, long newParentId) {
        if (nodeId == newParentId) {
            throw new IllegalArgumentException("cannot reparent node to itself: " + nodeId);
        }
        if (!LongLongMap.isUnitValue(newParentId) && !containsNode(newParentId)) {
            throw new IllegalStateException("new parent not found: " + newParentId);
        }
        if (!LongLongMap.isUnitValue(newParentId) && isDescendant(newParentId, nodeId)) {
            throw new IllegalArgumentException(
                    "cannot reparent " + nodeId + " to its own descendant " + newParentId);
        }

        detachFromParent(nodeId);

        if (LongLongMap.isUnitValue(newParentId)) {
            hierarchy.roots().getForWrite().getList().add(nodeId);
            hierarchy.parent().getForWrite().getMap().remove(nodeId);
        } else {
            hierarchy.parent().getForWrite().getMap().put(nodeId, newParentId);

            LongLongMap firstChild = hierarchy.firstChild().getForWrite().getMap();
            LongLongMap nextSibling = hierarchy.nextSibling().getForWrite().getMap();
            LongLongMap prevSibling = hierarchy.prevSibling().getForWrite().getMap();

            long head = firstChild.get(newParentId);
            if (LongLongMap.isUnitValue(head)) {
                firstChild.put(newParentId, nodeId);
                prevSibling.put(nodeId, LongLongMap.UNIT_VALUE);
            } else {
                long last = head;
                long next = nextSibling.get(last);
                while (!LongLongMap.isUnitValue(next)) {
                    last = next;
                    next = nextSibling.get(last);
                }
                nextSibling.put(last, nodeId);
                prevSibling.put(nodeId, last);
            }
            nextSibling.put(nodeId, LongLongMap.UNIT_VALUE);
        }

        markDirty(nodeId);
    }

    private boolean isDescendant(long candidate, long ancestor) {
        LongLongMap parentMap = hierarchy.parent().getSnapshot().getMap();
        long p = candidate;
        while (!LongLongMap.isUnitValue(p)) {
            if (p == ancestor) return true;
            p = parentMap.get(p);
        }
        return false;
    }

    // ═══════════════════════════════════════════════
    // 全量刷新接口
    // ═══════════════════════════════════════════════

    /**
     * 全量刷新：重算所有脏子树，返回缓存。
     */
    private CopyOnWriteObject<LongObjectMapResource<CopyOnWriteFloats>> getGlobalTransforms() {
        if (globalTransforms == null) {
            LongObjectMap<CopyOnWriteFloats> map;
            map = PreMultiTransform.compute(hierarchy, floatArena);
            globalTransforms = new DefaultCopyOnWriteObject<>(new LongObjectMapResource<>(map));
            dirtyNodes.clear();
            return globalTransforms;
        }
        if (!dirtyNodes.isEmpty()) {
            flushDirty();
        }
        return globalTransforms;
    }

    /**
     * 全量刷新：<b>预热</b>全量逆变换缓存（先全量刷新全局），返回缓存。
     *
     * <p>这是显式「对每个节点做 {@code mat4Inv}」的可选入口，适用于
     * 序列化、批量导出等需要一次性拿全量逆的场景。
     *
     * <p><b>日常局部查询不依赖本方法</b>：{@link #getInvGlobalTransform}
     * 在首次调用时会懒初始化空缓存，按查询逐步回填，缓存同样生效。
     *
     * <p><b>不可逆直接抛</b>：预热过程中任何节点全局变换不可逆，
     * {@link KMath#mat4Inv} 的异常原样传播，{@code invTransforms} 保持
     * 调用前的状态——编辑器状态视为损坏，调用方不应继续使用。
     */
    private CopyOnWriteObject<LongObjectMapResource<CopyOnWriteFloats>> getInvTransforms() {
        if (invTransforms == null
                || invTransforms.getSnapshot().getMap().isEmpty()) {
            CopyOnWriteObject<LongObjectMapResource<CopyOnWriteFloats>> globals = getGlobalTransforms();
            LongObjectMap<CopyOnWriteFloats> map = new LongObjectMap<>();
            LongObjectMap<CopyOnWriteFloats> globalsMap = globals.getSnapshot().getMap();
            float[] g = mat4Scratch0;
            float[] inv = mat4Scratch1;
            globalsMap.forEach((id, cow) -> {
                try (FloatArenaView.ArraySession session = cow.array()) {
                    System.arraycopy(session.getFloats(), session.offset(), g, 0, MAT4_LENGTH);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }


                KMath.mat4Inv(g, inv);  // 不可逆直接抛
                map.put(id, allocFloats(inv));
            });
            invTransforms = new DefaultCopyOnWriteObject<>(new LongObjectMapResource<>(map));
        }
        return invTransforms;
    }

    // ═══════════════════════════════════════════════
    // 局部刷新
    // ═══════════════════════════════════════════════

    /**
     * 确保 nodeId 的全局变换是最新的。只刷新 nodeId 所在的脏子树，
     * 不清空其他脏标记。
     *
     * <p><b>逆缓存只失效、不重算</b>：若 invTransforms 已懒初始化，
     * 对脏子树做 {@link #invalidateInvSubtree}——只 remove + close，
     * 不做 {@code mat4Inv}。逆变换的成本推迟到
     * {@link #getInvGlobalTransform} 真正查询的节点上。
     * 缓存为空时 {@code invalidateInvSubtree} 直接返回，不遍历脏子树。
     */
    private void ensureGlobalFresh(long nodeId) {
        if (globalTransforms == null) {
            LongObjectMap<CopyOnWriteFloats> map;
            map =   PreMultiTransform.compute(hierarchy, floatArena);

            globalTransforms = new DefaultCopyOnWriteObject<>(new LongObjectMapResource<>(map));
            dirtyNodes.clear();
            return;
        }
        if (dirtyNodes.isEmpty()) return;

        LongLongMap parentMap = hierarchy.parent().getSnapshot().getMap();
        long d = nodeId;
        while (!LongLongMap.isUnitValue(d)) {
            if (dirtyNodes.containsKey(d)) break;
            d = parentMap.get(d);
        }

        if (LongLongMap.isUnitValue(d)) return;

        LongObjectMap<CopyOnWriteFloats> out = globalTransforms.getForWrite().getMap();
        readParentGlobal(d, parentMap, out, mat4Scratch0);
        PreMultiTransform.computeSubtree(hierarchy, d, mat4Scratch0, out, floatArena);


        // 逆缓存：只失效，不重算。按需在 getInvGlobalTransform 中回填。
        if (invTransforms != null) {
            LongObjectMap<CopyOnWriteFloats> invOut = invTransforms.getForWrite().getMap();
            invalidateInvSubtree(d, invOut);
        }

        dirtyNodes.remove(d);
    }

    // ═══════════════════════════════════════════════
    // 脏标记
    // ═══════════════════════════════════════════════

    private void markDirty(long nodeId) {
        LongLongMap parentMap = hierarchy.parent().getSnapshot().getMap();
        long p = parentMap.get(nodeId);

        while (!LongLongMap.isUnitValue(p)) {
            if (dirtyNodes.containsKey(p)) return;
            p = parentMap.get(p);
        }
        if (!dirtyNodes.containsKey(nodeId)) {
            dirtyNodes.put(nodeId, DIRTY_MARKER);
        }
    }

    private void flushDirty() {
        LongObjectMap<CopyOnWriteFloats> out = globalTransforms.getForWrite().getMap();
        LongLongMap parentMap = hierarchy.parent().getSnapshot().getMap();
        LongObjectMap<CopyOnWriteFloats> transforms = hierarchy.transforms().getSnapshot().getMap();

        dirtyNodes.forEach((nodeId, ignored) -> {
            if (!transforms.containsKey(nodeId)) return;
            readParentGlobal(nodeId, parentMap, out, mat4Scratch0);
            PreMultiTransform.computeSubtree(hierarchy, nodeId, mat4Scratch0, out, floatArena);

        });

        // 逆缓存：只失效，不重算
        if (invTransforms != null) {
            LongObjectMap<CopyOnWriteFloats> invOut = invTransforms.getForWrite().getMap();
            dirtyNodes.forEach((nodeId, ignored) -> {
                if (!transforms.containsKey(nodeId)) return;
                invalidateInvSubtree(nodeId, invOut);
            });
        }

        dirtyNodes.clear();
    }

    /**
     * 失效子树内所有节点的逆缓存——只 remove + close，不做 {@code mat4Inv}。
     *
     * <p>全局变换刷新后调用。被失效的节点在下次
     * {@link #getInvGlobalTransform} 时按需单节点现算并回填，
     * 把「全量重算逆」的成本推迟到真正查询的节点上。
     *
     * <p><b>空缓存直接跳过</b>：缓存为空时没有条目需要失效，
     * 不遍历脏子树——避免「懒初始化后从未查询过逆」的编辑器
     * 在每次全局刷新时付 O(subtree) 的无意义遍历。
     */
    private void invalidateInvSubtree(long nodeId, LongObjectMap<CopyOnWriteFloats> invs) {
        if (invs.isEmpty()) return;

        CopyOnWriteFloats old = invs.remove(nodeId);
        if (old != null) {
            try {
                old.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        LongLongMap firstChild = hierarchy.firstChild().getSnapshot().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getSnapshot().getMap();
        long child = firstChild.get(nodeId);
        while (!LongLongMap.isUnitValue(child)) {
            invalidateInvSubtree(child, invs);
            child = nextSibling.get(child);
        }
    }

    private void readParentGlobal(long nodeId,
                                  LongLongMap parentMap,
                                  LongObjectMap<CopyOnWriteFloats> out,
                                  float[] parentGlobalOut) {
        long parentId = parentMap.get(nodeId);
        if (LongLongMap.isUnitValue(parentId)) {
            System.arraycopy(MAT4_IDENTITY, 0, parentGlobalOut, 0, MAT4_LENGTH);
            return;
        }
        CopyOnWriteFloats parentCow = out.get(parentId);
        if (parentCow == null) {
            throw new IllegalStateException(
                    "parent " + parentId + " missing in preTransforms cache");
        }
        try (FloatArenaView.ArraySession session = parentCow.array()) {
            System.arraycopy(session.getFloats(), session.offset(),
                    parentGlobalOut, 0, MAT4_LENGTH);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

    }

    // ═══════════════════════════════════════════════
    // 删除辅助
    // ═══════════════════════════════════════════════

    private void removeSubtreeFromScene(long nodeId) {
        LongLongMap firstChild = hierarchy.firstChild().getForWrite().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getForWrite().getMap();

        long childId = firstChild.get(nodeId);
        while (!LongLongMap.isUnitValue(childId)) {
            long next = nextSibling.get(childId);
            removeSubtreeFromScene(childId);
            childId = next;
        }

        detachFromParent(nodeId);

        hierarchy.objects().getForWrite().getMap().remove(nodeId);
        hierarchy.transforms().getForWrite().getMap().remove(nodeId);
        hierarchy.parent().getForWrite().getMap().remove(nodeId);
        firstChild.remove(nodeId);
        nextSibling.remove(nodeId);
        hierarchy.prevSibling().getForWrite().getMap().remove(nodeId);
    }

    private void detachFromParent(long nodeId) {
        LongLongMap parentMap = hierarchy.parent().getForWrite().getMap();
        LongLongMap firstChild = hierarchy.firstChild().getForWrite().getMap();
        LongLongMap nextSibling = hierarchy.nextSibling().getForWrite().getMap();
        LongLongMap prevSibling = hierarchy.prevSibling().getForWrite().getMap();

        long parentId = parentMap.get(nodeId);
        if (LongLongMap.isUnitValue(parentId)) {
            LongList roots = hierarchy.roots().getForWrite().getList();
            int n = roots.size();
            for (int i = 0; i < n; i++) {
                if (roots.get(i) == nodeId) {
                    roots.removeAt(i);
                    break;
                }
            }
            return;
        }

        long prev = prevSibling.get(nodeId);
        long next = nextSibling.get(nodeId);

        if (LongLongMap.isUnitValue(prev)) {
            if (LongLongMap.isUnitValue(next)) {
                firstChild.remove(parentId);
            } else {
                firstChild.put(parentId, next);
                prevSibling.remove(next);
            }
        } else {
            if (LongLongMap.isUnitValue(next)) {
                nextSibling.remove(prev);
            } else {
                nextSibling.put(prev, next);
                prevSibling.put(next, prev);
            }
        }

        parentMap.remove(nodeId);
        nextSibling.remove(nodeId);
        prevSibling.remove(nodeId);
    }

    // ═══════════════════════════════════════════════
    // 分配
    // ═══════════════════════════════════════════════

    private CopyOnWriteFloats allocFloats(float[] data) {
        int byteSize = data.length * Float.BYTES;
        AllocateResult<FloatArenaView> r;
        r = floatArena.allocate(byteSize);

        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow float allocation failed unexpectedly: " + byteSize + " bytes");
        }
        FloatArenaView view = r.getView();
        try (FloatArenaView.BufferSession session = view.buffer()) {
            session.floatBuffer().put(data);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        return new DefaultCopyOnWriteFloats(view);
    }

    // ═══════════════════════════════════════════════
    // COW
    // ═══════════════════════════════════════════════

    @Override
    public HierarchyEditor shared() {
        if (globalTransforms != null && !dirtyNodes.isEmpty()) {
            flushDirty();
        }
        HierarchyEditor e = new HierarchyEditor(hierarchy.shared(), floatArena);
        e.globalTransforms = globalTransforms == null ? null : globalTransforms.shared();
        e.invTransforms    = invTransforms    == null ? null : invTransforms.shared();
        return e;
    }

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(globalTransforms, invTransforms, hierarchy);
        if (ex != null) throw ex;
    }
}