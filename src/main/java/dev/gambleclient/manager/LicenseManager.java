package dev.gambleclient.manager;

import dev.gambleclient.utils.SecurityUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class LicenseManager {
    
    private static final String LICENSE_CACHE_FILE = "phantom.license.cache";
    private static final String LICENSE_CONFIG_FILE = "phantom.license.config";
    private static final long CACHE_DURATION = 24 * 60 * 60 * 1000; // 24 hours
    private static final String OFFLINE_SIGNATURE = "OFFLINE_MODE_ENABLED";
    
    private final Properties config;
    private final Map<String, LicenseInfo> licenseCache;
    private String currentLicense;
    private boolean offlineMode;
    
    public LicenseManager() {
        this.config = new Properties();
        this.licenseCache = new HashMap<>();
        this.currentLicense = "";
        this.offlineMode = false;
        loadConfig();
    }
    
    /**
     * Initialize license manager
     */
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                loadLicenseCache();
                return true;
            } catch (Exception e) {
                System.err.println("[LicenseManager] Initialization failed: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Validate license key
     */
    public CompletableFuture<LicenseValidationResult> validateLicense(String licenseKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                if (licenseCache.containsKey(licenseKey)) {
                    LicenseInfo cached = licenseCache.get(licenseKey);
                    if (System.currentTimeMillis() < cached.getExpiryTime()) {
                        this.currentLicense = licenseKey;
                        return new LicenseValidationResult(true, "License valid (cached)", cached);
                    } else {
                        // Remove expired license from cache
                        licenseCache.remove(licenseKey);
                    }
                }
                
                // Validate format
                if (!SecurityUtils.isValidLicenseFormat(licenseKey)) {
                    return new LicenseValidationResult(false, "Invalid license format", null);
                }
                
                // Check if it's an offline license
                if (isOfflineLicense(licenseKey)) {
                    return validateOfflineLicense(licenseKey);
                }
                
                // Online validation would go here
                // For now, we'll simulate online validation
                return simulateOnlineValidation(licenseKey);
                
            } catch (Exception e) {
                System.err.println("[LicenseManager] License validation failed: " + e.getMessage());
                return new LicenseValidationResult(false, "Validation error: " + e.getMessage(), null);
            }
        });
    }
    
    /**
     * Check if license is valid offline
     */
    public boolean isOfflineLicense(String licenseKey) {
        return licenseKey.startsWith("OFFLINE_") || licenseKey.equals(OFFLINE_SIGNATURE);
    }
    
    /**
     * Validate offline license
     */
    private LicenseValidationResult validateOfflineLicense(String licenseKey) {
        try {
            // Generate hardware-specific license info
            String hwid = SecurityUtils.generateHardwareFingerprint();
            String expectedLicense = generateOfflineLicense(hwid);
            
            if (licenseKey.equals(expectedLicense) || licenseKey.equals(OFFLINE_SIGNATURE)) {
                LicenseInfo licenseInfo = new LicenseInfo();
                licenseInfo.setLicenseKey(licenseKey);
                licenseInfo.setUsername("Offline User");
                licenseInfo.setPlan("Offline");
                licenseInfo.setExpiryTime(System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000)); // 30 days
                licenseInfo.setHardwareId(hwid);
                licenseInfo.setOffline(true);
                
                // Cache the license
                licenseCache.put(licenseKey, licenseInfo);
                this.currentLicense = licenseKey;
                this.offlineMode = true;
                
                saveLicenseCache();
                
                return new LicenseValidationResult(true, "Offline license valid", licenseInfo);
            } else {
                return new LicenseValidationResult(false, "Invalid offline license", null);
            }
            
        } catch (Exception e) {
            return new LicenseValidationResult(false, "Offline validation failed: " + e.getMessage(), null);
        }
    }
    
    /**
     * Generate offline license for current hardware
     */
    public String generateOfflineLicense() {
        try {
            String hwid = SecurityUtils.generateHardwareFingerprint();
            return generateOfflineLicense(hwid);
        } catch (Exception e) {
            return "OFFLINE_ERROR";
        }
    }
    
    /**
     * Generate offline license for specific hardware ID
     */
    private String generateOfflineLicense(String hwid) throws Exception {
        String base = "OFFLINE_" + hwid + "_" + System.getProperty("user.name");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder license = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            license.append(String.format("%02X", hash[i]));
        }
        
        return license.toString();
    }
    
    /**
     * Simulate online license validation
     */
    private LicenseValidationResult simulateOnlineValidation(String licenseKey) {
        try {
            // This is a simulation - in a real implementation, you would
            // send the license to your authentication server
            
            // For demo purposes, accept any valid format license
            if (SecurityUtils.isValidLicenseFormat(licenseKey)) {
                LicenseInfo licenseInfo = new LicenseInfo();
                licenseInfo.setLicenseKey(licenseKey);
                licenseInfo.setUsername("Demo User");
                licenseInfo.setPlan("Premium");
                licenseInfo.setExpiryTime(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000)); // 1 year
                licenseInfo.setHardwareId(SecurityUtils.generateHardwareFingerprint());
                licenseInfo.setOffline(false);
                
                // Cache the license
                licenseCache.put(licenseKey, licenseInfo);
                this.currentLicense = licenseKey;
                
                saveLicenseCache();
                
                return new LicenseValidationResult(true, "License validated successfully", licenseInfo);
            } else {
                return new LicenseValidationResult(false, "Invalid license format", null);
            }
            
        } catch (Exception e) {
            return new LicenseValidationResult(false, "Online validation failed: " + e.getMessage(), null);
        }
    }
    
    /**
     * Check if current license is valid
     */
    public boolean isLicenseValid() {
        if (currentLicense.isEmpty()) {
            return false;
        }
        
        LicenseInfo info = licenseCache.get(currentLicense);
        if (info == null) {
            return false;
        }
        
        return System.currentTimeMillis() < info.getExpiryTime();
    }
    
    /**
     * Get current license info
     */
    public LicenseInfo getCurrentLicenseInfo() {
        if (currentLicense.isEmpty()) {
            return null;
        }
        
        return licenseCache.get(currentLicense);
    }
    
    /**
     * Get license expiry time
     */
    public long getLicenseExpiryTime() {
        LicenseInfo info = getCurrentLicenseInfo();
        return info != null ? info.getExpiryTime() : 0;
    }
    
    /**
     * Check if license is expired
     */
    public boolean isLicenseExpired() {
        return System.currentTimeMillis() > getLicenseExpiryTime();
    }
    
    /**
     * Get days until expiry
     */
    public int getDaysUntilExpiry() {
        long expiryTime = getLicenseExpiryTime();
        if (expiryTime == 0) {
            return -1;
        }
        
        long days = (expiryTime - System.currentTimeMillis()) / (24 * 60 * 60 * 1000);
        return Math.max(0, (int) days);
    }
    
    /**
     * Clear current license
     */
    public void clearLicense() {
        this.currentLicense = "";
        this.offlineMode = false;
        saveConfig();
    }
    
    /**
     * Enable offline mode
     */
    public void enableOfflineMode() {
        this.offlineMode = true;
        this.currentLicense = OFFLINE_SIGNATURE;
        saveConfig();
    }
    
    /**
     * Check if offline mode is enabled
     */
    public boolean isOfflineMode() {
        return offlineMode;
    }
    
    /**
     * Load configuration
     */
    private void loadConfig() {
        try {
            File configFile = new File(LICENSE_CONFIG_FILE);
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    config.load(fis);
                }
                
                this.currentLicense = config.getProperty("currentLicense", "");
                this.offlineMode = Boolean.parseBoolean(config.getProperty("offlineMode", "false"));
            }
        } catch (Exception e) {
            System.err.println("[LicenseManager] Failed to load config: " + e.getMessage());
        }
    }
    
    /**
     * Save configuration
     */
    private void saveConfig() {
        try {
            config.setProperty("currentLicense", currentLicense);
            config.setProperty("offlineMode", String.valueOf(offlineMode));
            
            try (FileOutputStream fos = new FileOutputStream(LICENSE_CONFIG_FILE)) {
                config.store(fos, "Krypton License Configuration");
            }
        } catch (Exception e) {
            System.err.println("[LicenseManager] Failed to save config: " + e.getMessage());
        }
    }
    
    /**
     * Load license cache
     */
    private void loadLicenseCache() {
        try {
            File cacheFile = new File(LICENSE_CACHE_FILE);
            if (cacheFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFile))) {
                    @SuppressWarnings("unchecked")
                    Map<String, LicenseInfo> cached = (Map<String, LicenseInfo>) ois.readObject();
                    
                    // Only load non-expired licenses
                    long currentTime = System.currentTimeMillis();
                    for (Map.Entry<String, LicenseInfo> entry : cached.entrySet()) {
                        if (entry.getValue().getExpiryTime() > currentTime) {
                            licenseCache.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[LicenseManager] Failed to load license cache: " + e.getMessage());
        }
    }
    
    /**
     * Save license cache
     */
    private void saveLicenseCache() {
        try {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(LICENSE_CACHE_FILE))) {
                oos.writeObject(licenseCache);
            }
        } catch (Exception e) {
            System.err.println("[LicenseManager] Failed to save license cache: " + e.getMessage());
        }
    }
    
    /**
     * Clear license cache
     */
    public void clearLicenseCache() {
        licenseCache.clear();
        try {
            File cacheFile = new File(LICENSE_CACHE_FILE);
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
        } catch (Exception e) {
            System.err.println("[LicenseManager] Failed to clear license cache: " + e.getMessage());
        }
    }
    
    /**
     * License validation result
     */
    public static class LicenseValidationResult {
        private final boolean valid;
        private final String message;
        private final LicenseInfo licenseInfo;
        
        public LicenseValidationResult(boolean valid, String message, LicenseInfo licenseInfo) {
            this.valid = valid;
            this.message = message;
            this.licenseInfo = licenseInfo;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public LicenseInfo getLicenseInfo() {
            return licenseInfo;
        }
    }
    
    /**
     * License information
     */
    public static class LicenseInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String licenseKey;
        private String username;
        private String plan;
        private long expiryTime;
        private String hardwareId;
        private boolean offline;
        
        public LicenseInfo() {
        }
        
        // Getters and setters
        public String getLicenseKey() {
            return licenseKey;
        }
        
        public void setLicenseKey(String licenseKey) {
            this.licenseKey = licenseKey;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPlan() {
            return plan;
        }
        
        public void setPlan(String plan) {
            this.plan = plan;
        }
        
        public long getExpiryTime() {
            return expiryTime;
        }
        
        public void setExpiryTime(long expiryTime) {
            this.expiryTime = expiryTime;
        }
        
        public String getHardwareId() {
            return hardwareId;
        }
        
        public void setHardwareId(String hardwareId) {
            this.hardwareId = hardwareId;
        }
        
        public boolean isOffline() {
            return offline;
        }
        
        public void setOffline(boolean offline) {
            this.offline = offline;
        }
        
        @Override
        public String toString() {
            return "LicenseInfo{" +
                    "licenseKey='" + licenseKey + '\'' +
                    ", username='" + username + '\'' +
                    ", plan='" + plan + '\'' +
                    ", expiryTime=" + expiryTime +
                    ", hardwareId='" + hardwareId + '\'' +
                    ", offline=" + offline +
                    '}';
        }
    }
}
