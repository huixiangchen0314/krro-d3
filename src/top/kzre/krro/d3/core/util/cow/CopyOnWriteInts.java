package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.util.arena.IntArenaView;

import java.nio.IntBuffer;

public interface CopyOnWriteInts extends CopyOnWrite<CopyOnWriteInts> {
    IntArenaView.ArraySession array();
    IntArenaView.ArraySession arrayForWrite();
    IntArenaView.BufferSession buffer();
    IntArenaView.BufferSession bufferForWrite();

}
