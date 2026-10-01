package top.kzre.krro.d3.core.geometry.bmesh;

/**
 * 径向链——不可变值类型。
 *
 * <p>int2（radialNext, radialPrev）——同一条边上的相邻环。
 *
 * <p>允许非流形——径向链长度不定。
 */
public final class LoopRadialRing {

    public final int radialNext;
    public final int radialPrev;

    public LoopRadialRing(int radialNext, int radialPrev) {
        this.radialNext = radialNext;
        this.radialPrev = radialPrev;
    }
    /** 空径向环——孤立环默认。 */
    public static final LoopRadialRing NONE = new LoopRadialRing(-1, -1);
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoopRadialRing)) return false;
        LoopRadialRing r = (LoopRadialRing) o;
        return radialNext == r.radialNext && radialPrev == r.radialPrev;
    }

    @Override
    public int hashCode() {
        return 31 * radialNext + radialPrev;
    }

    @Override
    public String toString() {
        return "LoopRadialRing(radialNext=" + radialNext + ", radialPrev=" + radialPrev + ")";
    }
}