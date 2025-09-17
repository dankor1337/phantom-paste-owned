package dev.gambleclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.gambleclient.Gamble;
import dev.gambleclient.event.events.*;
import dev.gambleclient.manager.EventManager;

@Mixin({MinecraftClient.class})
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public ClientWorld world;
    @Shadow
    @Final
    private Window window;
    @Shadow
    private int itemUseCooldown;

    @Inject(method = {"tick"}, at = {@At("HEAD")})
    private void onTick(final CallbackInfo ci) {
        if (this.world != null) {
            EventManager.b(new TickEvent());
        }
    }

    @Inject(method = {"onResolutionChanged"}, at = {@At("HEAD")})
    private void onResolutionChanged(final CallbackInfo ci) {
        EventManager.b(new ResolutionChangedEvent(this.window));
    }

    @Inject(method = {"doItemUse"}, at = {@At("RETURN")}, cancellable = true)
    private void onItemUseReturn(final CallbackInfo ci) {
        final PostItemUseEvent event = new PostItemUseEvent(this.itemUseCooldown);
        EventManager.b(event);
        if (event.isCancelled()) ci.cancel();
        this.itemUseCooldown = event.cooldown;
    }

    @Inject(method = {"doItemUse"}, at = {@At("HEAD")}, cancellable = true)
    private void onItemUseHead(final CallbackInfo ci) {
        final PreItemUseEvent event = new PreItemUseEvent(this.itemUseCooldown);
        EventManager.b(event);
        if (event.isCancelled()) ci.cancel();
        this.itemUseCooldown = event.cooldown;
    }

    @Inject(method = {"doAttack"}, at = {@At("HEAD")}, cancellable = true)
    private void onAttack(final CallbackInfoReturnable<Boolean> cir) {
        final AttackEvent event = new AttackEvent();
        EventManager.b(event);
        if (event.isCancelled()) cir.setReturnValue(false);
    }

    @Inject(method = {"handleBlockBreaking"}, at = {@At("HEAD")}, cancellable = true)
    private void onBlockBreaking(final boolean breaking, final CallbackInfo ci) {
        final BlockBreakingEvent event = new BlockBreakingEvent();
        EventManager.b(event);
        if (event.isCancelled()) ci.cancel();
    }

    @Inject(method = {"setScreen"}, at = {@At("HEAD")}, cancellable = true)
    private void onSetScreen(final Screen screen, final CallbackInfo ci) {
        final SetScreenEvent event = new SetScreenEvent(screen);
        EventManager.b(event);
        if (event.isCancelled()) ci.cancel();

        // Check if we need to show authentication screen
        if (Gamble.INSTANCE != null && !Gamble.INSTANCE.isAuthenticated && screen != null) {
            // If trying to set a screen but not authenticated, show auth screen instead
            if (!(screen instanceof dev.gambleclient.gui.AuthScreen)) {
                ci.cancel();
                Gamble.INSTANCE.showAuthenticationScreen();
            }
        }
    }

    @Inject(method = {"stop"}, at = {@At("HEAD")})
    private void onClose(final CallbackInfo callbackInfo) {
        Gamble.INSTANCE.getConfigManager().shutdown();
    }
}