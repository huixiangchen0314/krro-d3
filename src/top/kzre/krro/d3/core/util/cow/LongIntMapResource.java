package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.d3.core.util.LongIntMap;

/**
 * LongIntMap 资源。
 *
 * <p><b>深度 COW</b>：{@link #copy()} 复制底层 LongIntMap——
 * 两个资源独立持有各自的映射。
 *
 * <p><b>写契约</b>：只提供只读 {@link #getMap()} 与复制 {@link #copy()}——
 * 写时复制的判断由宿主层负责。
 *
 * <p><b>onDispose</b>：置空 map 引用——允许 GC 回收。
 */
public final class LongIntMapResource extends AbstractResource<LongIntMapResource> {

    private LongIntMap map;

    public LongIntMapResource(LongIntMap map) {
        if (map == null) {
            throw new NullPointerException("map");
        }
        this.map = map;
    }

    public LongIntMap getMap() {
        return map;
    }

    @Override
    protected void onDispose() {
        map = null;
    }

    @Override
    public LongIntMapResource copy() {
        return new LongIntMapResource(map.copy());
    }
}