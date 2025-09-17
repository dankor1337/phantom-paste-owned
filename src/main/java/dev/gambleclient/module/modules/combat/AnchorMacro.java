package dev.gambleclient.module.modules.combat;

import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.BlockUtil;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.InventoryUtil;
import dev.gambleclient.utils.KeyUtils;

public final class AnchorMacro extends Module {
    private final NumberSetting switchDelay = new NumberSetting(EncryptedString.of("Switch Delay"), 0.0, 20.0, 0.0, 1.0);
    private final NumberSetting glowstoneDelay = new NumberSetting(EncryptedString.of("Glowstone Delay"), 0.0, 20.0, 0.0, 1.0);
    private final NumberSetting explodeDelay = new NumberSetting(EncryptedString.of("Explode Delay"), 0.0, 20.0, 0.0, 1.0);
    private final NumberSetting totemSlot = new NumberSetting(EncryptedString.of("Totem Slot"), 1.0, 9.0, 1.0, 1.0);
    private int keybind;
    private int glowstoneDelayCounter;
    private int explodeDelayCounter;

    public AnchorMacro() {
        super(EncryptedString.of("Anchor Macro"), EncryptedString.of("Automatically blows up respawn anchors for you"), -1, Category.COMBAT);
        this.keybind = 0;
        this.glowstoneDelayCounter = 0;
        this.explodeDelayCounter = 0;
        this.addSettings(this.switchDelay, this.glowstoneDelay, this.explodeDelay, this.totemSlot);
    }

    @Override
    public void onEnable() {
        this.resetCounters();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onTick(final TickEvent tickEvent) {
        if (this.mc.currentScreen != null) {
            return;
        }
        if (this.isShieldOrFoodActive()) {
            return;
        }
        if (KeyUtils.isKeyPressed(1)) {
            this.handleAnchorInteraction();
        }
    }

    private boolean isShieldOrFoodActive() {
        final boolean isFood = this.mc.player.getMainHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD) || this.mc.player.getOffHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD);
        final boolean isShield = this.mc.player.getMainHandStack().getItem() instanceof ShieldItem || this.mc.player.getOffHandStack().getItem() instanceof ShieldItem;
        final boolean isRightClickPressed = GLFW.glfwGetMouseButton(this.mc.getWindow().getHandle(), 1) == 1;
        return (isFood || isShield) && isRightClickPressed;
    }

    private void handleAnchorInteraction() {
        if (!(this.mc.crosshairTarget instanceof BlockHitResult blockHitResult)) {
            return;
        }
        if (!BlockUtil.isBlockAtPosition(blockHitResult.getBlockPos(), Blocks.RESPAWN_ANCHOR)) {
            return;
        }
        this.mc.options.useKey.setPressed(false);
        if (BlockUtil.isRespawnAnchorUncharged(blockHitResult.getBlockPos())) {
            this.placeGlowstone(blockHitResult);
        } else if (BlockUtil.isRespawnAnchorCharged(blockHitResult.getBlockPos())) {
            this.explodeAnchor(blockHitResult);
        }
    }

    private void placeGlowstone(final BlockHitResult blockHitResult) {
        if (!this.mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            if (this.keybind < this.switchDelay.getIntValue()) {
                ++this.keybind;
                return;
            }
            this.keybind = 0;
            InventoryUtil.swap(Items.GLOWSTONE);
        }
        if (this.mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            if (this.glowstoneDelayCounter < this.glowstoneDelay.getIntValue()) {
                ++this.glowstoneDelayCounter;
                return;
            }
            this.glowstoneDelayCounter = 0;
            BlockUtil.interactWithBlock(blockHitResult, true);
        }
    }

    private void explodeAnchor(final BlockHitResult blockHitResult) {
        final int selectedSlot = this.totemSlot.getIntValue() - 1;
        if (this.mc.player.getInventory().selectedSlot != selectedSlot) {
            if (this.keybind < this.switchDelay.getIntValue()) {
                ++this.keybind;
                return;
            }
            this.keybind = 0;
            this.mc.player.getInventory().selectedSlot = selectedSlot;
        }
        if (this.mc.player.getInventory().selectedSlot == selectedSlot) {
            if (this.explodeDelayCounter < this.explodeDelay.getIntValue()) {
                ++this.explodeDelayCounter;
                return;
            }
            this.explodeDelayCounter = 0;
            BlockUtil.interactWithBlock(blockHitResult, true);
        }
    }

    private void resetCounters() {
        this.keybind = 0;
        this.glowstoneDelayCounter = 0;
        this.explodeDelayCounter = 0;
    }
}