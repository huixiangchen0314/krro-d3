package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.util.arena.AllocateResult;
import top.kzre.krro.util.arena.ArenaAllocationException;
import top.kzre.krro.util.arena.FloatArenaView;

import java.nio.FloatBuffer;

/**
 * 属性级 COW 单元——一组 float。
 *
 * <p><b>生命周期契约</b>：
 * <pre>
 *   构造 → 拥有一个引用（refCount = 1）
 *   shared()  → 返回新实例——引用 +1
 *   close()   → 释放一个引用——归零时底层段归还 Arena
 * </pre>
 * 所有引用计数管理通过 shared() / close() —— 无其他入口。
 *
 * <p><b>写时复制</b>：ForWrite 方法检查底层 view 是否被多个实例共享。
 * 共享时——克隆段——原实例不受影响。
 *
 * <p><b>线程契约</b>：写路径 + 生命周期 synchronized；读路径 volatile。
 *
 * <p><b>零拷贝契约</b>：Snapshot 返回底层 storage 的整个数组——
 * 调用方必须结合 offset() / count() 使用。
 *
 * <p><b>不要持有底层 view</b>：view 不对外暴露——通过快照方法访问。
 */
public final class DefaultCopyOnWriteFloats implements CopyOnWriteFloats {

    private volatile FloatArenaView data;
    private final Object lock = new Object();

    /**
     * @param data 底层 view——引用计数应为 1——所有权转移给本实例
     */
    public DefaultCopyOnWriteFloats(FloatArenaView data) {
        if (data == null) throw new NullPointerException("data");
        this.data = data;
    }

    // ═══════════════════════════════════════════════
    // 元数据——只读
    // ═══════════════════════════════════════════════

    @Override
    public long offset() { return data.offset(); }
    @Override
    public long count()  { return data.count(); }

    // ═══════════════════════════════════════════════
    // 只读——不触发 COW
    // ═══════════════════════════════════════════════

    @Override
    public float[] getFloatsSnapshot() {
        return data.getFloats();
    }

    @Override
    public FloatBuffer getFloatBufferSnapshot() {
        return data.floatBuffer();
    }

    // ═══════════════════════════════════════════════
    // 写——触发 COW
    // ═══════════════════════════════════════════════
    @Override
    public float[] getFloatsForWrite() {
        synchronized (lock) {
            ensureWritableLocked();
            return data.getFloats();
        }
    }

    @Override
    public FloatBuffer getFloatBufferForWrite() {
        synchronized (lock) {
            ensureWritableLocked();
            return data.floatBuffer();
        }
    }

    @Override
    public long floatOffset() {
        synchronized (lock) {
            ensureWritableLocked();
            return data.floatOffset();
        }
    }

    private void ensureWritableLocked() {
        if (data.refCount() > 1) {
            AllocateResult<FloatArenaView> r = data.copy();
            if (r.isFailure()) {
                throw new ArenaAllocationException(
                        "COW failed: refCount=" + data.refCount());
            }
            data.release();
            data = r.getView();
        }
    }

    // ═══════════════════════════════════════════════
    // 生命周期——唯二入口
    // ═══════════════════════════════════════════════

    /**
     * 共享——返回新实例——底层 view 引用 +1。
     *
     * <p>新实例和当前实例共享同一段——直到任一方写入——写入方
     * 自动 COW 隔离。
     */
    @Override
    public CopyOnWriteFloats shared() {
        synchronized (lock) {
            data.acquire();
            return new DefaultCopyOnWriteFloats(data);
        }
    }

    /**
     * 释放一个引用。归零时底层段归还 Arena。
     *
     * <p><b>幂等？不</b>——close 后再次调用 close 是 bug——会抛
     * {@link IllegalStateException}。
     */
    @Override
    public void close() {
        synchronized (lock) {
            data.release();
        }
    }
}