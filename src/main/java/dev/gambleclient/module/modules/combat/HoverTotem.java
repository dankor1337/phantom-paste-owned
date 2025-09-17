package dev.gambleclient.module.modules.combat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.mixin.HandledScreenMixin;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.EncryptedString;

public final class HoverTotem extends Module {
    private final NumberSetting tickDelay = new NumberSetting(EncryptedString.of("Tick Delay"), 0.0, 20.0, 0.0, 1.0).getValue(EncryptedString.of("Ticks to wait between operations"));
    private final BooleanSetting hotbarTotem = new BooleanSetting(EncryptedString.of("Hotbar Totem"), true).setDescription(EncryptedString.of("Also places a totem in your preferred hotbar slot"));
    private final NumberSetting hotbarSlot = new NumberSetting(EncryptedString.of("Hotbar Slot"), 1.0, 9.0, 1.0, 1.0).getValue(EncryptedString.of("Your preferred hotbar slot for totem (1-9)"));
    private final BooleanSetting autoSwitchToTotem = new BooleanSetting(EncryptedString.of("Auto Switch To Totem"), false).setDescription(EncryptedString.of("Automatically switches to totem slot when inventory is opened"));
    private int remainingDelay;

    public HoverTotem() {
        super(EncryptedString.of("Hover Totem"), EncryptedString.of("Equips a totem in offhand and optionally hotbar when hovering over one in inventory"), -1, Category.COMBAT);
        this.addSettings(this.tickDelay, this.hotbarTotem, this.hotbarSlot, this.autoSwitchToTotem);
    }

    @Override
    public void onEnable() {
        this.resetDelay();
        super.onEnable();
    }

    @EventListener
    public void onTick(final TickEvent event) {
        if (this.mc.player == null) {
            return;
        }
        final Screen currentScreen = this.mc.currentScreen;
        if (!(this.mc.currentScreen instanceof InventoryScreen)) {
            this.resetDelay();
            return;
        }
        final Slot focusedSlot = ((HandledScreenMixin) currentScreen).getFocusedSlot();
        if (focusedSlot == null || focusedSlot.getIndex() > 35) {
            return;
        }
        if (this.autoSwitchToTotem.getValue()) {
            this.mc.player.getInventory().selectedSlot = this.hotbarSlot.getIntValue() - 1;
        }
        if (focusedSlot.getStack().getItem() != Items.TOTEM_OF_UNDYING) {
            return;
        }
        if (this.remainingDelay > 0) {
            --this.remainingDelay;
            return;
        }
        final int index = focusedSlot.getIndex();
        final int syncId = ((InventoryScreen) currentScreen).getScreenHandler().syncId;
        if (!this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            this.equipOffhandTotem(syncId, index);
            return;
        }
        if (this.hotbarTotem.getValue()) {
            final int n = this.hotbarSlot.getIntValue() - 1;
            if (!this.mc.player.getInventory().getStack(n).isOf(Items.TOTEM_OF_UNDYING)) {
                this.equipHotbarTotem(syncId, index, n);
            }
        }
    }

    private void equipOffhandTotem(final int n, final int n2) {
        this.mc.interactionManager.clickSlot(n, n2, 40, SlotActionType.SWAP, this.mc.player);
        this.resetDelay();
    }

    private void equipHotbarTotem(final int n, final int n2, final int n3) {
        this.mc.interactionManager.clickSlot(n, n2, n3, SlotActionType.SWAP, this.mc.player);
        this.resetDelay();
    }

    private void resetDelay() {
        this.remainingDelay = this.tickDelay.getIntValue();
    }
}
