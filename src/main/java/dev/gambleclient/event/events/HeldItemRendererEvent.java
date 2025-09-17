package dev.gambleclient.event.events;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import dev.gambleclient.event.Event;

public class HeldItemRendererEvent implements Event {
    private final Hand hand;
    private final ItemStack item;
    private final float equipProgress;
    private final MatrixStack matrices;

    public HeldItemRendererEvent(Hand hand, ItemStack item, float equipProgress, MatrixStack matrices) {
        this.hand = hand;
        this.item = item;
        this.equipProgress = equipProgress;
        this.matrices = matrices;
    }

    public Hand getHand() {
        return hand;
    }

    public ItemStack getItem() {
        return item;
    }

    public float getEquipProgress() {
        return equipProgress;
    }

    public MatrixStack getMatrices() {
        return matrices;
    }
}
