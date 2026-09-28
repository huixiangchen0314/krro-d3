package top.kzre.krro.d3.core.geometry;

public interface IAABB {
    float getMinX();
    float getMinY();
    float getMinZ();
    float getMaxX();
    float getMaxY();
    float getMaxZ();

    /** 空盒——min > max——不含任何点。 */
    default boolean isEmpty() {
        return getMinX() > getMaxX()
                || getMinY() > getMaxY()
                || getMinZ() > getMaxZ();
    }

    /** 中心点 X。 */
    default float centerX() { return (getMinX() + getMaxX()) * 0.5f; }
    default float centerY() { return (getMinY() + getMaxY()) * 0.5f; }
    default float centerZ() { return (getMinZ() + getMaxZ()) * 0.5f; }

    /** 是否包含另一个盒。 */
    default boolean contains(IAABB o) {
        return getMinX() <= o.getMinX() && getMaxX() >= o.getMaxX()
                && getMinY() <= o.getMinY() && getMaxY() >= o.getMaxY()
                && getMinZ() <= o.getMinZ() && getMaxZ() >= o.getMaxZ();
    }

    /** 是否与另一个盒相交。 */
    default boolean intersects(IAABB o) {
        return getMinX() <= o.getMaxX() && getMaxX() >= o.getMinX()
                && getMinY() <= o.getMaxY() && getMaxY() >= o.getMinY()
                && getMinZ() <= o.getMaxZ() && getMaxZ() >= o.getMinZ();
    }
}
