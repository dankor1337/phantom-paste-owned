package dev.gambleclient.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import dev.gambleclient.Gamble;
import dev.gambleclient.event.events.HeldItemRendererEvent;
import dev.gambleclient.module.modules.render.Animations;
import dev.gambleclient.module.modules.render.NoRender;

import static dev.gambleclient.Gamble.mc;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void onRenderItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;
        HeldItemRendererEvent event = new HeldItemRendererEvent(hand, item, equipProgress, matrices);
        Gamble.INSTANCE.getEventBus().a(event);
        
        // Shader ESP functionality removed
    }

    @Inject(method = "renderFirstPersonItem", at = @At(value = "HEAD"))
    private void onRenderItemHook(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        Animations animationsModule = (Animations) Gamble.INSTANCE.getModuleManager().getModuleByClass(Animations.class);
        if (animationsModule != null && animationsModule.isEnabled() && animationsModule.shouldAnimate() && !(item.isEmpty()) && !(item.getItem() instanceof FilledMapItem)) {
            animationsModule.applyTransformations(matrices, swingProgress);
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At(value = "RETURN"))
    private void onRenderItemPost(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        Animations animationsModule = (Animations) Gamble.INSTANCE.getModuleManager().getModuleByClass(Animations.class);
        if (animationsModule != null && animationsModule.isEnabled() && animationsModule.shouldAnimate() && !(item.isEmpty()) && !(item.getItem() instanceof FilledMapItem)) {
            matrices.pop();
        }
    }

    @ModifyArgs(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void renderItem(Args args) {
        NoRender noRenderModule = (NoRender) Gamble.INSTANCE.getModuleManager().getModuleByClass(NoRender.class);
        if (noRenderModule != null && noRenderModule.isEnabled() && !noRenderModule.shouldRenderSwing()) {
            args.set(6, 0.0F);
        }
    }
}
