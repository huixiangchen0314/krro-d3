package top.kzre.krro.d3.core.animation;

import top.kzre.krro.d3.core.scene.Hierarchy;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;

@FunctionalInterface
public interface Animation {
    /**
     * 原地改，外部 COW 负责资产的安全性
     */
    void animate(CopyOnWrite<?> assetLib,
                 Hierarchy hierachy,
                 AnimationContext context);
}
