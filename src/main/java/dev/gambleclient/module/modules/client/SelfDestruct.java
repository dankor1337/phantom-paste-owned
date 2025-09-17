package dev.gambleclient.module.modules.client;

import com.sun.jna.Memory;
import dev.gambleclient.Gamble;
import dev.gambleclient.gui.ClickGUI;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.Setting;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.StringSetting;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public final class SelfDestruct extends Module {
    public static boolean isActive = false;
    public static boolean hasSelfDestructed = false;
    private final BooleanSetting replaceMod = new BooleanSetting(EncryptedString.of("Replace Mod"), true).setDescription(EncryptedString.of("Replaces the mod with the specified JAR file"));
    private final BooleanSetting saveLastModified = new BooleanSetting(EncryptedString.of("Save Last Modified"), true).setDescription(EncryptedString.of("Saves the last modified date after self destruct"));
    private final StringSetting replaceUrl = new StringSetting(EncryptedString.of("Replace URL"), "https://cdn.modrinth.com/data/8shC1gFX/versions/sXO3idkS/BetterF3-11.0.1-Fabric-1.21.jar");


    public SelfDestruct() {
        super(EncryptedString.of("Self Destruct"), EncryptedString.of("Removes the client from your game |Credits to Argon for deletion|"), -1, Category.CLIENT);
        addSettings(this.replaceMod, this.saveLastModified, this.replaceUrl);
    }

    @Override
    public void onEnable() {
        isActive = true;
        hasSelfDestructed = true;

        // Small delay to ensure everything is properly initialized
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // Ignore
        }

        dev.gambleclient.Gamble.INSTANCE.getModuleManager().getModuleByClass(Phantom.class).toggle(false);
        this.toggle(false);
        dev.gambleclient.Gamble.INSTANCE.getConfigManager().shutdown();
        if (this.mc.currentScreen instanceof ClickGUI) {
            dev.gambleclient.Gamble.INSTANCE.shouldPreventClose = false;
            this.mc.currentScreen.close();
        }

        // Replace mod file if enabled
        if (this.replaceMod.getValue()) {
            try {
                String downloadUrl = this.replaceUrl.getValue();
                File currentJar = Utils.getCurrentJarPath();
                if (currentJar.exists() && currentJar.isFile()) {
                    replaceModFile(currentJar, downloadUrl);
                }
            } catch (Exception e) {
                // Silent error handling
            }
        }

        for (Module module : dev.gambleclient.Gamble.INSTANCE.getModuleManager().c()) {
            module.toggle(false);
            module.setName(null);
            module.setDescription(null);
            for (Setting setting : module.getSettings()) {
                setting.getDescription(null);
                setting.setDescription(null);
                if (!(setting instanceof StringSetting)) continue;
                ((StringSetting) setting).setValue(null);
            }
            module.getSettings().clear();
        }
        Runtime runtime = Runtime.getRuntime();
        if (this.saveLastModified.getValue()) {
            dev.gambleclient.Gamble.INSTANCE.resetModifiedDate();
        }
        for (int i = 0; i <= 10; ++i) {
            runtime.gc();
            try {
                Thread.sleep(100 * i);
                Memory.purge();
                Memory.disposeAll();
                continue;
            }
            catch (InterruptedException interruptedException) {}
        }

        // Self-destruct completed - send message in chat
        if (this.mc.player != null) {
            this.mc.player.sendMessage(net.minecraft.text.Text.literal("§c§l[SelfDestruct] §rClient has been cleared. Game will continue running."));
        }
    }

    private void replaceModFile(File targetFile, String downloadUrl) {
        try {
            // Create HTTP connection
            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);

            // Download the file
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(targetFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
            }

            connection.disconnect();

        } catch (Exception e) {
            // Silent error handling
        }
    }


}
