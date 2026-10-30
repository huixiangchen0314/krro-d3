package top.kzre.krro.d3.core.util.cow;

import java.nio.FloatBuffer;

public interface CopyOnWriteFloats
        extends CopyOnWrite<CopyOnWriteFloats> {
    long offset();
    long count();
    // 读
    float[] getFloatsSnapshot();
    FloatBuffer getFloatBufferSnapshot();

    // 写
    float[] getFloatsForWrite();
    FloatBuffer getFloatBufferForWrite();
}