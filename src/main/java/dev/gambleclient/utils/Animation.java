package dev.gambleclient.utils;

import dev.gambleclient.module.modules.client.Phantom;

public final class Animation {
    private double value;
    private final double end;

    public Animation(double end) {
        this.value = end;
        this.end = end;
    }

    public void animate(double speed, double target) {
        if (Phantom.animationMode.isMode(Phantom.AnimationMode.NORMAL)) {
            this.value = MathUtil.approachValue((float) speed, this.value, target);
        } else if (Phantom.animationMode.isMode(Phantom.AnimationMode.POSITIVE)) {
            this.value = MathUtil.smoothStep(speed, this.value, target);
        } else if (Phantom.animationMode.isMode(Phantom.AnimationMode.OFF)) {
            this.value = target;
        }
    }

    public double getAnimation() {
        return this.value;
    }

    public void setAnimation(final double factor) {
        this.value = MathUtil.smoothStep(factor, this.value, this.end);
    }
}
