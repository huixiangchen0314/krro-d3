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

/**
 * 场景编辑器——编辑操作 + 派生缓存 + 脏标记。
 *
 * <p><b>职责</b>：
 * <ul>
 *   <li>所有编辑操作走本类——写 {@link Scene} 的本质数据</li>
 *   <li>编辑操作触发脏标记，{@link #preTransforms()} 时重算脏子树</li>
 *   <li>持有 preTransforms 缓存，生命周期和编辑器一致</li>
 * </ul>
 *
 * <p><b>粒度</b>：脏标记记录“子树根”。重算时只重算标记的子树，
 * 其他节点不受影响。标记时如果祖先已脏，跳过——重算时祖先会覆盖。
 *
 * <p><b>惰性</b>：编辑时不立即重算，{@link #preTransforms()} 时才
 * flush 脏子树——批量编辑后只重算一次。
 *
 * <p><b>线程契约</b>：非线程安全。多线程通过 {@link #shared()} 获取
 * 副本，各自的脏标记独立。
 */
public final class SceneEditor implements CopyOnWrite<SceneEditor> {

    private static final float[] MAT4_IDENTITY = KMath.mat4Identity();

    /** 脏标记占位值——{@code LongObjectMap} 不允许 null value。 */
    private static final Object DIRTY_MARKER = new Object();

    private final Scene scene;
    private final ArenaTemplate<?, FloatArenaView> floatTemplate;

    /** 全局变换缓存。null 表示未计算。 */
    private CopyOnWriteObject<LongObjectMapResource<CopyOnWriteFloats>> preTransforms;

    /**
     * 脏子树根集合——{@code LongObjectMap} 做 O(1) 的 {@code containsKey}。
     *
     * <p>遍历顺序不保证——脏节点之间没有祖先关系（{@link #markDirty}
     * 保证），任意顺序重算都正确。
     */
    private final LongObjectMap<Object> dirtyNodes = new LongObjectMap<>();

    /**
     * 复用字段——{@link #readParentGlobal} 的输出容器，{@link #flushDirty}
     * 里每次覆盖后立即传给 {@code computeSubtree}。
     *
     * <p><b>安全前提</b>：{@code SceneEditor} 非线程安全；{@code flushDirty}
     * 是同步循环；{@code computeSubtree} 只读 {@code parentGlobal}，
     * 不持有引用（内部 {@code KMath.mat4Mul} 返回新数组）。
     */
    private final float[] parentGlobalScratch = new float[16];

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public SceneEditor(Scene scene, ArenaTemplate<?, FloatArenaView> floatTemplate) {
        if (scene == null)         throw new NullPointerException("scene");
        if (floatTemplate == null) throw new NullPointerException("floatTemplate");
        this.scene         = scene;
        this.floatTemplate = floatTemplate;
    }

    // ═══════════════════════════════════════════════
    // Scene 只读访问
    // ═══════════════════════════════════════════════

    public Scene scene() { return scene; }

    // ═══════════════════════════════════════════════
    // 编辑操作——全部触发脏标记
    // ═══════════════════════════════════════════════

    /**
     * 设置节点的局部变换。
     *
     * <p>标记该节点的子树为脏——局部变换变了，它的全局变换和所有
     * 子孙的全局变换都会变。
     *
     * @param nodeId 节点 id
     * @param mat4   列主序 4x4 矩阵，长度 16
     */
    public void setLocalTransform(long nodeId, float[] mat4) {
        if (mat4 == null || mat4.length != 16) {
            throw new IllegalArgumentException("mat4 must be float[16]");
        }
        LongObjectMap<CopyOnWriteFloats> t = scene.transforms().getForWrite().getMap();
        CopyOnWriteFloats old = t.get(nodeId);
        if (old != null) {
            try { old.close(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        t.put(nodeId, allocFloats(mat4));
        markDirty(nodeId);
    }

    /**
     * 添加根节点。
     *
     * @param nodeId 节点 id
     * @param mat4   列主序 4x4 局部变换，长度 16
     */
    public void addRoot(long nodeId, float[] mat4) {
        if (mat4 == null || mat4.length != 16) {
            throw new IllegalArgumentException("mat4 must be float[16]");
        }
        LongObjectMap<CopyOnWriteFloats> t = scene.transforms().getForWrite().getMap();
        t.put(nodeId, allocFloats(mat4));

        scene.roots().getForWrite().getList().add(nodeId);

        markDirty(nodeId);
    }

    /**
     * 添加子节点——挂到 {@code parentId} 的 children 末尾。
     *
     * @param parentId 父节点 id
     * @param childId  子节点 id
     * @param mat4     子节点的列主序 4x4 局部变换，长度 16
     */
    public void addChild(long parentId, long childId, float[] mat4) {
        if (mat4 == null || mat4.length != 16) {
            throw new IllegalArgumentException("mat4 must be float[16]");
        }
        LongObjectMap<CopyOnWriteFloats> t = scene.transforms().getForWrite().getMap();
        t.put(childId, allocFloats(mat4));

        scene.parent().getForWrite().getMap().put(childId, parentId);

        LongLongMap firstChild  = scene.firstChild().getForWrite().getMap();
        LongLongMap nextSibling = scene.nextSibling().getForWrite().getMap();
        LongLongMap prevSibling = scene.prevSibling().getForWrite().getMap();

        long head = firstChild.get(parentId);
        if (head == Long.MIN_VALUE) {
            firstChild.put(parentId, childId);
            prevSibling.put(childId, Long.MIN_VALUE);
        } else {
            long last = head;
            long next = nextSibling.get(last);
            while (next != Long.MIN_VALUE) {
                last = next;
                next = nextSibling.get(last);
            }
            nextSibling.put(last, childId);
            prevSibling.put(childId, last);
        }
        nextSibling.put(childId, Long.MIN_VALUE);

        markDirty(childId);
    }

    /**
     * 删除节点及其子树。
     *
     * <p>从 Scene 中移除节点、它的所有子孙、以及对应的全局变换缓存。
     * 不标记脏——直接删除，不需要重算。
     *
     * <p><b>dirtyNodes 不清理</b>：可能包含已删除的节点，
     * {@link #flushDirty} 会跳过不存在的节点。未删除节点的脏标记
     * 保留——它们仍然需要重算。
     *
     * @param nodeId 节点 id
     */
    public void removeNode(long nodeId) {
        removeSubtreeFromScene(nodeId);

        if (preTransforms != null) {
            LongObjectMap<CopyOnWriteFloats> out = preTransforms.getForWrite().getMap();
            removeSubtreeFromOut(nodeId, out);
        }
    }

    /**
     * 重设父节点——把 {@code nodeId} 从原父下移到 {@code newParentId} 下。
     *
     * <p>标记 {@code nodeId} 的子树为脏——它的全局变换会变（父变了）。
     *
     * @param nodeId      要移动的节点
     * @param newParentId 新父节点，{@code Long.MIN_VALUE} 表示移到根
     */
    public void reparent(long nodeId, long newParentId) {
        detachFromParent(nodeId);

        if (newParentId == Long.MIN_VALUE) {
            scene.roots().getForWrite().getList().add(nodeId);
            scene.parent().getForWrite().getMap().remove(nodeId);
        } else {
            scene.parent().getForWrite().getMap().put(nodeId, newParentId);

            LongLongMap firstChild  = scene.firstChild().getForWrite().getMap();
            LongLongMap nextSibling = scene.nextSibling().getForWrite().getMap();
            LongLongMap prevSibling = scene.prevSibling().getForWrite().getMap();

            long head = firstChild.get(newParentId);
            if (head == Long.MIN_VALUE) {
                firstChild.put(newParentId, nodeId);
                prevSibling.put(nodeId, Long.MIN_VALUE);
            } else {
                long last = head;
                long next = nextSibling.get(last);
                while (next != Long.MIN_VALUE) {
                    last = next;
                    next = nextSibling.get(last);
                }
                nextSibling.put(last, nodeId);
                prevSibling.put(nodeId, last);
            }
            nextSibling.put(nodeId, Long.MIN_VALUE);
        }

        markDirty(nodeId);
    }

    // ═══════════════════════════════════════════════
    // 缓存读取
    // ═══════════════════════════════════════════════

    /**
     * 获取全局变换。
     *
     * <p>首次调用时全量计算；之后调用时如果脏节点非空，重算脏子树。
     *
     * @return id → mat4 的缓存
     */
    public CopyOnWriteObject<LongObjectMapResource<CopyOnWriteFloats>> preTransforms() {
        if (preTransforms == null) {
            LongObjectMap<CopyOnWriteFloats> map =
                    PreMultiTransform.compute(scene, floatTemplate);
            preTransforms = new DefaultCopyOnWriteObject<>(new LongObjectMapResource<>(map));
            dirtyNodes.clear();
            return preTransforms;
        }
        if (!dirtyNodes.isEmpty()) {
            flushDirty();
        }
        return preTransforms;
    }

    // ═══════════════════════════════════════════════
    // 脏标记
    // ═══════════════════════════════════════════════

    /**
     * 标记子树为脏。
     *
     * <p>如果祖先已脏，跳过——重算时祖先会覆盖。{@code containsKey}
     * 是 O(1)，祖先检查是 O(深度)。
     */
    private void markDirty(long nodeId) {
        LongLongMap parentMap = scene.parent().getSnapshot().getMap();

        long p = parentMap.get(nodeId);
        while (p != Long.MIN_VALUE) {
            if (dirtyNodes.containsKey(p)) return;
            p = parentMap.get(p);
        }

        if (!dirtyNodes.containsKey(nodeId)) {
            dirtyNodes.put(nodeId, DIRTY_MARKER);
        }
    }

    /**
     * 重算所有脏子树。
     *
     * <p><b>顺序无关</b>：{@link #markDirty} 保证脏节点之间没有祖先
     * 关系，任意顺序重算都正确。
     *
     * <p><b>已删除节点跳过</b>：{@code dirtyNodes} 可能包含被
     * {@link #removeNode} 删除的节点——删除时不清理脏标记，
     * 由这里跳过。跳过后 {@code dirtyNodes.clear()} 统一清空，
     * 不会长期残留。
     *
     * <p><b>复用字段</b>：{@link #parentGlobalScratch} 每次覆盖后
     * 立即传给 {@code computeSubtree}，循环内无分配。
     */
    private void flushDirty() {
        LongObjectMap<CopyOnWriteFloats> out = preTransforms.getForWrite().getMap();
        LongLongMap parentMap = scene.parent().getSnapshot().getMap();
        LongObjectMap<CopyOnWriteFloats> transforms =
                scene.transforms().getSnapshot().getMap();

        dirtyNodes.forEach((nodeId, ignored) -> {
            // 节点可能已被删除——跳过
            if (!transforms.containsKey(nodeId)) return;

            readParentGlobal(nodeId, parentMap, out, parentGlobalScratch);
            PreMultiTransform.computeSubtree(
                    scene, nodeId, parentGlobalScratch, out, floatTemplate);
        });

        dirtyNodes.clear();
    }

    /**
     * 读父节点的全局变换，写入 {@code parentGlobalOut}。
     *
     * <p><b>总是写满 16 个 float</b>：父节点存在时写父的全局变换；
     * 父节点不存在（{@code nodeId} 是根）时写单位矩阵。
     *
     * @param nodeId          节点 id
     * @param parentMap       子 → 父映射
     * @param out             已有的全局变换映射
     * @param parentGlobalOut 输出容器，长度 ≥ 16
     */
    private void readParentGlobal(long nodeId,
                                  LongLongMap parentMap,
                                  LongObjectMap<CopyOnWriteFloats> out,
                                  float[] parentGlobalOut) {
        long parentId = parentMap.get(nodeId);
        if (parentId == Long.MIN_VALUE) {
            System.arraycopy(MAT4_IDENTITY, 0, parentGlobalOut, 0, 16);
            return;
        }

        CopyOnWriteFloats parentCow = out.get(parentId);
        if (parentCow == null) {
            throw new IllegalStateException(
                    "parent " + parentId + " missing in preTransforms cache");
        }
        float[] storage = parentCow.getFloatsSnapshot();
        int off = (int) parentCow.floatOffset();
        System.arraycopy(storage, off, parentGlobalOut, 0, 16);
    }

    // ═══════════════════════════════════════════════
    // 删除辅助
    // ═══════════════════════════════════════════════

    /** 从 Scene 中递归删除节点及其子树。 */
    private void removeSubtreeFromScene(long nodeId) {
        LongLongMap firstChild  = scene.firstChild().getForWrite().getMap();
        LongLongMap nextSibling = scene.nextSibling().getForWrite().getMap();

        long childId = firstChild.get(nodeId);
        while (childId != Long.MIN_VALUE) {
            long next = nextSibling.get(childId);
            removeSubtreeFromScene(childId);
            childId = next;
        }

        detachFromParent(nodeId);

        scene.transforms().getForWrite().getMap().remove(nodeId);
        scene.parent().getForWrite().getMap().remove(nodeId);
        firstChild.remove(nodeId);
        nextSibling.remove(nodeId);
        scene.prevSibling().getForWrite().getMap().remove(nodeId);
    }

    /** 从父/兄弟链中摘除 {@code nodeId}，不删除它的子树。 */
    private void detachFromParent(long nodeId) {
        LongLongMap parentMap   = scene.parent().getForWrite().getMap();
        LongLongMap firstChild  = scene.firstChild().getForWrite().getMap();
        LongLongMap nextSibling = scene.nextSibling().getForWrite().getMap();
        LongLongMap prevSibling = scene.prevSibling().getForWrite().getMap();

        long parentId = parentMap.get(nodeId);
        if (parentId == Long.MIN_VALUE) {
            // 是根——从 roots 里移除
            LongList roots = scene.roots().getForWrite().getList();
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

        if (prev == Long.MIN_VALUE) {
            if (next == Long.MIN_VALUE) {
                firstChild.remove(parentId);
            } else {
                firstChild.put(parentId, next);
                prevSibling.remove(next);
            }
        } else {
            if (next == Long.MIN_VALUE) {
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

    /** 从 out 中递归删除条目并 close。 */
    private void removeSubtreeFromOut(long nodeId,
                                      LongObjectMap<CopyOnWriteFloats> out) {
        LongLongMap firstChild  = scene.firstChild().getSnapshot().getMap();
        LongLongMap nextSibling = scene.nextSibling().getSnapshot().getMap();

        long childId = firstChild.get(nodeId);
        while (childId != Long.MIN_VALUE) {
            long next = nextSibling.get(childId);
            removeSubtreeFromOut(childId, out);
            childId = next;
        }

        CopyOnWriteFloats old = out.remove(nodeId);
        if (old != null) {
            try { old.close(); } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    // ═══════════════════════════════════════════════
    // 分配
    // ═══════════════════════════════════════════════

    private CopyOnWriteFloats allocFloats(float[] data) {
        int byteSize = data.length * Float.BYTES;
        AllocateResult<FloatArenaView> r = floatTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow float allocation failed unexpectedly: "
                            + byteSize + " bytes");
        }
        FloatArenaView view = r.getView();
        view.floatBuffer().put(data);
        return new DefaultCopyOnWriteFloats(view);
    }

    // ═══════════════════════════════════════════════
    // COW
    // ═══════════════════════════════════════════════

    /**
     * 共享——返回新副本。
     *
     * <p><b>先 flush 脏标记</b>：如果当前有未 flush 的编辑，先重算——
     * 保证共享出去的 {@code preTransforms} 是完整正确的。副本的
     * {@code dirtyNodes} 为空，从干净状态开始，各自独立编辑。
     *
     * <p><b>副作用</b>：本方法会修改当前实例的 {@code preTransforms}
     * （flush 时触发 COW）和清空 {@code dirtyNodes}。这是必要的——
     * 共享出去的必须是完整状态。
     *
     * <p><b>未计算过时不 flush</b>：{@code preTransforms == null} 表示
     * 从未调用过 {@link #preTransforms()}——副本第一次调用时会全量
     * 计算，包含当前的所有编辑，正确。
     */
    @Override
    public SceneEditor shared() {
        if (preTransforms != null && !dirtyNodes.isEmpty()) {
            flushDirty();
        }

        SceneEditor e = new SceneEditor(scene.shared(), floatTemplate);
        e.preTransforms = preTransforms == null ? null : preTransforms.shared();
        return e;
    }

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(preTransforms, scene);
        if (ex != null) throw ex;
    }
}