package dev.gambleclient.event.events;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import dev.gambleclient.event.CancellableEvent;

public class Render3DEvent extends CancellableEvent {
    public MatrixStack matrixStack;
    public Matrix4f matrix4f;
    public float tickDelta;

    public Render3DEvent(final MatrixStack matrixStack, final Matrix4f matrix4f, final float tickDelta) {
        this.matrixStack = matrixStack;
        this.matrix4f = matrix4f;
        this.tickDelta = tickDelta;
    }
}