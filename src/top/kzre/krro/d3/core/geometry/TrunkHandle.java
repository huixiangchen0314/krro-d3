package top.kzre.krro.d3.core.geometry;

/**
 * 网格分块句柄，封装 COW（写时复制）语义，类似于 2D 的 {@code Tile}。
 * <p>
 * 内部持有 {@link MeshTrunk} 引用，并提供线程安全的可写访问。
 * 当底层分块被多个句柄共享（引用计数 > 1）时，写入操作会自动克隆分块，
 * 从而保证其他共享者不受影响。
 */
public final class TrunkHandle {
    private volatile MeshTrunk trunk;

    /**
     * 构造句柄，不增加 trunk 的引用计数（假设 trunk 初始计数为 1）。
     */
    public TrunkHandle(MeshTrunk trunk) {
        this.trunk = trunk;
    }

    /**
     * 返回只读的 MeshTrunk。调用者不得修改返回的对象或其内部数据。
     */
    public synchronized MeshTrunk trunk() {
        return trunk;
    }

    /**
     * 返回一个可安全修改的 MeshTrunk。如果当前 trunk 被多个句柄共享（引用计数 > 1），
     * 则内部自动克隆一份，旧 trunk 引用减 1，新 trunk 独占（引用计数为 1）。
     * 调用者可以直接修改返回的 trunk，不影响其他句柄。
     */
    public synchronized MeshTrunk writableTrunk() {
        if (trunk.getRefCount() > 1) {
            MeshTrunk newTrunk = trunk.cloneTrunk();   // 池化克隆
            trunk.release();                            // 释放旧 trunk 的一个引用
            trunk = newTrunk;                           // 切换为新 trunk
        }
        return trunk;
    }

    /**
     * 替换当前 trunk 为新 trunk（用于共享）。
     * 调用者必须保证 newTrunk 的引用计数已正确增加（通常由其他句柄调用 acquire 后传入）。
     * 替换前会释放当前 trunk 的一个引用。
     * <p>
     * 包内可见，不公开给外部用户。
     */
    synchronized void replaceData(MeshTrunk newTrunk) {
        trunk.release();        // 释放旧 trunk
        trunk = newTrunk;       // 直接持有新 trunk（不再额外 acquire，因为 newTrunk 已由调用者增加了引用）
    }

    /**
     * 获取当前 trunk 的只读引用（不增加引用计数）。
     * 用于只读遍历，调用者不得修改返回的 trunk 或其内部数据。
     * <p>
     * 包内可见。
     */
    synchronized MeshTrunk getDataRef() {
        return trunk;
    }
}