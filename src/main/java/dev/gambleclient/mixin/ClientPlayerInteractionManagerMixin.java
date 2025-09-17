package dev.gambleclient.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.gambleclient.event.events.AttackBlockEvent;
import dev.gambleclient.manager.EventManager;

@Mixin({ClientPlayerInteractionManager.class})
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = {"attackBlock"}, at = {@At("HEAD")})
    private void onAttackBlock(final BlockPos pos, final Direction dir, final CallbackInfoReturnable<Boolean> cir) {
        EventManager.b(new AttackBlockEvent(pos, dir));
    }
}