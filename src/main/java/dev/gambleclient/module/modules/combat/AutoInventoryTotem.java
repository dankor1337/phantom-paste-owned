package dev.gambleclient.module.modules.combat;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.FakeInvScreen;
import dev.gambleclient.utils.EncryptedString;

import java.util.function.Predicate;

public final class AutoInventoryTotem extends Module {
    private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0.0, 20.0, 0.0, 1.0);
    private final BooleanSetting hotbar = new BooleanSetting(EncryptedString.of("Hotbar"), true).setDescription(EncryptedString.of("Puts a totem in your hotbar as well, if enabled (Setting below will work if this is enabled)"));
    private final NumberSetting totemSlot = new NumberSetting(EncryptedString.of("Totem Slot"), 1.0, 9.0, 1.0, 1.0).getValue(EncryptedString.of("Your preferred totem slot"));
    private final BooleanSetting autoSwitch = new BooleanSetting(EncryptedString.of("Auto Switch"), false).setDescription(EncryptedString.of("Switches to totem slot when going inside the inventory"));
    private final BooleanSetting forceTotem = new BooleanSetting(EncryptedString.of("Force Totem"), false).setDescription(EncryptedString.of("Puts the totem in the slot, regardless if its space is taken up by something else"));
    private final BooleanSetting autoOpen = new BooleanSetting(EncryptedString.of("Auto Open"), false).setDescription(EncryptedString.of("Automatically opens and closes the inventory for you"));
    private final NumberSetting stayOpenDuration = new NumberSetting(EncryptedString.of("Stay Open For"), 0.0, 20.0, 0.0, 1.0);
    int delayCounter = -1;
    int stayOpenCounter = -1;

    public AutoInventoryTotem() {
        super(EncryptedString.of("Auto Inv Totem"), EncryptedString.of("Automatically equips a totem in your offhand and main hand if empty"), -1, Category.COMBAT);
        this.addSettings(this.delay, this.hotbar, this.totemSlot, this.autoSwitch, this.forceTotem, this.autoOpen, this.stayOpenDuration);
    }

    @Override
    public void onEnable() {
        this.delayCounter = -1;
        this.stayOpenCounter = -1;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onTick(final TickEvent event) {
        if (this.shouldOpenInventory() && this.autoOpen.getValue()) {
            this.mc.setScreen(new FakeInvScreen(this.mc.player));
        }
        if (!(this.mc.currentScreen instanceof InventoryScreen) && !(this.mc.currentScreen instanceof FakeInvScreen)) {
            this.delayCounter = -1;
            this.stayOpenCounter = -1;
            return;
        }
        if (this.delayCounter == -1) {
            this.delayCounter = this.delay.getIntValue();
        }
        if (this.stayOpenCounter == -1) {
            this.stayOpenCounter = this.stayOpenDuration.getIntValue();
        }
        if (this.delayCounter > 0) {
            --this.delayCounter;
        }
        final PlayerInventory getInventory = this.mc.player.getInventory();
        if (this.autoSwitch.getValue()) {
            getInventory.selectedSlot = this.totemSlot.getIntValue() - 1;
        }
        if (this.delayCounter <= 0) {
            if (getInventory.offHand.get(0).getItem() != Items.TOTEM_OF_UNDYING) {
                final int l = this.findTotemSlot();
                if (l != -1) {
                    this.mc.interactionManager.clickSlot(((InventoryScreen) this.mc.currentScreen).getScreenHandler().syncId, l, 40, SlotActionType.SWAP, this.mc.player);
                    return;
                }
            }
            if (this.hotbar.getValue()) {
                final ItemStack getAbilities = this.mc.player.getMainHandStack();
                if (getAbilities.isEmpty() || (this.forceTotem.getValue() && getAbilities.getItem() != Items.TOTEM_OF_UNDYING)) {
                    final int i = this.findTotemSlot();
                    if (i != -1) {
                        this.mc.interactionManager.clickSlot(((InventoryScreen) this.mc.currentScreen).getScreenHandler().syncId, i, getInventory.selectedSlot, SlotActionType.SWAP, this.mc.player);
                        return;
                    }
                }
            }
            if (this.isTotemEquipped() && this.autoOpen.getValue()) {
                if (this.stayOpenCounter != 0) {
                    --this.stayOpenCounter;
                    return;
                }
                this.mc.currentScreen.close();
                this.stayOpenCounter = this.stayOpenDuration.getIntValue();
            }
        }
    }

    public boolean isTotemEquipped() {
        if (this.hotbar.getValue()) {
            return this.mc.player.getInventory().getStack(this.totemSlot.getIntValue() - 1).getItem() == Items.TOTEM_OF_UNDYING && this.mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING && this.mc.currentScreen instanceof FakeInvScreen;
        }
        return this.mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING && this.mc.currentScreen instanceof FakeInvScreen;
    }

    public boolean shouldOpenInventory() {
        if (this.hotbar.getValue()) {
            return (this.mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING || this.mc.player.getInventory().getStack(this.totemSlot.getIntValue() - 1).getItem() != Items.TOTEM_OF_UNDYING) && !(this.mc.currentScreen instanceof FakeInvScreen) && this.countTotems(item -> item == Items.TOTEM_OF_UNDYING) != 0;
        }
        return this.mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING && !(this.mc.currentScreen instanceof FakeInvScreen) && this.countTotems(item2 -> item2 == Items.TOTEM_OF_UNDYING) != 0;
    }

    private int findTotemSlot() {
        final PlayerInventory inventory = this.mc.player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            if (inventory.main.get(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    private int countTotems(final Predicate<Item> predicate) {
        int count = 0;
        final PlayerInventory inventory = this.mc.player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            final ItemStack stack = inventory.main.get(i);
            if (predicate.test(stack.getItem())) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
