package dev.gambleclient.mixin;

import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.gambleclient.event.events.ChunkMarkClosedEvent;
import dev.gambleclient.manager.EventManager;

@Mixin({ChunkOcclusionDataBuilder.class})
public abstract class ChunkOcclusionDataBuilderMixin {
    @Inject(method = {"markClosed"}, at = {@At("HEAD")}, cancellable = true)
    private void onMarkClosed(final BlockPos pos, final CallbackInfo ci) {
        final ChunkMarkClosedEvent event = new ChunkMarkClosedEvent();
        EventManager.b(event);
        if (event.isCancelled()) ci.cancel();
    }
}
