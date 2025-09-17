package dev.gambleclient.module.modules.client;

import dev.gambleclient.Gamble;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.module.setting.StringSetting;
import dev.gambleclient.utils.EncryptedString;

public final class ConfigDebug extends Module {
    private final BooleanSetting testBoolean = new BooleanSetting(EncryptedString.of("Test Boolean"), false)
            .setDescription(EncryptedString.of("Test boolean setting"));
    
    private final NumberSetting testNumber = (NumberSetting) new NumberSetting(EncryptedString.of("Test Number"), 1, 100, 50, 1)
            .setDescription(EncryptedString.of("Test number setting"));
    
    private final StringSetting testString = new StringSetting(EncryptedString.of("Test String"), "Default Value")
            .setDescription(EncryptedString.of("Test string setting"));

    public ConfigDebug() {
        super(EncryptedString.of("Config Debug"), 
              EncryptedString.of("Debug module for testing config saving"), 
              -1, 
              Category.CLIENT);
        addSettings(this.testBoolean, this.testNumber, this.testString);
    }

    @Override
    public void onEnable() {
        System.out.println("[ConfigDebug] Module enabled - testing config saving...");
        
        // Test changing some values
        testBoolean.setValue(true);
        testNumber.getValue(75.0);
        testString.setValue("Test Value Changed");
        
        // Force save config
        Gamble.INSTANCE.getConfigManager().manualSave();
        
        System.out.println("[ConfigDebug] Config save test completed. Check console for results.");
        
        // Disable the module after testing
        toggle();
    }

    @Override
    public void onDisable() {
        // Nothing to do here
    }
}
