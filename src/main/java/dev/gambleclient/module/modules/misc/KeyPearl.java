package dev.gambleclient.module.modules.misc;

import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BindSetting;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.InventoryUtil;
import dev.gambleclient.utils.KeyUtils;

public final class KeyPearl extends Module {
    private final BindSetting activateKey = new BindSetting(EncryptedString.of("Activate Key"), -1, false);
    private final NumberSetting throwDelay = new NumberSetting(EncryptedString.of("Delay"), 0.0, 20.0, 0.0, 1.0);
    private final BooleanSetting switchBack = new BooleanSetting(EncryptedString.of("Switch Back"), true);
    private final NumberSetting switchBackDelay = new NumberSetting(EncryptedString.of("Switch Delay"), 0.0, 20.0, 0.0, 1.0).getValue(EncryptedString.of("Delay after throwing pearl before switching back"));
    private boolean isActivated;
    private boolean hasThrown;
    private int currentThrowDelay;
    private int previousSlot;
    private int currentSwitchBackDelay;

    public KeyPearl() {
        super(EncryptedString.of("Key Pearl"), EncryptedString.of("Switches to an ender pearl and throws it when you press a bind"), -1, Category.MISC);
        this.addSettings(this.activateKey, this.throwDelay, this.switchBack, this.switchBackDelay);
    }

    @Override
    public void onEnable() {
        this.resetState();
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
        if (KeyUtils.isKeyPressed(this.activateKey.getValue())) {
            this.isActivated = true;
        }
        if (this.isActivated) {
            if (this.previousSlot == -1) {
                this.previousSlot = this.mc.player.getInventory().selectedSlot;
            }
            InventoryUtil.swap(Items.ENDER_PEARL);
            if (this.currentThrowDelay < this.throwDelay.getIntValue()) {
                ++this.currentThrowDelay;
                return;
            }
            if (!this.hasThrown) {
                final ActionResult interactItem = this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
                if (interactItem.isAccepted() && interactItem.shouldSwingHand()) {
                    this.mc.player.swingHand(Hand.MAIN_HAND);
                }
                this.hasThrown = true;
            }
            if (this.switchBack.getValue()) {
                this.handleSwitchBack();
            } else {
                this.resetState();
            }
        }
    }

    private void handleSwitchBack() {
        if (this.currentSwitchBackDelay < this.switchBackDelay.getIntValue()) {
            ++this.currentSwitchBackDelay;
            return;
        }
        InventoryUtil.swap(this.previousSlot);
        this.resetState();
    }

    private void resetState() {
        this.previousSlot = -1;
        this.currentThrowDelay = 0;
        this.currentSwitchBackDelay = 0;
        this.isActivated = false;
        this.hasThrown = false;
    }
}
