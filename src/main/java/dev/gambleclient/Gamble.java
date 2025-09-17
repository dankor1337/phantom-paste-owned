//PHANTOM ARE CLOWNS
package dev.gambleclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import dev.gambleclient.gui.ClickGUI;
import dev.gambleclient.gui.AuthScreen;
import dev.gambleclient.manager.ConfigManager;
import dev.gambleclient.manager.EventManager;
import dev.gambleclient.manager.AuthManager;
import dev.gambleclient.manager.LicenseManager;
import dev.gambleclient.module.ModuleManager;

import java.io.File;

public final class Gamble {
    public ConfigManager configManager;
    public ModuleManager MODULE_MANAGER;
    public EventManager EVENT_BUS;
    public AuthManager authManager;
    public LicenseManager licenseManager;
    public static MinecraftClient mc;
    public String version;
    public static Gamble INSTANCE;
    public boolean shouldPreventClose;
    public boolean isAuthenticated;
    public ClickGUI GUI;
    public Screen screen;
    public long modified;
    public File jar;

    public Gamble() {
        try {
            Gamble.INSTANCE = this;
            this.version = " b1.3";
            this.screen = null;
            this.EVENT_BUS = new EventManager();
            this.MODULE_MANAGER = new ModuleManager();
            this.GUI = new ClickGUI();
            this.configManager = new ConfigManager();
            this.authManager = new AuthManager();
            this.licenseManager = new LicenseManager();
            this.isAuthenticated = false;
            this.getConfigManager().loadProfile();
            this.jar = new File(Gamble.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            this.modified = this.jar.lastModified();
            this.shouldPreventClose = false;
            Gamble.mc = MinecraftClient.getInstance();

            // Initialize authentication system
            //initializeAuthentication(); patched by dankor1337.

        } catch (Throwable _t) {
            _t.printStackTrace(System.err);
        }
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    public ModuleManager getModuleManager() {
        return this.MODULE_MANAGER;
    }

    public EventManager getEventBus() {
        return this.EVENT_BUS;
    }

    public void resetModifiedDate() {
        this.jar.setLastModified(this.modified);
    }

    /**
     * Initialize authentication system
     */
    private void initializeAuthentication() {
        // Check if user is already authenticated
        if (licenseManager.isLicenseValid()) {
            this.isAuthenticated = true;
            System.out.println("[Phantom] License validated successfully");
        } else {
            // Don't show auth screen here - let TitleScreenMixin handle it
            System.out.println("[Phantom] Authentication required - waiting for main menu");
        }
    }

    /**
     * Show authentication screen
     */
    public void showAuthenticationScreen() {
        if (mc != null) {
            mc.setScreen(new AuthScreen(this::onAuthenticationSuccess));
        }
    }

    /**
     * Handle successful authentication
     */
    private void onAuthenticationSuccess() {
        this.isAuthenticated = true;
        System.out.println("[Phantom] Authentication successful - Client loaded");

        // Close authentication screen on render thread
        if (mc != null && mc.currentScreen instanceof AuthScreen) {
            mc.execute(() -> {
                mc.setScreen(null);
            });
        }
    }

    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    /**
     * Get authentication manager
     */
    public AuthManager getAuthManager() {
        return authManager;
    }

    /**
     * Get license manager
     */
    public LicenseManager getLicenseManager() {
        return licenseManager;
    }

}