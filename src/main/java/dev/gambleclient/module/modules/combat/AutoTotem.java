package dev.gambleclient.module.modules.combat;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import dev.gambleclient.Gamble;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.modules.donut.RtpBaseFinder;
import dev.gambleclient.module.modules.donut.TunnelBaseFinder;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.EncryptedString;

public final class AutoTotem extends Module {
    private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0.0, 5.0, 1.0, 1.0);
    private int delayCounter;

    public AutoTotem() {
        super(EncryptedString.of("Auto Totem"), EncryptedString.of("Automatically holds totem in your off hand"), -1, Category.COMBAT);
        this.addSettings(this.delay);
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
        if (this.mc.player == null) {
            return;
        }
        final Module rtpBaseFinder = Gamble.INSTANCE.MODULE_MANAGER.getModuleByClass(RtpBaseFinder.class);
        if (rtpBaseFinder.isEnabled() && ((RtpBaseFinder) rtpBaseFinder).isRepairingActive()) {
            return;
        }
        final Module tunnelBaseFinder = Gamble.INSTANCE.MODULE_MANAGER.getModuleByClass(TunnelBaseFinder.class);
        if (tunnelBaseFinder.isEnabled() && ((TunnelBaseFinder) tunnelBaseFinder).isDigging()) {
            return;
        }
        if (this.mc.player.getInventory().getStack(40).getItem() == Items.TOTEM_OF_UNDYING) {
            this.delayCounter = this.delay.getIntValue();
            return;
        }
        if (this.delayCounter > 0) {
            --this.delayCounter;
            return;
        }
        final int slot = this.findItemSlot(Items.TOTEM_OF_UNDYING);
        if (slot == -1) {
            return;
        }
        this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, convertSlotIndex(slot), 40, SlotActionType.SWAP, this.mc.player);
        this.delayCounter = this.delay.getIntValue();
    }

    public int findItemSlot(final Item item) {
        if (this.mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 36; ++i) {
            if (this.mc.player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private static int convertSlotIndex(final int slotIndex) {
        if (slotIndex < 9) {
            return 36 + slotIndex;
        }
        return slotIndex;
    }
}