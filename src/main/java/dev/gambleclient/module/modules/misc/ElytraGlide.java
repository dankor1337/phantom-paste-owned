package dev.gambleclient.module.modules.misc;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.PreItemUseEvent;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.InventoryUtil;

public final class ElytraGlide extends Module {
    private boolean isFlyingTriggered;
    private boolean isFireworkUsed;
    private boolean isJumpKeyPressed;

    public ElytraGlide() {
        super(EncryptedString.of("Elytra Glide"), EncryptedString.of("Starts flying when attempting to use a firework"), -1, Category.MISC);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onTick(final TickEvent event) {
        if (this.mc.currentScreen != null) {
            return;
        }
        if (this.isJumpKeyPressed) {
            this.isJumpKeyPressed = false;
            KeyBinding.setKeyPressed(this.mc.options.jumpKey.getDefaultKey(), false);
            return;
        }
        if (this.isFlyingTriggered) {
            if (this.isFireworkUsed) {
                final int selectedSlot = this.mc.player.getInventory().selectedSlot;
                if (!this.mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET) && !InventoryUtil.swap(Items.FIREWORK_ROCKET)) {
                    return;
                }
                this.mc.interactionManager.interactItem(this.mc.player, this.mc.player.getActiveHand());
                this.mc.player.getInventory().selectedSlot = selectedSlot;
                this.isFireworkUsed = false;
                this.isFlyingTriggered = false;
            } else if (this.mc.player.isOnGround()) {
                KeyBinding.setKeyPressed(this.mc.options.jumpKey.getDefaultKey(), true);
                this.isJumpKeyPressed = true;
            } else {
                KeyBinding.setKeyPressed(this.mc.options.jumpKey.getDefaultKey(), true);
                this.isJumpKeyPressed = true;
                this.isFireworkUsed = true;
            }
        }
    }

    @EventListener
    public void onPreItemUse(final PreItemUseEvent event) {
        if (!mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET) || !mc.player.getInventory().getArmorStack(EquipmentSlot.CHEST.getEntitySlotId()).isOf(Items.ELYTRA) || !mc.player.isFallFlying()) return;
        this.isFlyingTriggered = true;
    }
}