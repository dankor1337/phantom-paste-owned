package dev.gambleclient.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.gambleclient.event.events.SendMovementPacketsEvent;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.manager.EventManager;

@Mixin({ClientPlayerEntity.class})
public class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {
    @Shadow
    @Final
    protected MinecraftClient client;

    public ClientPlayerEntityMixin(final ClientWorld world, final GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = {"sendMovementPackets"}, at = {@At("HEAD")})
    private void onSendMovementPackets(final CallbackInfo ci) {
        EventManager.b(new SendMovementPacketsEvent());
    }

    @Inject(method = {"tick"}, at = {@At("HEAD")})
    private void onPlayerTick(final CallbackInfo ci) {
        EventManager.b(new TickEvent());
    }
}