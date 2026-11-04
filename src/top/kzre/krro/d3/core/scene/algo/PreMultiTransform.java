package top.kzre.krro.d3.core.scene.algo;

import top.kzre.krro.d3.core.scene.Hierarchy;
import top.kzre.krro.d3.core.util.LongList;
import top.kzre.krro.d3.core.util.LongLongMap;
import top.kzre.krro.d3.core.util.LongObjectMap;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.DefaultCopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.LongListResource;
import top.kzre.krro.util.arena.AllocateResult;
import top.kzre.krro.util.arena.ArenaTemplate;
import top.kzre.krro.util.arena.FloatArenaView;
import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.pool.FloatsHolder;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.PoolManagers;
import top.kzre.krro.util.pool.PoolsHolder;

import java.util.Arrays;

// TODO 并发计算。这个东西没法委托给原生计算

/**
 * 预乘变换计算——把局部变换沿树累乘成全局变换。
 *
 * <p><b>输入</b>：树结构 + 局部变换（{@link Hierarchy} 的本质数据）。
 *
 * <p><b>输出</b>：{@code id → mat4} 的映射，每个节点一个全局变换
 * {@code float[16]}，列主序。
 *
 * <p><b>两种粒度</b>：
 * <ul>
 *   <li>{@link #compute} —— 全量重算，从所有根开始，返回新的映射</li>
 *   <li>{@link #computeSubtree} —— 子树重算，只重算指定节点及其子树，
 *       写入已有的映射，覆盖旧值</li>
 * </ul>
 *
 * <p><b>算法</b>：从起点出发深度优先遍历，
 * {@code global = parentGlobal * local}。根节点的 {@code parentGlobal}
 * 是单位矩阵。节点没有局部变换时用单位矩阵——它的全局变换等于父的
 * 全局变换。
 *
 * <p><b>矩阵运算复用 {@link KMath}</b>——不重复实现。
 *
 * <p><b>纯函数</b>：只读 {@link Hierarchy} 的快照数据，不修改 Scene、
 * 不感知缓存、不做失效——缓存和失效由上层负责。{@code computeSubtree}
 * 对 {@code out} 的写入是“调用方提供的输出容器”，不是“算法维护的
 * 内部状态”。
 *
 * <p><b>线程契约</b>：只读 Scene，无 GL 调用。{@code out} 的写入
 * 由调用方保证线程安全——不同子树可以并行计算，前提是写入互斥。
 */
public final class PreMultiTransform {
    private static final FloatsPool mat4Pool;
    private static final int MAT4_LENGTH = 16;

    static
    {
        FloatsHolder holder = PoolManagers.floats().getHolder();
         mat4Pool = holder.getPool(MAT4_LENGTH);
    }
    private PreMultiTransform() {}

    // ═══════════════════════════════════════════════
    // 全量重算
    // ═══════════════════════════════════════════════

    /**
     * 全量重算——从所有根开始，返回新的映射。
     *
     * <p>首次计算时用。不读取任何已有缓存——返回的映射是全新的，
     * 调用方负责其生命周期（归入 {@code LongObjectMapResource} 后
     * 由它统一 close）。
     *
     * @param hierachy         场景，非 null
     * @param floatTemplate 输出分配的 arena 模板，非 null
     * @return id → mat4 的映射，非 null
     */
    public static LongObjectMap<CopyOnWriteFloats> compute(
            Hierarchy hierachy,
            ArenaTemplate<?, FloatArenaView> floatTemplate) {
        if (hierachy == null)         throw new NullPointerException("scene");
        if (floatTemplate == null) throw new NullPointerException("floatTemplate");

        LongObjectMap<CopyOnWriteFloats> out = new LongObjectMap<>();

        LongListResource roots = hierachy.roots().getSnapshot();
        LongList rootList = roots.getList();
        int rootCount = rootList.size();

        // 根节点的父全局变换——单位矩阵。共享一份，只读。
        float[] identity = KMath.mat4Identity();

        for (int i = 0; i < rootCount; i++) {
            long rootId = rootList.get(i);
            computeSubtree(hierachy, rootId, identity, out, floatTemplate);
        }

        return out;
    }

    // ═══════════════════════════════════════════════
    // 子树重算
    // ═══════════════════════════════════════════════

    /**
     * 子树重算——只重算 {@code rootNodeId} 及其子树。
     *
     * <p><b>parentGlobal</b>：起点的父节点全局变换，非 null。
     * 如果 {@code rootNodeId} 是场景根，传单位矩阵
     * （{@link KMath#mat4Identity()}）。
     *
     * <p><b>调用方契约</b>：{@code parentGlobal} 必须和 {@code out} 里
     * 父节点的全局变换一致——否则子树的全局变换会和父节点不连续。
     * {@code computeSubtree} 只读 {@code parentGlobal}，不持有引用。
     */
    public static void computeSubtree(
            Hierarchy hierachy,
            long rootNodeId,
            float[] parentGlobal,
            LongObjectMap<CopyOnWriteFloats> out,
            ArenaTemplate<?, FloatArenaView> floatTemplate) {
        if (hierachy == null)         throw new NullPointerException("scene");
        if (parentGlobal == null)  throw new NullPointerException("parentGlobal");
        if (out == null)           throw new NullPointerException("out");
        if (floatTemplate == null) throw new NullPointerException("floatTemplate");

        LongObjectMap<CopyOnWriteFloats> transforms  = hierachy.transforms().getSnapshot().getMap();
        LongLongMap                      firstChild  = hierachy.firstChild().getSnapshot().getMap();
        LongLongMap                      nextSibling = hierachy.nextSibling().getSnapshot().getMap();

        computeRecursive(rootNodeId, parentGlobal, out, transforms, firstChild, nextSibling, floatTemplate);
    }

    // ═══════════════════════════════════════════════
    // 递归核心
    // ═══════════════════════════════════════════════

    private static void computeRecursive(long nodeId,
                                         float[] parentGlobal,
                                         LongObjectMap<CopyOnWriteFloats> out,
                                         LongObjectMap<CopyOnWriteFloats> transforms,
                                         LongLongMap firstChild,
                                         LongLongMap nextSibling,
                                         ArenaTemplate<?, FloatArenaView> floatTemplate) {
        float[] local = mat4Pool.acquire();
        float[] global = mat4Pool.acquire();
        try{
            CopyOnWriteFloats localCow = transforms.get(nodeId);

            if (localCow != null) {
                try (FloatArenaView.ArraySession session = localCow.array()) {
                    float[] storage = session.getFloats();
                    int     off     = session.offset();
                    System.arraycopy(storage, off,
                            local, 0, MAT4_LENGTH);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
                // TODO KMath 偏移重载
                global = KMath.mat4Mul(parentGlobal, local);
            } else {
                global = parentGlobal;
            }

        }finally {
            mat4Pool.release(local);
            mat4Pool.release(global);
        }


        // 覆盖 out——旧值 close（子树重算场景）
        // 危险操作
        CopyOnWriteFloats old = out.put(nodeId, allocFloats(global, floatTemplate));
        if (old != null) {
            try {
                old.close();
            } catch (Exception e) {
                throw new RuntimeException(
                        "failed to close old preTransform for node " + nodeId, e);
            }
        }

        // 深度优先遍历子节点
        long childId = firstChild.get(nodeId);

        while (!LongLongMap.isUnitValue(childId)) {
            computeRecursive(childId, global, out, transforms, firstChild, nextSibling, floatTemplate);
            childId = nextSibling.get(childId);
        }
    }

    private static CopyOnWriteFloats allocFloats(
            float[] data,
            ArenaTemplate<?, FloatArenaView> floatTemplate) {
        int byteSize = data.length * Float.BYTES;
        AllocateResult<FloatArenaView> r = floatTemplate.allocate(byteSize);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "AutoGrow float allocation failed unexpectedly: "
                            + byteSize + " bytes");
        }
        FloatArenaView view = r.getView();
        try(FloatArenaView.ArraySession session = view.array()){
            System.arraycopy(data, 0,
                    session.getFloats(), session.offset(), session.count());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new DefaultCopyOnWriteFloats(view);
    }
}