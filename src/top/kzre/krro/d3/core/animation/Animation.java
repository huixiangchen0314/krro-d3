package top.kzre.krro.d3.core.animation;

import top.kzre.krro.d3.core.scene.Scene;
import top.kzre.krro.d3.core.util.cow.CopyOnWrite;

@FunctionalInterface
public interface Animation {
    /**
     * 原地改，外部 COW 负责资产的安全性
     */
    void animate(CopyOnWrite<?> assetLib,
                 Scene scene,
                 AnimationContext context);
}
