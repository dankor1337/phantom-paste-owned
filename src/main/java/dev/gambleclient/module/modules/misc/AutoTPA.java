package dev.gambleclient.module.modules.misc;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.ModeSetting;
import dev.gambleclient.module.setting.MinMaxSetting;
import dev.gambleclient.module.setting.StringSetting;
import dev.gambleclient.utils.EncryptedString;

public final class AutoTPA extends Module {
    private final MinMaxSetting delayRange = new MinMaxSetting(EncryptedString.of("Delay"), 1.0, 80.0, 1.0, 10.0, 30.0);
    private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.TPAHERE, Mode.class);
    private final StringSetting playerName = new StringSetting(EncryptedString.of("Player"), "DrDonutt");
    private int delayCounter;

    public AutoTPA() {
        super(EncryptedString.of("Auto Tpa"), EncryptedString.of("Module that helps you teleport streamers to you"), -1, Category.MISC);
        this.addSettings(this.mode, this.delayRange, this.playerName);
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
        if (this.delayCounter > 0) {
            --this.delayCounter;
            return;
        }
        final ClientPlayNetworkHandler networkHandler = this.mc.getNetworkHandler();
        String commandPrefix;
        if (this.mode.getValue().equals(Mode.TPA)) {
            commandPrefix = "tpa ";
        } else {
            commandPrefix = "tpahere ";
        }
        networkHandler.sendCommand(commandPrefix + this.playerName.getValue());
        this.delayCounter = this.delayRange.getRandomIntInRange();
    }

    enum Mode {
        TPA("Tpa", 0),
        TPAHERE("Tpahere", 1);

        Mode(final String name, final int ordinal) {
        }
    }
}