package top.kzre.krro.d3.core.scene;

import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.d3.core.util.cow.*;

/**
 * 场景——纯数据。
 *
 * <p>七张表：
 * <ul>
 *   <li>{@code roots}       根节点 id 列表</li>
 *   <li>{@code firstChild}  父 → 首子</li>
 *   <li>{@code parent}      子 → 父</li>
 *   <li>{@code nextSibling} 节点 → 下个兄弟</li>
 *   <li>{@code prevSibling} 节点 → 上个兄弟</li>
 *   <li>{@code objects}     id → SceneObject</li>
 *   <li>{@code transforms}  id → mat4</li>
 * </ul>
 *
 * <p>不含逻辑——操作由外部 Editor / 函数完成。
 */
public final class Scene implements CopyOnWrite<Scene> {

    /** 根节点 id 列表 */
    private final CopyOnWriteObject<LongListResource> roots;

    /** 父 → 首子 */
    private final CopyOnWriteObject<LongLongMapResource> firstChild;

    /** 子 → 父 */
    private final CopyOnWriteObject<LongLongMapResource> parent;

    /** 节点 → 下个兄弟 */
    private final CopyOnWriteObject<LongLongMapResource> nextSibling;

    /** 节点 → 上个兄弟 */
    private final CopyOnWriteObject<LongLongMapResource> prevSibling;

    /** id → SceneObject */
    private final CopyOnWriteObject<LongObjectMapResource<SceneObject>> objects;

    /** id → mat4（局部变换） */
    private final CopyOnWriteObject<LongObjectMapResource<CopyOnWriteFloats>> transforms;

    public Scene(
            CopyOnWriteObject<LongListResource> roots,
            CopyOnWriteObject<LongLongMapResource> firstChild,
            CopyOnWriteObject<LongLongMapResource> parent,
            CopyOnWriteObject<LongLongMapResource> nextSibling,
            CopyOnWriteObject<LongLongMapResource> prevSibling,
            CopyOnWriteObject<LongObjectMapResource<SceneObject>> objects,
            CopyOnWriteObject<LongObjectMapResource<CopyOnWriteFloats>> transforms) {
        this.roots       = roots;
        this.firstChild  = firstChild;
        this.parent      = parent;
        this.nextSibling = nextSibling;
        this.prevSibling = prevSibling;
        this.objects     = objects;
        this.transforms  = transforms;
    }

    public CopyOnWriteObject<LongListResource> roots() { return roots; }
    public CopyOnWriteObject<LongLongMapResource> firstChild()  { return firstChild; }
    public CopyOnWriteObject<LongLongMapResource> parent()      { return parent; }
    public CopyOnWriteObject<LongLongMapResource> nextSibling() { return nextSibling; }
    public CopyOnWriteObject<LongLongMapResource> prevSibling() { return prevSibling; }
    public CopyOnWriteObject<LongObjectMapResource<SceneObject>> objects() { return objects; }
    public CopyOnWriteObject<LongObjectMapResource<CopyOnWriteFloats>> transforms() { return transforms; }

    @Override
    public Scene shared() {
        return new Scene(
                roots.shared(),
                firstChild.shared(),
                parent.shared(),
                nextSibling.shared(),
                prevSibling.shared(),
                objects.shared(),
                transforms.shared());
    }

    @Override
    public void close() throws Exception {
        RuntimeException throwable = ResourceUtils.closeDelayError(
                roots, firstChild, parent, nextSibling, prevSibling, objects, transforms);
        if (throwable != null) {
            throw throwable;
        }
    }
}