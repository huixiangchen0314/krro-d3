package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.util.CopyOnWrite;
import top.kzre.krro.d3.core.util.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.CopyOnWriteInts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 网格——本质数据容器。
 *
 * <p><b>只持有本质数据</b>：
 * <ul>
 *   <li>{@link #cornerVerts()} —— 面角 → 顶点</li>
 *   <li>{@link #faceOffsets()} —— 面 → 面角范围偏移</li>
 *   <li>{@link #positions()}   —— 顶点位置</li>
 *   <li>{@link #cornerUvs()}   —— 面角 UV</li>
 *   <li>{@link #submeshes()}   —— 材质分组</li>
 * </ul>
 *
 * <p><b>不感知派生数据</b>：法线 / 边 / 切线 / 包围盒的
 * 计算、缓存、失效——全部由使用者自己管理。Mesh 不提供
 * 任何相关机制。
 *
 * <p><b>版本号不在 Mesh</b>：使用者需要版本时——自己在消费侧维护。
 *
 * <p><b>共享</b>：{@link #shared()} 返回共享底层数据的副本——
 * 写时由 {@link CopyOnWrite} 自动隔离。
 *
 * <p><b>线程契约</b>：
 * <ul>
 *   <li><b>读</b>：多线程安全。字段为 final——安全发布；
 *       底层 COW 数据通过 {@link CopyOnWrite} 的 volatile /
 *       synchronized 保证。</li>
 *   <li><b>跨 Mesh 写</b>：多线程安全。{@link #shared()} 产生的
 *       多个实例可分散到多线程——各自通过 COW 隔离——互不影响。</li>
 *   <li><b>同 Mesh 写</b>：单线程——同一 Mesh 实例的
 *       {@code getFloatBufferForWrite()} 不并发调用。这是业务
 *       语义约定——因为两次并发写会让两个线程拿到同一个独占
 *       Buffer——后写覆盖先写。</li>
 *   <li><b>{@code submeshes(newList)}</b>：单线程——字段非
 *       volatile——并发替换不可见。</li>
 * </ul>
 */
public final class Mesh implements CopyOnWrite<Mesh> {

    /** 面角 → 顶点索引。int[ncorners] */
    private final CopyOnWriteInts   cornerVerts;

    /** 面 → 面角范围偏移。int[nfaces+1] */
    private final CopyOnWriteInts   faceOffsets;

    /** 顶点位置。float3[nverts] */
    private final CopyOnWriteFloats positions;

    /** 面角 UV。float2[ncorners] */
    private final CopyOnWriteFloats cornerUvs;

    /**
     * 材质分组。
     *
     * <p>引用可变——替换整个 List 即修改。
     * shared() 后各 Mesh 的字段独立——一方替换不影响另一方。
     * 调用方不得直接修改 List 内容。
     */
    private List<SubMesh> submeshes;

    // ═══════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════

    public Mesh(
            CopyOnWriteInts   cornerVerts,
            CopyOnWriteInts   faceOffsets,
            CopyOnWriteFloats positions,
            CopyOnWriteFloats cornerUvs,
            List<SubMesh> submeshes) {

        if (cornerVerts == null || faceOffsets == null
                || positions == null || cornerUvs == null
                || submeshes == null) {
            throw new NullPointerException("Mesh fields must not be null");
        }

        this.cornerVerts = cornerVerts;
        this.faceOffsets = faceOffsets;
        this.positions   = positions;
        this.cornerUvs   = cornerUvs;
        // 防御性拷贝
        this.submeshes   = Collections.unmodifiableList(new ArrayList<>(submeshes));
    }

    // ═══════════════════════════════════════════════
    // 计数
    // ═══════════════════════════════════════════════

    public long vertCount()   { return positions.count() / 3; }
    public long cornerCount() { return cornerVerts.count(); }
    public long faceCount()   { return faceOffsets.count() - 1; }

    // ═══════════════════════════════════════════════
    // 本质访问
    // ═══════════════════════════════════════════════

    public CopyOnWriteInts   cornerVerts() { return cornerVerts; }
    public CopyOnWriteInts   faceOffsets() { return faceOffsets; }
    public CopyOnWriteFloats positions()   { return positions; }
    public CopyOnWriteFloats cornerUvs()   { return cornerUvs; }

    public List<SubMesh> submeshes() { return submeshes; }

    /**
     * 替换 submeshes——不影响 shared() 的其他 Mesh。
     *
     * <p>传入的 List 所有权转移给本 Mesh——调用方不得再修改。
     */
    public void submeshes(List<SubMesh> submeshes) {
        if (submeshes == null) throw new NullPointerException("submeshes");
        this.submeshes = submeshes;
    }

    // ═══════════════════════════════════════════════
    // 共享
    // ═══════════════════════════════════════════════

    @Override
    public Mesh shared() {
        return new Mesh(
                cornerVerts.shared(),
                faceOffsets.shared(),
                positions.shared(),
                cornerUvs.shared(),
                submeshes
        );
    }


    // ═══════════════════════════════════════════════
    // 关闭
    // ═══════════════════════════════════════════════

    @Override
    public void close() {
        cornerVerts.close();
        faceOffsets.close();
        positions.close();
        cornerUvs.close();
    }
}