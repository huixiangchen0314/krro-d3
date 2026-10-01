package top.kzre.krro.d3.core.geometry.bmesh;

/**
 * 面内链——不可变值类型。
 *
 * <p>int2（next, prev）——环在面内的下一条 / 上一条。
 *
 * <p>满足 next.prev == loop。
 */
public final class LoopRing {

    public final int next;
    public final int prev;

    public LoopRing(int next, int prev) {
        this.next = next;
        this.prev = prev;
    }
    /** 空环——孤立环默认。 */
    public static final LoopRing NONE = new LoopRing(-1, -1);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoopRing)) return false;
        LoopRing r = (LoopRing) o;
        return next == r.next && prev == r.prev;
    }

    @Override
    public int hashCode() {
        return 31 * next + prev;
    }

    @Override
    public String toString() {
        return "LoopRing(next=" + next + ", prev=" + prev + ")";
    }
}