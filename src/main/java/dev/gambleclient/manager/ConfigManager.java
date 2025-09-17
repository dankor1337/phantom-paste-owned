package dev.gambleclient.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import dev.gambleclient.Gamble;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.Setting;
import dev.gambleclient.module.setting.*;
import dev.gambleclient.utils.EncryptedString;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ConfigManager {
    private JsonObject jsonObject;
    private final File configFile;
    private final Gson gson;

    public ConfigManager() {
        // Use a more reliable location - user's home directory
        String userHome = System.getProperty("user.home");
        String configDir = userHome + File.separator + ".minecraft" + File.separator + "config";
        File configFolder = new File(configDir);
        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }

        this.configFile = new File(configFolder, "krypton_config.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.jsonObject = new JsonObject();

        // Load existing config if it exists
        loadConfigFromFile();
    }

    private void loadConfigFromFile() {
        try {
            if (configFile.exists()) {
                try (FileReader reader = new FileReader(configFile)) {
                    this.jsonObject = gson.fromJson(reader, JsonObject.class);
                    if (this.jsonObject == null) {
                        this.jsonObject = new JsonObject();
                    }

                }
            } else {
                this.jsonObject = new JsonObject();
            }
        } catch (Exception e) {
            System.err.println("[ConfigManager] Error loading config file: " + e.getMessage());
            e.printStackTrace();
            this.jsonObject = new JsonObject();
        }
    }

    public void loadProfile() {
        try {
            if (this.jsonObject == null) {
                this.jsonObject = new JsonObject();
                return;
            }


            int modulesLoaded = 0;

            for (final Module next : Gamble.INSTANCE.getModuleManager().c()) {
                try {
                    String moduleName = getModuleName(next);
                    final JsonElement value = this.jsonObject.get(moduleName);
                    if (value != null) {
                        if (!value.isJsonObject()) {
                            continue;
                        }
                        final JsonObject asJsonObject = value.getAsJsonObject();
                        final JsonElement value2 = asJsonObject.get("enabled");
                        if (value2 != null && value2.isJsonPrimitive() && value2.getAsBoolean()) {
                            next.toggle(true);
                        }
                        for (final Object next2 : next.getSettings()) {
                            try {
                                String settingName = getSettingName((Setting) next2);
                                final JsonElement value3 = asJsonObject.get(settingName);
                                if (value3 == null) {
                                    continue;
                                }
                                this.setValueFromJson((Setting) next2, value3, next);
                            } catch (Exception e) {
                                System.err.println("[ConfigManager] Error loading setting for module " + moduleName + ": " + e.getMessage());
                            }
                        }
                        modulesLoaded++;
                    }
                } catch (Exception e) {
                    System.err.println("[ConfigManager] Error loading module: " + e.getMessage());
                }
            }


        } catch (final Exception ex) {
            System.err.println("[ConfigManager] Error loading profile: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private String getModuleName(Module module) {
        try {
            CharSequence name = module.getName();
            if (name instanceof EncryptedString) {
                return ((EncryptedString) name).toString();
            } else {
                return name.toString();
            }
        } catch (Exception e) {
            // Fallback to a simple name if EncryptedString fails
            return "Module_" + module.hashCode();
        }
    }

    private String getSettingName(Setting setting) {
        try {
            CharSequence name = setting.getName();
            if (name instanceof EncryptedString) {
                return ((EncryptedString) name).toString();
            } else {
                return name.toString();
            }
        } catch (Exception e) {
            // Fallback to a simple name if EncryptedString fails
            return "Setting_" + setting.hashCode();
        }
    }

    private void setValueFromJson(final Setting setting, final JsonElement jsonElement, final Module module) {
        try {
            if (setting instanceof final BooleanSetting booleanSetting) {
                if (jsonElement.isJsonPrimitive()) {
                    booleanSetting.setValue(jsonElement.getAsBoolean());
                }
            } else if (setting instanceof final ModeSetting enumSetting) {
                if (jsonElement.isJsonPrimitive()) {
                    final int asInt = jsonElement.getAsInt();
                    if (asInt != -1) {
                        enumSetting.setModeIndex(asInt);
                    } else {
                        enumSetting.setModeIndex(enumSetting.getOriginalValue());
                    }
                }
            } else if (setting instanceof final NumberSetting numberSetting) {
                if (jsonElement.isJsonPrimitive()) {
                    numberSetting.getValue(jsonElement.getAsDouble());
                }
            } else if (setting instanceof final BindSetting bindSetting) {
                if (jsonElement.isJsonPrimitive()) {
                    final int asInt2 = jsonElement.getAsInt();
                    bindSetting.setValue(asInt2);
                    if (bindSetting.isModuleKey()) {
                        module.setKeybind(asInt2);
                    }
                }
            } else if (setting instanceof final StringSetting stringSetting) {
                if (jsonElement.isJsonPrimitive()) {
                    stringSetting.setValue(jsonElement.getAsString());
                }
            } else if (setting instanceof final MinMaxSetting minMaxSetting) {
                if (jsonElement.isJsonObject()) {
                    final JsonObject asJsonObject = jsonElement.getAsJsonObject();
                    if (asJsonObject.has("min") && asJsonObject.has("max")) {
                        final double asDouble = asJsonObject.get("min").getAsDouble();
                        final double asDouble2 = asJsonObject.get("max").getAsDouble();
                        minMaxSetting.setCurrentMin(asDouble);
                        minMaxSetting.setCurrentMax(asDouble2);
                    }
                }
            } else if (setting instanceof ItemSetting && jsonElement.isJsonPrimitive()) {
                ((ItemSetting) setting).setItem(Registries.ITEM.get(Identifier.of(jsonElement.getAsString())));
            } else if (setting instanceof MacroSetting && jsonElement.isJsonArray()) {
                final MacroSetting macroSetting = (MacroSetting) setting;
                macroSetting.clearCommands();
                for (JsonElement element : jsonElement.getAsJsonArray()) {
                    if (element.isJsonPrimitive()) {
                        macroSetting.addCommand(element.getAsString());
                    }
                }
            }
        } catch (final Exception ex) {
            System.err.println("[ConfigManager] Error setting value from JSON: " + ex.getMessage());
        }
    }

    private void saveConfigToFile() {
        try {
            // Create parent directory if it doesn't exist
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(this.jsonObject, writer);
            }


        } catch (IOException e) {
            System.err.println("[ConfigManager] Error saving config file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveConfig() {
        shutdown();
    }

    public void manualSave() {
        shutdown();
    }

    public void reloadConfig() {
        loadConfigFromFile();
        loadProfile();
    }

    public File getConfigFile() {
        return configFile;
    }

    public void shutdown() {
        try {

            this.jsonObject = new JsonObject();
            int modulesSaved = 0;

            for (final Module module : Gamble.INSTANCE.getModuleManager().c()) {
                try {
                    String moduleName = getModuleName(module);
                    final JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("enabled", module.isEnabled());

                    for (Setting setting : module.getSettings()) {
                        try {
                            this.save(setting, jsonObject, module);
                        } catch (Exception e) {
                            System.err.println("[ConfigManager] Error saving setting for module " + moduleName + ": " + e.getMessage());
                        }
                    }

                    this.jsonObject.add(moduleName, jsonObject);
                    modulesSaved++;
                } catch (Exception e) {
                    System.err.println("[ConfigManager] Error saving module: " + e.getMessage());
                }
            }

            // Save config to file
            saveConfigToFile();
        } catch (final Exception _t) {
            System.err.println("[ConfigManager] Error during shutdown: " + _t.getMessage());
            _t.printStackTrace(System.err);
        }
    }

    private void save(final Setting setting, final JsonObject jsonObject, final Module module) {
        try {
            String settingName = getSettingName(setting);

            if (setting instanceof final BooleanSetting booleanSetting) {
                jsonObject.addProperty(settingName, booleanSetting.getValue());
            } else if (setting instanceof final ModeSetting<?> enumSetting) {
                jsonObject.addProperty(settingName, enumSetting.getModeIndex());
            } else if (setting instanceof final NumberSetting numberSetting) {
                jsonObject.addProperty(settingName, numberSetting.getValue());
            } else if (setting instanceof final BindSetting bindSetting) {
                jsonObject.addProperty(settingName, bindSetting.getValue());
            } else if (setting instanceof final StringSetting stringSetting) {
                jsonObject.addProperty(settingName, stringSetting.getValue());
            } else if (setting instanceof MinMaxSetting) {
                final JsonObject jsonObject2 = new JsonObject();
                jsonObject2.addProperty("min", ((MinMaxSetting) setting).getCurrentMin());
                jsonObject2.addProperty("max", ((MinMaxSetting) setting).getCurrentMax());
                jsonObject.add(settingName, jsonObject2);
            } else if (setting instanceof final ItemSetting itemSetting) {
                jsonObject.addProperty(settingName, Registries.ITEM.getId(itemSetting.getItem()).toString());
            } else if (setting instanceof final MacroSetting macroSetting) {
                final JsonArray commandsArray = new JsonArray();
                for (String command : macroSetting.getCommands()) {
                    commandsArray.add(command);
                }
                jsonObject.add(settingName, commandsArray);
            }
        } catch (final Exception ex) {
            String settingName = "Unknown";
            try {
                settingName = getSettingName(setting);
            } catch (Exception e) {
                settingName = "Setting_" + setting.hashCode();
            }
            System.err.println("[ConfigManager] Error saving setting " + settingName + ": " + ex.getMessage());
        }
    }
}
