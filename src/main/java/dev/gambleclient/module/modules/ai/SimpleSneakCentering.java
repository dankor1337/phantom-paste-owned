package dev.gambleclient.module.modules.ai;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.BlockPos;

public class SimpleSneakCentering {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private static final double TOLERANCE = 0.15;

    private boolean active = false;
    private BlockPos targetBlock = null;
    private double targetX;
    private double targetZ;

    // Debug tracking
    private int tickCount = 0;
    private double lastDistanceToTarget = 0;

    public boolean startCentering() {
        if (mc.player == null) return false;

        targetBlock = mc.player.getBlockPos();
        targetX = targetBlock.getX() + 0.5;
        targetZ = targetBlock.getZ() + 0.5;

        double offsetX = Math.abs(mc.player.getX() - targetX);
        double offsetZ = Math.abs(mc.player.getZ() - targetZ);

        if (offsetX <= TOLERANCE && offsetZ <= TOLERANCE) {
            return false;
        }

        active = true;
        tickCount = 0;
        lastDistanceToTarget = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        return true;
    }

    public boolean tick() {
        if (!active || mc.player == null) return false;

        tickCount++;

        double worldOffsetX = mc.player.getX() - targetX;
        double worldOffsetZ = mc.player.getZ() - targetZ;
        double currentDistance = Math.sqrt(worldOffsetX * worldOffsetX + worldOffsetZ * worldOffsetZ);

        if (currentDistance > lastDistanceToTarget + 0.01) {
            // drifting away (debug if needed)
        }
        lastDistanceToTarget = currentDistance;

        if (Math.abs(worldOffsetX) <= TOLERANCE && Math.abs(worldOffsetZ) <= TOLERANCE) {
            stopCentering();
            return false;
        }

        releaseAllKeys();
        setPressed(mc.options.sneakKey, true);

        float yaw = mc.player.getYaw();
        double yawRad = Math.toRadians(yaw);

        double moveX = -worldOffsetX;
        double moveZ = -worldOffsetZ;

        double relativeForward = moveX * (-Math.sin(yawRad)) + moveZ * Math.cos(yawRad);
        double relativeStrafe  = moveX * (-Math.cos(yawRad)) + moveZ * (-Math.sin(yawRad));

        if (Math.abs(relativeForward) > TOLERANCE * 0.5) {
            if (relativeForward > 0) setPressed(mc.options.forwardKey, true);
            else setPressed(mc.options.backKey, true);
        }
        if (Math.abs(relativeStrafe) > TOLERANCE * 0.5) {
            if (relativeStrafe > 0) setPressed(mc.options.rightKey, true);
            else setPressed(mc.options.leftKey, true);
        }

        if (tickCount > 100) {
            stopCentering();
            return false;
        }
        return true;
    }

    public void stopCentering() {
        active = false;
        targetBlock = null;
        releaseAllKeys();
        tickCount = 0;
    }

    private void releaseAllKeys() {
        setPressed(mc.options.forwardKey, false);
        setPressed(mc.options.backKey, false);
        setPressed(mc.options.leftKey, false);
        setPressed(mc.options.rightKey, false);
        setPressed(mc.options.sneakKey, false);
    }

    private void setPressed(KeyBinding key, boolean pressed) {
        key.setPressed(pressed); // Correct usage
    }

    public boolean isCentering() {
        return active;
    }

    public boolean isDone() {
        if (!active || mc.player == null) return true;
        double offsetX = Math.abs(mc.player.getX() - targetX);
        double offsetZ = Math.abs(mc.player.getZ() - targetZ);
        return offsetX <= TOLERANCE && offsetZ <= TOLERANCE;
    }
}