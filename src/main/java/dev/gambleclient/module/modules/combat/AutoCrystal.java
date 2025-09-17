package dev.gambleclient.module.modules.combat;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.PreItemUseEvent;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BindSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.BlockUtil;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.KeyUtils;

public final class  AutoCrystal extends Module {
    private final BindSetting activateKey = new BindSetting(EncryptedString.of("Activate Key"), 1, false).setDescription(EncryptedString.of("Key that does the crystalling"));
    private final NumberSetting placeDelay = new NumberSetting(EncryptedString.of("Place Delay"), 0.0, 20.0, 0.0, 1.0);
    private final NumberSetting breakDelay = new NumberSetting(EncryptedString.of("Break Delay"), 0.0, 20.0, 0.0, 1.0);
    private int placeDelayCounter;
    private int breakDelayCounter;
    public boolean isActive;

    public AutoCrystal() {
        super(EncryptedString.of("Auto Crystal"), EncryptedString.of("Automatically crystals fast for you"), -1, Category.COMBAT);
        this.addSettings(this.activateKey, this.placeDelay, this.breakDelay);
    }

    @Override
    public void onEnable() {
        this.resetCounters();
        this.isActive = false;
        super.onEnable();
    }

    private void resetCounters() {
        this.placeDelayCounter = 0;
        this.breakDelayCounter = 0;
    }

    @EventListener
    public void onTick(final TickEvent tickEvent) {
        if (this.mc.currentScreen != null) {
            return;
        }
        this.updateCounters();
        if (this.mc.player.isUsingItem()) {
            return;
        }
        if (!this.isKeyActive()) {
            return;
        }
        if (this.mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL) {
            return;
        }
        this.handleInteraction();
    }

    private void updateCounters() {
        if (this.placeDelayCounter > 0) {
            --this.placeDelayCounter;
        }
        if (this.breakDelayCounter > 0) {
            --this.breakDelayCounter;
        }
    }

    private boolean isKeyActive() {
        final int d = this.activateKey.getValue();
        if (d != -1 && !KeyUtils.isKeyPressed(d)) {
            this.resetCounters();
            return this.isActive = false;
        }
        return this.isActive = true;
    }

    private void handleInteraction() {
        final HitResult crosshairTarget = this.mc.crosshairTarget;
        if (this.mc.crosshairTarget instanceof BlockHitResult) {
            this.handleBlockInteraction((BlockHitResult) crosshairTarget);
        } else if (this.mc.crosshairTarget instanceof final EntityHitResult entityHitResult) {
            this.handleEntityInteraction(entityHitResult);
        }
    }

    private void handleBlockInteraction(final BlockHitResult blockHitResult) {
        if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (this.placeDelayCounter > 0) {
            return;
        }
        final BlockPos blockPos = blockHitResult.getBlockPos();
        if ((BlockUtil.isBlockAtPosition(blockPos, Blocks.OBSIDIAN) || BlockUtil.isBlockAtPosition(blockPos, Blocks.BEDROCK)) && this.isValidCrystalPlacement(blockPos)) {
            BlockUtil.interactWithBlock(blockHitResult, true);
            this.placeDelayCounter = this.placeDelay.getIntValue();
        }
    }

    private void handleEntityInteraction(final EntityHitResult entityHitResult) {
        if (this.breakDelayCounter > 0) {
            return;
        }
        final Entity entity = entityHitResult.getEntity();
        if (!(entity instanceof EndCrystalEntity) && !(entity instanceof SlimeEntity)) {
            return;
        }
        this.mc.interactionManager.attackEntity(this.mc.player, entity);
        this.mc.player.swingHand(Hand.MAIN_HAND);
        this.breakDelayCounter = this.breakDelay.getIntValue();
    }

    @EventListener
    public void onPreItemUse(final PreItemUseEvent preItemUseEvent) {
        if (this.mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL) {
            return;
        }
        if (!(this.mc.crosshairTarget instanceof BlockHitResult blockHitResult)) {
            return;
        }
        if (this.mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            return;
        }
        final BlockPos blockPos = blockHitResult.getBlockPos();
        if (BlockUtil.isBlockAtPosition(blockPos, Blocks.OBSIDIAN) || BlockUtil.isBlockAtPosition(blockPos, Blocks.BEDROCK)) {
            preItemUseEvent.cancel();
        }
    }

    private boolean isValidCrystalPlacement(final BlockPos blockPos) {
        final BlockPos up = blockPos.up();
        if (!this.mc.world.isAir(up)) {
            return false;
        }
        final int getX = up.getX();
        final int getY = up.getY();
        final int compareTo = up.getZ();
        return this.mc.world.getOtherEntities(null, new Box(getX, getY, compareTo, getX + 1.0, getY + 2.0, compareTo + 1.0)).isEmpty();
    }
}
