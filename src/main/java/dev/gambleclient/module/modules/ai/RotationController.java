// Unchanged logic with package swap; trimmed comments for brevity.
package dev.gambleclient.module.modules.ai;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import java.util.Random;

public class RotationController {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    private float currentYaw, targetYaw, currentPitch, targetPitch;
    private float currentRotationSpeed, currentPitchSpeed;
    private boolean isRotating = false;
    private Runnable callback;

    private boolean smoothRotation = true;
    private double baseSpeed = 4.5;
    private double acceleration = 0.8;
    private boolean humanLike = true;
    private double overshootChance = 0.3;
    private boolean preciseLanding = true;
    private double randomVariation = 0.0;
    private double effectiveSpeed;
    private double effectiveAcceleration;

    public void updateSettings(boolean smooth, double speed, double accel, boolean human, double overshoot) {
        this.smoothRotation = smooth;
        this.baseSpeed = speed;
        this.acceleration = accel;
        this.humanLike = human;
        this.overshootChance = overshoot;
    }
    public void updateRandomVariation(double variation) { this.randomVariation = variation; }
    public void setPreciseLanding(boolean precise) { this.preciseLanding = precise; }

    public void startRotation(float targetYaw, Runnable onComplete) {
        startRotation(targetYaw, 0.0f, onComplete);
    }
    public void startRotation(float targetYaw, float targetPitch, Runnable onComplete) {
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.callback = onComplete;
        calculateEffectiveValues();
        if (!smoothRotation) {
            setYawAngle(targetYaw);
            setPitchAngle(targetPitch);
            if (callback != null) callback.run();
            return;
        }
        isRotating = true;
        currentYaw = mc.player.getYaw();
        currentPitch = mc.player.getPitch();
        currentRotationSpeed = 0;
        currentPitchSpeed = 0;
    }

    private void calculateEffectiveValues() {
        if (randomVariation <= 0) {
            effectiveSpeed = baseSpeed;
            effectiveAcceleration = acceleration;
            return;
        }
        double speedMultiplier = (random.nextDouble() * 2) - 1;
        double accelMultiplier = (random.nextDouble() * 2) - 1;
        double speedVariation = randomVariation;
        effectiveSpeed = baseSpeed + (speedMultiplier * speedVariation);
        double accelRatio = acceleration / baseSpeed;
        double accelVariation = randomVariation * accelRatio;
        effectiveAcceleration = acceleration + (accelMultiplier * accelVariation);
        effectiveSpeed = Math.max(0.5, Math.min(baseSpeed * 2, effectiveSpeed));
        effectiveAcceleration = Math.max(0.1, Math.min(acceleration * 2, effectiveAcceleration));
    }

    public void update() {
        if (!isRotating) return;
        boolean yawComplete = updateYaw();
        boolean pitchComplete = updatePitch();
        if (yawComplete && pitchComplete) {
            isRotating = false;
            if (preciseLanding) {
                setYawAngle(targetYaw);
                setPitchAngle(targetPitch);
            }
            if (callback != null) callback.run();
        }
    }

    private boolean updateYaw() {
        float deltaAngle = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float distance = Math.abs(deltaAngle);
        float snapThreshold = preciseLanding ? 1.0f : 0.5f;
        if (distance < snapThreshold) {
            if (preciseLanding) {
                currentYaw = targetYaw;
                setYawAngle(targetYaw);
            }
            return true;
        }
        double speedToUse = effectiveSpeed;
        if (preciseLanding && distance < 5 && randomVariation > 0) {
            double blendFactor = distance / 5.0;
            speedToUse = baseSpeed + (effectiveSpeed - baseSpeed) * blendFactor;
        }
        float targetSpeed;
        if (distance > 45) targetSpeed = (float)(speedToUse * 1.5);
        else if (distance > 15) targetSpeed = (float)speedToUse;
        else {
            targetSpeed = (float)(speedToUse * (distance / 15));
            targetSpeed = Math.max(targetSpeed, preciseLanding ? 0.3f : 0.5f);
        }
        float accel = (float)effectiveAcceleration;
        if (currentRotationSpeed < targetSpeed) currentRotationSpeed = Math.min(currentRotationSpeed + accel, targetSpeed);
        else currentRotationSpeed = Math.max(currentRotationSpeed - accel, targetSpeed);

        float jitter = 0;
        float speedVariation = 1;
        if (humanLike && (!preciseLanding || distance > 5)) {
            jitter = (random.nextFloat() - 0.5f) * 0.2f;
            speedVariation = 0.9f + random.nextFloat() * 0.2f;
            if (random.nextFloat() < 0.02 && distance > 10) {
                currentRotationSpeed *= 0.3f;
            }
        }
        float step = Math.min(distance, currentRotationSpeed * speedVariation);
        if (deltaAngle < 0) step = -step;
        currentYaw += step + jitter;
        if (preciseLanding) {
            float newDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
            if (Math.signum(newDelta) != Math.signum(deltaAngle)) currentYaw = targetYaw;
        }
        setYawAngle(currentYaw);
        return false;
    }

    private boolean updatePitch() {
        float deltaAngle = targetPitch - currentPitch;
        float distance = Math.abs(deltaAngle);
        float snapThreshold = preciseLanding ? 1.0f : 0.5f;
        if (distance < snapThreshold) {
            if (preciseLanding) {
                currentPitch = targetPitch;
                setPitchAngle(targetPitch);
            }
            return true;
        }
        double speedToUse = effectiveSpeed;
        if (preciseLanding && distance < 3 && randomVariation > 0) {
            double blendFactor = distance / 3.0;
            speedToUse = baseSpeed + (effectiveSpeed - baseSpeed) * blendFactor;
        }
        float targetSpeed;
        if (distance > 30) targetSpeed = (float)(speedToUse * 1.2);
        else if (distance > 10) targetSpeed = (float)(speedToUse * 0.8);
        else {
            targetSpeed = (float)(speedToUse * 0.8 * (distance / 10));
            targetSpeed = Math.max(targetSpeed, preciseLanding ? 0.25f : 0.4f);
        }
        float accel = (float)(effectiveAcceleration * 0.8);
        if (currentPitchSpeed < targetSpeed) currentPitchSpeed = Math.min(currentPitchSpeed + accel, targetSpeed);
        else currentPitchSpeed = Math.max(currentPitchSpeed - accel, targetSpeed);
        float jitter = 0;
        float speedVariation = 1;
        if (humanLike && (!preciseLanding || distance > 3)) {
            jitter = (random.nextFloat() - 0.5f) * 0.15f;
            speedVariation = 0.92f + random.nextFloat() * 0.16f;
        }
        float step = Math.min(distance, currentPitchSpeed * speedVariation);
        if (deltaAngle < 0) step = -step;
        currentPitch += step + jitter;
        if (preciseLanding) {
            float newDelta = targetPitch - currentPitch;
            if (Math.signum(newDelta) != Math.signum(deltaAngle)) currentPitch = targetPitch;
        }
        setPitchAngle(currentPitch);
        return false;
    }

    private void setYawAngle(float yawAngle) {
        mc.player.setYaw(yawAngle);
        mc.player.headYaw = yawAngle;
        mc.player.bodyYaw = yawAngle;
    }
    private void setPitchAngle(float pitchAngle) {
        pitchAngle = MathHelper.clamp(pitchAngle, -90.0f, 90.0f);
        mc.player.setPitch(pitchAngle);
    }

    public boolean isRotating() { return isRotating; }
    public void resetPitch(Runnable onComplete) {
        if (Math.abs(mc.player.getPitch()) > 1.0f) {
            startRotation(mc.player.getYaw(), 0.0f, onComplete);
        } else {
            if (preciseLanding) setPitchAngle(0.0f);
            if (onComplete != null) onComplete.run();
        }
    }
}