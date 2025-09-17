package dev.gambleclient.mixin;

import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.gambleclient.Gamble;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    
    @Inject(method = {"init"}, at = {@At("HEAD")})
    private void onInit(CallbackInfo ci) {
        // Show authentication screen when main menu loads
        if (Gamble.INSTANCE != null && !Gamble.INSTANCE.isAuthenticated) {
            Gamble.INSTANCE.showAuthenticationScreen();
        }
    }
}
