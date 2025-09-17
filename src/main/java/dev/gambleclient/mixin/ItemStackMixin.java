package dev.gambleclient.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.gambleclient.Gamble;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    
    @Inject(method = "decrement", at = @At("HEAD"), cancellable = true)
    private void onDecrement(int amount, CallbackInfo ci) {
        // Check if this is a firework rocket and anticonsume is enabled
        if (Gamble.INSTANCE != null &&
                Gamble.INSTANCE.MODULE_MANAGER != null) {
            
            var autoFirework = Gamble.INSTANCE.MODULE_MANAGER.getModuleByClass(
                dev.gambleclient.module.modules.misc.AutoFirework.class);
            
            if (autoFirework != null && autoFirework.isEnabled()) {
                ItemStack stack = (ItemStack) (Object) this;
                if (stack.isOf(Items.FIREWORK_ROCKET)) {
                    // Get the anticonsume setting value
                    try {
                        var antiConsumeField = autoFirework.getClass().getDeclaredField("antiConsume");
                        antiConsumeField.setAccessible(true);
                        var antiConsumeSetting = antiConsumeField.get(autoFirework);
                        var getValueMethod = antiConsumeSetting.getClass().getMethod("getValue");
                        boolean antiConsumeEnabled = (Boolean) getValueMethod.invoke(antiConsumeSetting);
                        
                        if (antiConsumeEnabled) {
                            // Cancel the decrement to prevent consumption
                            ci.cancel();
                        }
                    } catch (Exception e) {
                        // If we can't access the setting, don't cancel
                    }
                }
            }
        }
    }
}
