package top.kzre.krro.d3.core.util.cow;

import java.nio.IntBuffer;

public interface CopyOnWriteInts extends CopyOnWrite<CopyOnWriteInts> {
    // ── 元数据 ──
    long offset();

    long count();

    // ── 读 ──
    int[]     getIntsSnapshot();
    IntBuffer getIntBufferSnapshot();

    // ── 写 ──
    int[] getIntsForWrite();
    IntBuffer getIntBufferForWrite();

    long intOffset();
}
