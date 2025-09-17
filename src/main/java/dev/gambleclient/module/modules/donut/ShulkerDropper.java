package dev.gambleclient.module.modules.donut;

import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.EncryptedString;

public final class ShulkerDropper extends Module {
    private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0.0, 20.0, 1.0, 1.0);
    private int delayCounter = 0;

    public ShulkerDropper() {
        super(EncryptedString.of("Shulker Dropper"), EncryptedString.of("Goes to shop buys shulkers and drops automatically"), -1, Category.DONUT);
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
        if (this.delayCounter > 0) {
            --this.delayCounter;
            return;
        }
        final ScreenHandler currentScreenHandler = this.mc.player.currentScreenHandler;
        if (!(this.mc.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
            this.mc.getNetworkHandler().sendChatCommand("shop");
            this.delayCounter = 20;
            return;
        }
        if (((GenericContainerScreenHandler) currentScreenHandler).getRows() != 3) {
            return;
        }
        if (currentScreenHandler.getSlot(11).getStack().isOf(Items.END_STONE) && currentScreenHandler.getSlot(11).getStack().getCount() == 1) {
            this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 11, 0, SlotActionType.PICKUP, this.mc.player);
            this.delayCounter = 20;
            return;
        }
        if (currentScreenHandler.getSlot(17).getStack().isOf(Items.SHULKER_BOX)) {
            this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 17, 0, SlotActionType.PICKUP, this.mc.player);
            this.delayCounter = 20;
            return;
        }
        if (currentScreenHandler.getSlot(13).getStack().isOf(Items.SHULKER_BOX)) {
            this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 23, 0, SlotActionType.PICKUP, this.mc.player);
            this.delayCounter = this.delay.getIntValue();
            this.mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ALL_ITEMS, BlockPos.ORIGIN, Direction.DOWN));
        }
    }
}
