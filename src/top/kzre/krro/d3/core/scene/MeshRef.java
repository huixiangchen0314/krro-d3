package top.kzre.krro.d3.core.scene;

import top.kzre.krro.d3.core.geometry.Mesh;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;

/**
 * 网格引用者, 引用烘焙过的，可直接渲染的网格.
 * <br/>
 * 注意网格不属于该接口
 */
@FunctionalInterface
public interface MeshRef {
    /**
     * 暴露COW 接口，而不是Mesh，外部想读写，必须 shared()，
     * 来保证安全
     */
    CopyOnWrite<Mesh> getRef();
}
