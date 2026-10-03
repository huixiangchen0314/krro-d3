package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.d3.core.util.LongList;

/**
 * LongList 资源。
 *
 */
public final class LongListResource extends AbstractResource<LongListResource> {

    private LongList list;

    public LongListResource(LongList list) {
        if (list == null) {
            throw new NullPointerException("list");
        }
        this.list = list;
    }

    public LongList getList() {
        return list;
    }

    @Override
    protected void onDispose() {
        list = null;
    }

    @Override
    public LongListResource copy() {
        return new LongListResource(cloneList(list));
    }

    // ─── 内部 ───

    private static LongList cloneList(LongList src) {
        int n = src.size();
        LongList copy = new LongList(n);
        if (n > 0) {
            copy.addAll(src.rawArray(), 0, n);
        }
        return copy;
    }
}