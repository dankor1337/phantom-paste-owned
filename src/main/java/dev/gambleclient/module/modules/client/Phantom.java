package dev.gambleclient.module.modules.client;

import dev.gambleclient.Gamble;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.PacketReceiveEvent;
import dev.gambleclient.gui.ClickGUI;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.ModeSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.EncryptedString;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class Phantom extends Module {
    public static final NumberSetting redColor = new NumberSetting(EncryptedString.of("Red"), 0.0, 255.0, 120.0, 1.0);
    public static final NumberSetting greenColor = new NumberSetting(EncryptedString.of("Green"), 0.0, 255.0, 190.0, 1.0);
    public static final NumberSetting blueColor = new NumberSetting(EncryptedString.of("Blue"), 0.0, 255.0, 255.0, 1.0);
    public static final NumberSetting windowAlpha = new NumberSetting(EncryptedString.of("Window Alpha"), 0.0, 255.0, 170.0, 1.0);
    public static final BooleanSetting enableBreathingEffect = new BooleanSetting(EncryptedString.of("Breathing"), false).setDescription(EncryptedString.of("Color breathing effect (only with rainbow off)"));
    public static final BooleanSetting enableRainbowEffect = new BooleanSetting(EncryptedString.of("Rainbow"), false).setDescription(EncryptedString.of("Enables LGBTQ mode"));
    public static final BooleanSetting renderBackground = new BooleanSetting(EncryptedString.of("Background"), true).setDescription(EncryptedString.of("Renders the background of the Click Gui"));
    public static final BooleanSetting useCustomFont = new BooleanSetting(EncryptedString.of("Custom Font"), true);
    private final BooleanSetting preventClose = new BooleanSetting(EncryptedString.of("Prevent Close"), true).setDescription(EncryptedString.of("For servers with freeze plugins that don't let you open the GUI"));
    public static final NumberSetting cornerRoundness = new NumberSetting(EncryptedString.of("Roundness"), 1.0, 10.0, 5.0, 1.0);
    public static final ModeSetting<AnimationMode> animationMode = new ModeSetting<>(EncryptedString.of("Animations"), AnimationMode.NORMAL, AnimationMode.class);
    public static final BooleanSetting enableMSAA = new BooleanSetting(EncryptedString.of("MSAA"), true).setDescription(EncryptedString.of("Anti Aliasing | This can impact performance if you're using tracers but gives them a smoother look |"));
    public boolean shouldPreventClose;

    public Phantom() {
        super(EncryptedString.of("Phantom++"), EncryptedString.of("Settings for the client"), 344, Category.CLIENT);
        this.addSettings(Phantom.redColor, Phantom.greenColor, Phantom.blueColor, Phantom.windowAlpha, Phantom.renderBackground, this.preventClose, Phantom.cornerRoundness, Phantom.animationMode, Phantom.enableMSAA);
    }

    @Override
    public void onEnable() {
        Gamble.INSTANCE.screen = this.mc.currentScreen;
        if (Gamble.INSTANCE.GUI != null) {
            this.mc.setScreenAndRender(Gamble.INSTANCE.GUI);
        } else if (this.mc.currentScreen instanceof InventoryScreen) {
            shouldPreventClose = true;
        }
        if (new Random().nextInt(3) == 1) {
            CompletableFuture.runAsync(() -> {
            });
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (this.mc.currentScreen instanceof ClickGUI) {
            Gamble.INSTANCE.GUI.close();
            this.mc.setScreenAndRender(Gamble.INSTANCE.screen);
            Gamble.INSTANCE.GUI.onGuiClose();
        } else if (this.mc.currentScreen instanceof InventoryScreen) {
            shouldPreventClose = false;
        }
        super.onDisable();
    }

    @EventListener
    public void onPacketReceive(final PacketReceiveEvent packetReceiveEvent) {
        if (shouldPreventClose && packetReceiveEvent.packet instanceof OpenScreenS2CPacket && this.preventClose.getValue()) {
            packetReceiveEvent.cancel();
        }
    }

    public enum AnimationMode {
        NORMAL("Normal", 0),
        POSITIVE("Positive", 1),
        OFF("Off", 2);

        AnimationMode(final String name, final int ordinal) {
        }
    }
}