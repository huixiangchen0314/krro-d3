package top.kzre.krro.d3.core.scene;

import top.kzre.krro.d3.core.geometry.Position;

/**
 * 点光源接口
 */
public interface PointLight {
    /**
     * 光源颜色，线性空间 RGB。
     * 数组长度 3，不可变。
     * 值范围 [0, ∞)，不限于 [0,1]。
     */
    float[] getColor();

    /**
     * 光源位置，不可变。
     */
    Position getPosition();

    /**
     * 光照强度。
     * 物理意义：坎德拉（cd）。
     * 值范围 [0, ∞)。
     */
    float getIntensity();
}