package top.kzre.krro.d3.core.util;

import top.kzre.krro.util.arena.AllocateResult;
import top.kzre.krro.util.arena.ArenaAllocationException;
import top.kzre.krro.util.arena.IntArenaView;

import java.nio.IntBuffer;

/**
 * 属性级 COW 单元——一组 int。
 *
 * <p><b>生命周期契约</b>：
 * <pre>
 *   构造 → 拥有一个引用（refCount = 1）
 *   shared()  → 返回新实例——引用 +1
 *   close()   → 释放一个引用——归零时底层段归还 Arena
 * </pre>
 *
 * <p><b>写时复制</b>：ForWrite 方法检查底层 view 是否被多个实例共享。
 * 共享时——克隆段——原实例不受影响。
 *
 * <p><b>线程契约</b>：写路径 + 生命周期 synchronized；读路径 volatile。
 *
 * <p><b>零拷贝契约</b>：Snapshot 返回底层 storage 的整个数组——
 * 调用方必须结合 offset() / count() 使用。
 */
public final class CopyOnWriteInts implements CopyOnWrite<CopyOnWriteInts> {

    private volatile IntArenaView data;
    private final Object lock = new Object();

    public CopyOnWriteInts(IntArenaView data) {
        if (data == null) throw new NullPointerException("data");
        this.data = data;
    }

    // ── 元数据 ──

    public long offset() { return data.offset(); }
    public long count()  { return data.count(); }

    // ── 只读 ──

    public int[]     getIntsSnapshot()      { return data.getInts(); }
    public IntBuffer getIntBufferSnapshot() { return data.intBuffer(); }

    // ── 写 ──

    public int[] getIntsForWrite() {
        synchronized (lock) {
            ensureWritableLocked();
            return data.getInts();
        }
    }

    public IntBuffer getIntBufferForWrite() {
        synchronized (lock) {
            ensureWritableLocked();
            return data.intBuffer();
        }
    }

    private void ensureWritableLocked() {
        if (data.refCount() > 1) {
            AllocateResult<IntArenaView> r = data.copy();
            if (r.isFailure()) {
                throw new ArenaAllocationException(
                        "COW failed: refCount=" + data.refCount());
            }
            data.release();
            data = r.getView();
        }
    }

    // ── 生命周期 ──

    @Override
    public CopyOnWriteInts shared() {
        synchronized (lock) {
            data.acquire();
            return new CopyOnWriteInts(data);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            data.release();
        }
    }
}