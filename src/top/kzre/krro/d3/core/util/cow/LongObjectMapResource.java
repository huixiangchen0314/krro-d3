package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.d3.core.util.LongObjectMap;

/**
 * LongObjectMap 资源。
 *
 * <p><b>浅 COW</b>：{@link #copy()} 只复制 map 的层级结构——
 * 不复制 value 对象。新旧 map 共享同一批 value。
 *
 * <p><b>写契约</b>：只提供只读 {@link #getMap()} 与复制 {@link #copy()}——
 * 写时复制的判断由宿主层负责。
 *
 * <p><b>onDispose</b>：置空 map 引用——不 close value（value 不归本资源所有）。
 *
 * @param <V> 值类型
 */
public final class LongObjectMapResource<V> extends AbstractResource<LongObjectMapResource<V>> {

    private LongObjectMap<V> map;

    public LongObjectMapResource(LongObjectMap<V> map) {
        if (map == null) {
            throw new NullPointerException("map");
        }
        this.map = map;
    }

    /** 只读访问——契约：调用方不得修改返回的 map。 */
    public LongObjectMap<V> getMap() {
        return map;
    }

    @Override
    protected void onDispose() {
        map = null;
    }

    @Override
    public LongObjectMapResource<V> copy() {
        return new LongObjectMapResource<>(map.copy());
    }
}