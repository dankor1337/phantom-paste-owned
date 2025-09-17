package dev.gambleclient.module.modules.donut;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.ModeSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.module.setting.ItemSetting;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.InventoryUtil;

public final class AutoSell extends Module {
    private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.SELL, Mode.class);
    private final ItemSetting sellItem = new ItemSetting(EncryptedString.of("Sell Item"), Items.SEA_PICKLE);
    private final NumberSetting delay = new NumberSetting(EncryptedString.of("delay"), 0.0, 20.0, 2.0, 1.0).getValue(EncryptedString.of("What should be delay in ticks"));
    private int delayCounter;
    private boolean isSlotProcessed;

    public AutoSell() {
        super(EncryptedString.of("Auto Sell"), EncryptedString.of("Automatically sells items"), -1, Category.DONUT);
        addSettings(this.mode, this.sellItem, this.delay);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.delayCounter = 20;
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onTick(final TickEvent event) {
        if (this.mc.player == null || this.mc.interactionManager == null) {
            return;
        }
        if (this.delayCounter > 0) {
            --this.delayCounter;
            return;
        }
        Mode currentMode = (Mode) this.mode.getValue();
        System.out.println("AutoSell: Current mode: " + currentMode.name());

        if (currentMode.equals(Mode.SELL)) {
            final ScreenHandler currentScreenHandler = this.mc.player.currentScreenHandler;
            if (!(this.mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) || ((GenericContainerScreenHandler) currentScreenHandler).getRows() != 5) {
                if (this.mc.getNetworkHandler() != null) {
                    this.mc.getNetworkHandler().sendChatCommand("sell");
                }
                this.delayCounter = 20;
                return;
            }
            // Check if there are items to sell (non-empty slots)
            boolean hasItemsToSell = false;
            Item targetItem = this.sellItem.getItem();

            // Debug: Print the target item for verification
            System.out.println("AutoSell: Looking for item: " + targetItem.getName().getString());

            for (int i = 45; i < 54 && i < this.mc.player.currentScreenHandler.getStacks().size(); i++) {
                Item item = this.mc.player.currentScreenHandler.getStacks().get(i).getItem();
                if (item != net.minecraft.item.Items.AIR) {
                    System.out.println("AutoSell: Found item at slot " + i + ": " + item.getName().getString());
                    if (item.equals(targetItem)) {
                        System.out.println("AutoSell: Selling item at slot " + i);
                        this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, i, 1, SlotActionType.QUICK_MOVE, this.mc.player);
                        this.delayCounter = this.delay.getIntValue();
                        return;
                    }
                }
            }
            // If no items found to sell, close the screen
            System.out.println("AutoSell: No target items found, closing screen");
            this.mc.player.closeHandledScreen();
            this.delayCounter = 20;
            return;
        } else {
            final ScreenHandler currentScreenHandler = this.mc.player.currentScreenHandler;
            if (!(this.mc.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
                if (this.mc.getNetworkHandler() != null) {
                    this.mc.getNetworkHandler().sendChatCommand("order " + this.getOrderCommand());
                }
                this.delayCounter = 20;
                return;
            }
            if (((GenericContainerScreenHandler) currentScreenHandler).getRows() == 6) {
                ItemStack stack = null;
                if (47 < currentScreenHandler.slots.size()) {
                    stack = currentScreenHandler.getSlot(47).getStack();
                    if (stack.isOf(net.minecraft.item.Items.AIR)) {
                        this.delayCounter = 2;
                        return;
                    }
                } else {
                    this.delayCounter = 2;
                    return;
                }

                if (stack != null) {
                    for (final Object next : stack.getTooltip(Item.TooltipContext.create(this.mc.world), this.mc.player, TooltipType.BASIC)) {
                        final String string = next.toString();
                        if (string.contains("Most Money Per Item") && (((Text) next).getStyle().toString().contains("white") || string.contains("white"))) {
                            if (47 < currentScreenHandler.slots.size()) {
                                this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 47, 1, SlotActionType.QUICK_MOVE, this.mc.player);
                            }
                            this.delayCounter = 5;
                            return;
                        }
                    }
                }
                Item targetItem = this.sellItem.getItem();
                System.out.println("AutoSell ORDER: Looking for item: " + targetItem.getName().getString());

                for (int i = 0; i < 44 && i < currentScreenHandler.slots.size(); ++i) {
                    ItemStack currentStack = currentScreenHandler.getSlot(i).getStack();
                    if (!currentStack.isOf(net.minecraft.item.Items.AIR)) {
                        System.out.println("AutoSell ORDER: Found item at slot " + i + ": " + currentStack.getItem().getName().getString());
                        if (currentStack.isOf(targetItem)) {
                            System.out.println("AutoSell ORDER: Ordering item at slot " + i);
                            this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, i, 1, SlotActionType.QUICK_MOVE, this.mc.player);
                            this.delayCounter = 10;
                            return;
                        }
                    }
                }
                this.delayCounter = 40;
                this.mc.player.closeHandledScreen();
                return;
            } else if (((GenericContainerScreenHandler) currentScreenHandler).getRows() == 4) {
                final int emptySlotCount = InventoryUtil.getSlot(net.minecraft.item.Items.AIR);
                if (emptySlotCount <= 0) {
                    this.mc.player.closeHandledScreen();
                    this.delayCounter = 10;
                    return;
                }
                if (this.isSlotProcessed && emptySlotCount == 36) {
                    this.isSlotProcessed = false;
                    this.mc.player.closeHandledScreen();
                    return;
                }
                final Item targetItem = this.sellItem.getItem();
                // Check if the target item is in the first slot (36)
                if (36 < this.mc.player.currentScreenHandler.getStacks().size()) {
                    final Item item = this.mc.player.currentScreenHandler.getStacks().get(36).getItem();
                    if (item != net.minecraft.item.Items.AIR && item == targetItem) {
                        this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 36, 1, SlotActionType.QUICK_MOVE, this.mc.player);
                        this.delayCounter = this.delay.getIntValue();
                        return;
                    }
                }
                // If no target item found, close the screen
                this.mc.player.closeHandledScreen();
                this.delayCounter = 20;
                return;
            } else if (((GenericContainerScreenHandler) currentScreenHandler).getRows() == 3) {
                if (15 < currentScreenHandler.slots.size()) {
                    this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 15, 1, SlotActionType.QUICK_MOVE, this.mc.player);
                }
                this.delayCounter = 10;
                return;
            }
        }
        this.delayCounter = 20;
    }



    private String getOrderCommand() {
        final Item targetItem = this.sellItem.getItem();
        String command;
        if (targetItem.equals(Items.BONE)) {
            command = "Bones";
        } else {
            command = targetItem.getName().getString();
        }
        System.out.println("AutoSell: Sending order command: " + command + " for item: " + targetItem.getName().getString());
        return command;
    }

    public enum Mode {
        SELL("Sell", 0),
        ORDER("Order", 1);

        Mode(final String name, final int ordinal) {
        }
    }


}
