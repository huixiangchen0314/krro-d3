package top.kzre.krro.d3.core.scene;

import top.kzre.krro.d3.core.geometry.Position;

/**
 * 没有资源，默认不可变设计
 */
public final class ImmutablePointLight implements SceneObject, PointLight{
    private final long id;
    private final float[] color;
    private final Position position;
    private final float intensity;

    public ImmutablePointLight(long id, float[] color, Position position, float intensity) {
        if (color == null || color.length != 3) {
            throw new IllegalArgumentException("color must be float[3]");
        }
        if (position == null) {
            throw new NullPointerException("position");
        }
        if (intensity < 0) {
            throw new IllegalArgumentException("intensity must be >= 0");
        }
        this.id = id;
        this.color = color.clone();
        this.position = position;
        this.intensity = intensity;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public float[] getColor() {
        return color;
    }

    @Override
    public Position getPosition() {
        return position;
    }

    @Override
    public float getIntensity() {
        return intensity;
    }


}
