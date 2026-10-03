package top.kzre.krro.d3.core.scene;

/**
 * 场景对象。
 *
 * <p>只有身份，没有层级，没有父子。
 * 层级是独立的结构，通过 id 引用。
 */
public interface SceneObject {
    /**
     * 获取对象 id，全局唯一。
     */
    long getId();
}