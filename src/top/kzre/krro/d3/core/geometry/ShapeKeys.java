package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteObject;
import top.kzre.krro.d3.core.util.cow.ListResource;

/**
 * 形态键组——基础位置 + 一组形态键偏移。
 *
 * <p>基础位置是全量顶点位置，形态键是对它的修正。
 * 权重为 0 时用基础位置，权重为 1 时完全应用偏移。
 *
 * <p><b>数据约束</b>：
 * <ul>
 *   <li>{@code basePositions.length = vertCount * 3}</li>
 *   <li>每个 delta 的 {@code deltas.length = affectedIndices.length * 3}</li>
 * </ul>
 */
public final class ShapeKeys implements CopyOnWrite<ShapeKeys> {

    /** 基础顶点位置。float3[nverts]。 */
    private final CopyOnWriteFloats basePositions;

    /** 形态键偏移，按顺序。 */
    private final CopyOnWriteObject<ListResource<ShapeKeyDelta>> deltas;

    public ShapeKeys(
            CopyOnWriteFloats basePositions,
            CopyOnWriteObject<ListResource<ShapeKeyDelta>> deltas) {
        this.basePositions = basePositions;
        this.deltas        = deltas;
    }

    public CopyOnWriteFloats basePositions() { return basePositions; }
    public CopyOnWriteObject<ListResource<ShapeKeyDelta>> deltas() { return deltas; }

    @Override
    public ShapeKeys shared() {
        return new ShapeKeys(basePositions.shared(), deltas.shared());
    }

    @Override
    public void close() throws Exception {
        RuntimeException ex = ResourceUtils.closeDelayError(basePositions, deltas);
        if (ex != null) throw ex;
    }

    // ═══════════════════════════════════════════════
    // ShapeKeyDelta——单个形态键
    // ═══════════════════════════════════════════════

    /**
     * 单个形态键偏移。
     *
     * <p>对基础位置的修正。两种模式：
     * <ul>
     *   <li><b>全量</b>：{@code affectedIndices == null}，{@code deltas} 覆盖所有顶点。
     *       {@code deltas.length = vertCount * 3}</li>
     *   <li><b>稀疏</b>：{@code affectedIndices != null}，只存受影响顶点的偏移。
     *       {@code deltas.length = affectedIndices.length * 3}</li>
     * </ul>
     */
    public static final class ShapeKeyDelta implements CopyOnWrite<ShapeKeyDelta> {

        private final String name;

        /** 受影响的顶点索引。null 表示全量。 */
        private final CopyOnWriteInts affectedIndices;

        /** 偏移量。float3[n]。 */
        private final CopyOnWriteFloats deltas;

        public ShapeKeyDelta(
                String name,
                CopyOnWriteInts affectedIndices,
                CopyOnWriteFloats deltas) {
            this.name            = name;
            this.affectedIndices = affectedIndices;
            this.deltas          = deltas;
        }

        public String name()                     { return name; }
        public CopyOnWriteInts affectedIndices() { return affectedIndices; }
        public CopyOnWriteFloats deltas()          { return deltas; }

        /** 是否全量——所有顶点都受影响。 */
        public boolean isFull() { return affectedIndices == null; }

        @Override
        public ShapeKeyDelta shared() {
            return new ShapeKeyDelta(
                    name,
                    affectedIndices == null ? null : affectedIndices.shared(),
                    deltas.shared());
        }

        @Override
        public void close() throws Exception {
            RuntimeException ex = ResourceUtils.closeDelayError(affectedIndices, deltas);
            if (ex != null) throw ex;
        }
    }
}