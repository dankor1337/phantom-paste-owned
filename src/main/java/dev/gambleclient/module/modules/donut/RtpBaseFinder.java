package dev.gambleclient.module.modules.donut;

import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import dev.gambleclient.Gamble;
import dev.gambleclient.module.modules.misc.AutoEat;
import dev.gambleclient.module.modules.combat.AutoTotem;
import dev.gambleclient.utils.embed.DiscordWebhook;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.mixin.MobSpawnerLogicAccessor;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.ModeSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.module.setting.StringSetting;
import dev.gambleclient.utils.BlockUtil;
import dev.gambleclient.utils.EnchantmentUtil;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.InventoryUtil;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Random;

public final class RtpBaseFinder extends Module {
    public final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.RANDOM, Mode.class);
    private final BooleanSetting spawn = new BooleanSetting(EncryptedString.of("Spawners"), true);
    private final NumberSetting minStorage = new NumberSetting(EncryptedString.of("Minimum Storage"), 1.0, 500.0, 100.0, 1.0);
    private final BooleanSetting autoTotemBuy = new BooleanSetting(EncryptedString.of("Auto Totem Buy"), true);
    private final NumberSetting totemSlot = new NumberSetting(EncryptedString.of("Totem Slot"), 1.0, 9.0, 8.0, 1.0);
    private final BooleanSetting autoMend = new BooleanSetting(EncryptedString.of("Auto Mend"), true).setDescription(EncryptedString.of("Automatically repairs pickaxe."));
    private final NumberSetting xpBottleSlot = new NumberSetting(EncryptedString.of("XP Bottle Slot"), 1.0, 9.0, 9.0, 1.0);
    private final BooleanSetting discordNotification = new BooleanSetting(EncryptedString.of("Discord Notification"), false);
    private final StringSetting webhook = new StringSetting(EncryptedString.of("Webhook"), "");
    private final BooleanSetting totemCheck = new BooleanSetting(EncryptedString.of("Totem Check"), true);
    private final NumberSetting totemCheckTime = new NumberSetting(EncryptedString.of("Totem Check Time"), 1.0, 120.0, 20.0, 1.0);
    private final NumberSetting digToY = new NumberSetting(EncryptedString.of("Dig To Y"), -59.0, 30.0, -20.0, 1.0);
    private Vec3d currentPosition;
    private Vec3d previousPosition;
    private double idleTime;
    private double totemCheckCounter = 0.0;
    private boolean isDigging = false;
    private boolean shouldDig = false;
    private boolean isRepairing = false;
    private boolean isBuyingTotem = false;
    private int selectedSlot = 0;
    private int rtpCooldown = 0;
    private int actionDelay = 0;
    private int totemBuyCounter = 0;
    private int spawnerCounter = 0;

    public RtpBaseFinder() {
        super(EncryptedString.of("Rtp Base Finder"), EncryptedString.of("Automatically searches for bases on DonutSMP"), -1, Category.DONUT);
        this.addSettings(this.mode, this.spawn, this.minStorage, this.autoTotemBuy, this.totemSlot, this.autoMend, this.xpBottleSlot, this.discordNotification, this.webhook, this.totemCheck, this.totemCheckTime, this.digToY);
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
        if (this.actionDelay > 0) {
            --this.actionDelay;
            return;
        }
        this.scanForEntities();
        if (this.autoTotemBuy.getValue()) {
            final int n = this.totemSlot.getIntValue() - 1;
            if (!this.mc.player.getInventory().getStack(n).isOf(Items.TOTEM_OF_UNDYING)) {
                if (this.totemBuyCounter < 30 && !this.isBuyingTotem) {
                    ++this.totemBuyCounter;
                    return;
                }
                this.totemBuyCounter = 0;
                this.isBuyingTotem = true;
                if (this.mc.player.getInventory().selectedSlot != n) {
                    InventoryUtil.swap(n);
                }
                final ScreenHandler currentScreenHandler = this.mc.player.currentScreenHandler;
                if (!(this.mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) || ((GenericContainerScreenHandler) currentScreenHandler).getRows() != 3) {
                    this.mc.getNetworkHandler().sendChatCommand("shop");
                    this.actionDelay = 10;
                    return;
                }
                if (currentScreenHandler.getSlot(11).getStack().isOf(Items.END_STONE)) {
                    this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 13, 0, SlotActionType.PICKUP, this.mc.player);
                    this.actionDelay = 10;
                    return;
                }
                if (currentScreenHandler.getSlot(16).getStack().isOf(Items.EXPERIENCE_BOTTLE)) {
                    this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 13, 0, SlotActionType.PICKUP, this.mc.player);
                    this.actionDelay = 10;
                    return;
                }
                this.mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ALL_ITEMS, BlockPos.ORIGIN, Direction.DOWN));
                if (currentScreenHandler.getSlot(23).getStack().isOf(Items.LIME_STAINED_GLASS_PANE)) {
                    this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 23, 0, SlotActionType.PICKUP, this.mc.player);
                    this.actionDelay = 10;
                    return;
                }
                this.mc.getNetworkHandler().sendChatCommand("shop");
                this.actionDelay = 10;
                return;
            } else if (this.isBuyingTotem) {
                if (this.mc.currentScreen != null) {
                    this.mc.player.closeHandledScreen();
                    this.actionDelay = 20;
                }
                this.isBuyingTotem = false;
                this.totemBuyCounter = 0;
            }
        }
        if (this.isRepairing) {
            final int n2 = this.xpBottleSlot.getIntValue() - 1;
            final ItemStack getStack = this.mc.player.getInventory().getStack(n2);
            if (this.mc.player.getInventory().selectedSlot != n2) {
                InventoryUtil.swap(n2);
            }
            if (!getStack.isOf(Items.EXPERIENCE_BOTTLE)) {
                final ScreenHandler fishHook = this.mc.player.currentScreenHandler;
                if (!(this.mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) || ((GenericContainerScreenHandler) fishHook).getRows() != 3) {
                    this.mc.getNetworkHandler().sendChatCommand("shop");
                    this.actionDelay = 10;
                    return;
                }
                if (fishHook.getSlot(11).getStack().isOf(Items.END_STONE)) {
                    this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 13, 0, SlotActionType.PICKUP, this.mc.player);
                    this.actionDelay = 10;
                    return;
                }
                if (fishHook.getSlot(16).getStack().isOf(Items.EXPERIENCE_BOTTLE)) {
                    this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 16, 0, SlotActionType.PICKUP, this.mc.player);
                    this.actionDelay = 10;
                    return;
                }
                if (fishHook.getSlot(17).getStack().isOf(Items.LIME_STAINED_GLASS_PANE)) {
                    this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 17, 0, SlotActionType.PICKUP, this.mc.player);
                    this.actionDelay = 10;
                    return;
                }
                this.mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ALL_ITEMS, BlockPos.ORIGIN, Direction.DOWN));
                if (fishHook.getSlot(23).getStack().isOf(Items.LIME_STAINED_GLASS_PANE)) {
                    this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 23, 0, SlotActionType.PICKUP, this.mc.player);
                    this.actionDelay = 10;
                    return;
                }
                this.mc.getNetworkHandler().sendChatCommand("shop");
                this.actionDelay = 10;
            } else {
                if (this.mc.currentScreen != null) {
                    this.mc.player.closeHandledScreen();
                    this.actionDelay = 20;
                    return;
                }
                if (!EnchantmentUtil.hasEnchantment(this.mc.player.getOffHandStack(), Enchantments.MENDING)) {
                    this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 36 + this.selectedSlot, 40, SlotActionType.SWAP, this.mc.player);
                    this.actionDelay = 20;
                    return;
                }
                if (this.mc.player.getOffHandStack().getDamage() > 0) {
                    final ActionResult interactItem = this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
                    if (interactItem.isAccepted() && interactItem.shouldSwingHand()) {
                        this.mc.player.swingHand(Hand.MAIN_HAND);
                    }
                    this.actionDelay = 1;
                    return;
                }
                this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, 36 + this.selectedSlot, 40, SlotActionType.SWAP, this.mc.player);
                this.isRepairing = false;
            }
        } else {
            if (this.shouldDig) {
                this.handleAutoEat();
            }
            if (this.totemCheck.getValue()) {
                final boolean equals = this.mc.player.getOffHandStack().getItem().equals(Items.TOTEM_OF_UNDYING);
                final Module moduleByClass = Gamble.INSTANCE.MODULE_MANAGER.getModuleByClass(AutoTotem.class);
                if (equals) {
                    this.totemCheckCounter = 0.0;
                } else if (moduleByClass.isEnabled() && ((AutoTotem) moduleByClass).findItemSlot(Items.TOTEM_OF_UNDYING) != -1) {
                    this.totemCheckCounter = 0.0;
                } else {
                    ++this.totemCheckCounter;
                }
                if (this.totemCheckCounter > this.totemCheckTime.getValue()) {
                    this.notifyTotemExplosion("Your totem exploded", (int) this.mc.player.getX(), (int) this.mc.player.getY(), (int) this.mc.player.getZ());
                    return;
                }
            }
            if (this.rtpCooldown > 0) {
                --this.rtpCooldown;
                if (this.rtpCooldown < 1) {
                    if (this.previousPosition != null && this.previousPosition.distanceTo(this.mc.player.getPos()) < 100.0) {
                        this.sendRtpCommand();
                        return;
                    }
                    this.mc.player.setPitch(89.9f);
                    if (this.autoMend.getValue()) {
                        final ItemStack size = this.mc.player.getMainHandStack();
                        if (EnchantmentUtil.hasEnchantment(size, Enchantments.MENDING) && size.getMaxDamage() - size.getDamage() < 100) {
                            this.isRepairing = true;
                            this.selectedSlot = this.mc.player.getInventory().selectedSlot;
                        }
                    }
                    this.shouldDig = true;
                }
                return;
            }
            if (this.currentPosition != null && this.currentPosition.distanceTo(this.mc.player.getPos()) < 2.0) {
                ++this.idleTime;
            } else {
                this.currentPosition = this.mc.player.getPos();
                this.idleTime = 0.0;
            }
            if (this.idleTime > 20.0 && this.isDigging) {
                this.sendRtpCommand();
                this.isDigging = false;
                return;
            }
            if (this.idleTime > 200.0) {
                this.sendRtpCommand();
                this.idleTime = 0.0;
                return;
            }
            if (this.mc.player.getY() < this.digToY.getIntValue() && !this.isDigging) {
                this.isDigging = true;
                this.shouldDig = false;
            }
        }
    }

    private void sendRtpCommand() {
        this.shouldDig = false;
        final ClientPlayNetworkHandler networkHandler = this.mc.getNetworkHandler();
        Mode l;
        if (this.mode.getValue() == Mode.RANDOM) {
            l = this.getRandomMode();
        } else {
            l = (Mode) this.mode.getValue();
        }
        networkHandler.sendChatCommand("rtp " + this.getModeName(l));
        this.rtpCooldown = 150;
        this.idleTime = 0.0;
        this.previousPosition = new Vec3d(this.mc.player.getPos().toVector3f());
    }

    private void disconnectWithMessage(final Text text) {
        final MutableText literal = Text.literal("[RTPBaseFinder] ");
        literal.append(text);
        this.toggle();
        this.mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(literal));
    }

    private Mode getRandomMode() {
        final Mode[] array = {Mode.EUCENTRAL, Mode.EUWEST, Mode.EAST, Mode.WEST, Mode.ASIA, Mode.OCEANIA};
        return array[new Random().nextInt(array.length)];
    }

    private String getModeName(final Mode mode) {
        final int n = mode.ordinal() ^ 0x706A485C;
        int n2;
        if (n != 0) {
            n2 = ((n * 31 >>> 4) % n ^ n >>> 16);
        } else {
            n2 = 0;
        }
        String name = null;
        switch (n2) {
            case 164469854: {
                name = "eu west";
                break;
            }
            case 164469848: {
                name = "eu central";
                break;
            }
            default: {
                name = mode.name();
                break;
            }
        }
        return name;
    }

    private void handleAutoEat() {
        final Module moduleByClass = Gamble.INSTANCE.MODULE_MANAGER.getModuleByClass(AutoEat.class);
        if (!moduleByClass.isEnabled()) {
            this.handleBlockBreaking(true);
            return;
        }
        if (((AutoEat) moduleByClass).shouldEat()) {
            return;
        }
        this.handleBlockBreaking(true);
    }

    private void handleBlockBreaking(final boolean b) {
        if (this.mc.player.getPitch() != 89.9f) {
            this.mc.player.setPitch(89.9f);
        }
        if (!this.mc.player.isUsingItem()) {
            if (b && this.mc.crosshairTarget != null && this.mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                final BlockHitResult blockHitResult = (BlockHitResult) this.mc.crosshairTarget;
                final BlockPos blockPos = ((BlockHitResult) this.mc.crosshairTarget).getBlockPos();
                if (!this.mc.world.getBlockState(blockPos).isAir()) {
                    final Direction side = blockHitResult.getSide();
                    if (this.mc.interactionManager.updateBlockBreakingProgress(blockPos, side)) {
                        this.mc.particleManager.addBlockBreakingParticles(blockPos, side);
                        this.mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            } else {
                this.mc.interactionManager.cancelBlockBreaking();
            }
        }
    }

    private void scanForEntities() {
        int n = 0;
        int n2 = 0;
        BlockPos blockPos = null;
        final Iterator iterator = BlockUtil.getLoadedChunks().iterator();
        while (iterator.hasNext()) {
            for (final Object next : ((WorldChunk) iterator.next()).getBlockEntityPositions()) {
                final BlockEntity getBlockEntity = this.mc.world.getBlockEntity((BlockPos) next);
                if (this.spawn.getValue() && getBlockEntity instanceof MobSpawnerBlockEntity) {
                    final String string = ((MobSpawnerLogicAccessor) ((MobSpawnerBlockEntity) getBlockEntity).getLogic()).getSpawnEntry().getNbt().getString("id");
                    if (string != "minecraft:cave_spider" && string != "minecraft:spider") {
                        ++n2;
                        blockPos = (BlockPos) next;
                    }
                }
                if (getBlockEntity instanceof ChestBlockEntity || getBlockEntity instanceof EnderChestBlockEntity || getBlockEntity instanceof ShulkerBoxBlockEntity || getBlockEntity instanceof FurnaceBlockEntity || getBlockEntity instanceof BarrelBlockEntity || getBlockEntity instanceof EnchantingTableBlockEntity) {
                    ++n;
                }
            }
        }
        if (n2 > 0) {
            ++this.spawnerCounter;
        } else {
            this.spawnerCounter = 0;
        }
        if (this.spawnerCounter > 10) {
            this.notifyBaseOrSpawner("YOU FOUND SPAWNER", blockPos.getX(), blockPos.getY(), blockPos.getZ(), false);
            this.spawnerCounter = 0;
        }
        if (n > this.minStorage.getIntValue()) {
            this.notifyBaseOrSpawner("YOU FOUND BASE", (int) this.mc.player.getPos().x, (int) this.mc.player.getPos().y, (int) this.mc.player.getPos().z, true);
        }
    }

    private void notifyBaseOrSpawner(final String s, final int n, final int n2, final int n3, final boolean b) {
        String s2;
        if (b) {
            s2 = "Base";
        } else {
            s2 = "Spawner";
        }
        if (this.discordNotification.getValue()) {
            final DiscordWebhook embedSender = new DiscordWebhook(this.webhook.value);
            final DiscordWebhook.EmbedObject bn = new DiscordWebhook.EmbedObject();
            bn.setTitle(s2);
            bn.setThumbnail("https://render.crafty.gg/3d/bust/" + MinecraftClient.getInstance().getSession().getUuidOrNull() + "?format=webp");
            bn.setDescription(s2 + " Found - " + MinecraftClient.getInstance().getSession().getUsername());
            bn.setColor(Color.GRAY);
            bn.setFooter(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), null);
            bn.addField(s2 + "Found at", "x: " + n + " y: " + n2 + " z: " + n3, true);
            embedSender.addEmbed(bn);
            try {
                embedSender.execute();
            } catch (final Throwable ex) {
            }
        }
        this.toggle();
        this.disconnectWithMessage(Text.of(s));
    }

    private void notifyTotemExplosion(final String s, final int n, final int n2, final int n3) {
        if (this.discordNotification.getValue()) {
            final DiscordWebhook embedSender = new DiscordWebhook(this.webhook.value);
            final DiscordWebhook.EmbedObject bn = new DiscordWebhook.EmbedObject();
            bn.setTitle("Totem Exploded");
            bn.setThumbnail("https://render.crafty.gg/3d/bust/" + MinecraftClient.getInstance().getSession().getUuidOrNull() + "?format=webp");
            bn.setDescription("Your Totem Exploded - " + MinecraftClient.getInstance().getSession().getUsername());
            bn.setColor(Color.RED);
            bn.setFooter(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), null);
            bn.addField("Location", "x: " + n + " y: " + n2 + " z: " + n3, true);
            embedSender.addEmbed(bn);
            try {
                embedSender.execute();
            } catch (final Throwable ignored) {
            }
        }
        this.disconnectWithMessage(Text.of(s));
    }

    public boolean isRepairingActive() {
        return this.isRepairing;
    }

    enum Mode {
        EUCENTRAL("eucentral", 0),
        EUWEST("euwest", 1),
        EAST("east", 2),
        WEST("west", 3),
        ASIA("asia", 4),
        OCEANIA("oceania", 5),
        RANDOM("random", 6);

        Mode(final String name, final int ordinal) {
        }
    }

}
