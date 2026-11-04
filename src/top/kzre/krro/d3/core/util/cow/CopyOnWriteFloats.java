package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.util.arena.FloatArenaView;

/**
 * 包装 Arena + COW 读写分离
 */
public interface CopyOnWriteFloats
        extends CopyOnWrite<CopyOnWriteFloats> {

    FloatArenaView.ArraySession array();
    FloatArenaView.ArraySession arrayForWrite();
    FloatArenaView.BufferSession buffer();
    FloatArenaView.BufferSession bufferForWrite();

}