package dev.gambleclient.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {

    @Inject(method = "render", at = @At(value = "HEAD"))
    private void onRenderHead(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        // Shader functionality removed
    }

    @Inject(method = "render", at = @At(value = "RETURN"))
    private void onRenderReturn(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        // Shader cleanup removed
    }
}
