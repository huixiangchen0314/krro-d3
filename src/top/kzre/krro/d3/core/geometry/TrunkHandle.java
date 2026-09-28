package top.kzre.krro.d3.core.geometry;

/**
 * 网格分块句柄——封装 COW（写时复制）语义。
 *
 * <p>内部持有 {@link IMeshTrunk} 引用，并提供线程安全的可写访问。
 * 当底层分块被多个句柄共享（引用计数 &gt; 1）时，{@link #writableTrunk()}
 * 自动克隆分块——保证其他共享者不受影响。
 */
public final class TrunkHandle {

    private IMeshTrunk trunk;

    /**
     * 构造句柄——不增加 trunk 的引用计数（假设 trunk 初始计数为 1）。
     */
    public TrunkHandle(IMeshTrunk trunk) {
        this.trunk = trunk;
    }

    /**
     * 只读引用——调用者不得修改返回的对象或其内部数据。
     */
    synchronized IMeshTrunk trunk() {
        return trunk;
    }

    /**
     * 返回可安全修改的 trunk。
     *
     * <p>如果当前 trunk 被多个句柄共享（引用计数 &gt; 1），内部自动克隆
     * 一份，旧 trunk 引用减 1，新 trunk 独占（引用计数为 1）。
     */
    synchronized IMeshTrunk writableTrunk() {
        if (trunk.refCount() > 1) {
            IMeshTrunk newTrunk = trunk.cloneTrunk();
            trunk.release();
            trunk = newTrunk;
        }
        return trunk;
    }

    /**
     * 替换当前 trunk 为新 trunk（用于共享）。
     *
     * <p>方法内部 acquire 新 trunk、release 旧 trunk——调用者不需要
     * 预先增加引用计数。当 {@code newTrunk == trunk} 时——acquire 和
     * release 净效果为零——引用计数不变。
     *
     * <p>包内可见。
     */
    synchronized void replaceTrunk(IMeshTrunk newTrunk) {
        newTrunk.acquire();
        this.trunk.release();
        this.trunk = newTrunk;
    }

    /**
     * 只读引用——不增加引用计数。包内可见。
     */
    synchronized IMeshTrunk getTrunkRef() {
        return trunk;
    }
}