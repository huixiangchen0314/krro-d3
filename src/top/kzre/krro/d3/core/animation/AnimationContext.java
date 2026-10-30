package top.kzre.krro.d3.core.animation;

public final class AnimationContext {
    /**
     * 动画开始的绝对时间
     */
    private final double time;

    public AnimationContext(double time) {
        this.time = time;
    }

    public double getTime() {
        return time;
    }
}
