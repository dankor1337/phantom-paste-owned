package dev.gambleclient.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.gambleclient.event.events.Render2DEvent;
import dev.gambleclient.manager.EventManager;

@Mixin({InGameHud.class})
public class InGameHudMixin {
    @Inject(method = {"render"}, at = {@At("HEAD")})
    private void onRenderHud(final DrawContext ctx, final RenderTickCounter rtc, final CallbackInfo ci) {
        EventManager.b(new Render2DEvent(ctx, rtc.getTickDelta(true)));
    }
}