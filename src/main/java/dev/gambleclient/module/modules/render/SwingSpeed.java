package dev.gambleclient.module.modules.render;

import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.EncryptedString;

public final class SwingSpeed extends Module {
    private final NumberSetting swingSpeed = (NumberSetting) new NumberSetting(EncryptedString.of("Swing Speed"), 1, 20, 6, 1)
            .setDescription(EncryptedString.of("Speed of hand swinging animation"));

    public SwingSpeed() {
        super(EncryptedString.of("Swing Speed"),
                EncryptedString.of("Modifies the speed of hand swinging animation"),
                -1,
                Category.RENDER);
        addSettings(this.swingSpeed);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    public NumberSetting getSwingSpeed() {
        return this.swingSpeed;
    }
}
