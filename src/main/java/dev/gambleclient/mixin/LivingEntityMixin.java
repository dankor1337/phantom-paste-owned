package dev.gambleclient.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.gambleclient.Gamble;
import dev.gambleclient.module.modules.render.SwingSpeed;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(
            method = "getHandSwingDuration",
            at = @At("HEAD"),
            cancellable = true)
    public void getHandSwingDurationInject(CallbackInfoReturnable<Integer> cir) {
        if (Gamble.INSTANCE != null && Gamble.mc != null) {
            SwingSpeed swingSpeedModule = (SwingSpeed) Gamble.INSTANCE.getModuleManager().getModuleByClass(SwingSpeed.class);
            if (swingSpeedModule != null && swingSpeedModule.isEnabled()) {
                cir.setReturnValue(swingSpeedModule.getSwingSpeed().getIntValue());
            }
        }
    }
}
