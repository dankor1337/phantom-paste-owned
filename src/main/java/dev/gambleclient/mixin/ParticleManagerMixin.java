package dev.gambleclient.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.gambleclient.Gamble;
import dev.gambleclient.module.modules.render.NoRender;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", 
            at = @At("HEAD"), 
            cancellable = true)
    public void onAddParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
        if (Gamble.INSTANCE != null) {
            NoRender noRender = (NoRender) Gamble.INSTANCE.getModuleManager().getModuleByClass(NoRender.class);
            if (noRender != null && noRender.isEnabled()) {
                // Check if particles should be disabled
                if (!noRender.shouldRenderParticles()) {
                    cir.setReturnValue(null);
                    return;
                }
                
                // Check if explosions should be disabled
                if (!noRender.shouldRenderExplosions() && isExplosionParticle(parameters)) {
                    cir.setReturnValue(null);
                    return;
                }
                
                // Check if smoke should be disabled
                if (!noRender.shouldRenderSmoke() && isSmokeParticle(parameters)) {
                    cir.setReturnValue(null);
                    return;
                }
            }
        }
    }
    
    private boolean isExplosionParticle(ParticleEffect effect) {
        String particleType = effect.getType().toString().toLowerCase();
        return particleType.contains("explosion") || 
               particleType.contains("explode") || 
               particleType.contains("blast") ||
               particleType.contains("boom");
    }
    
    private boolean isSmokeParticle(ParticleEffect effect) {
        String particleType = effect.getType().toString().toLowerCase();
        return particleType.contains("smoke") || 
               particleType.contains("cloud") || 
               particleType.contains("fume");
    }
}
