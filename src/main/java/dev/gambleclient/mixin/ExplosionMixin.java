package dev.gambleclient.mixin;

import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.gambleclient.Gamble;
import dev.gambleclient.module.modules.render.NoRender;

@Mixin(TntEntity.class)
public class ExplosionMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void onTick(CallbackInfo ci) {
        if (Gamble.INSTANCE != null) {
            NoRender noRender = (NoRender) Gamble.INSTANCE.getModuleManager().getModuleByClass(NoRender.class);
            if (noRender != null && noRender.isEnabled() && !noRender.shouldRenderExplosions()) {
                // Remove the TNT entity to prevent explosion
                TntEntity tnt = (TntEntity) (Object) this;
                tnt.discard();
                ci.cancel();
            }
        }
    }
}
