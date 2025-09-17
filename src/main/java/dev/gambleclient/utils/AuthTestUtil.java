package dev.gambleclient.utils;

import dev.gambleclient.manager.AuthManager;
import dev.gambleclient.manager.LicenseManager;

import java.util.concurrent.CompletableFuture;

/**
 * Utility class for testing and development of the authentication system
 */
public class AuthTestUtil {
    
    /**
     * Test the authentication system with a sample license
     */
    public static void testAuthentication() {
        System.out.println("=== Krypton Authentication Test ===");
        
        // Test hardware fingerprinting
        testHardwareFingerprinting();
        
        // Test license generation
        testLicenseGeneration();
        
        // Test authentication flow
        testAuthenticationFlow();
        
        // Test offline mode
        testOfflineMode();
        
        System.out.println("=== Test Complete ===");
    }
    
    /**
     * Test hardware fingerprinting
     */
    private static void testHardwareFingerprinting() {
        System.out.println("\n--- Testing Hardware Fingerprinting ---");
        
        try {
            String hwid1 = SecurityUtils.generateHardwareFingerprint();
            String hwid2 = SecurityUtils.generateHardwareFingerprint();
            
            System.out.println("Hardware ID 1: " + hwid1);
            System.out.println("Hardware ID 2: " + hwid2);
            System.out.println("Consistent: " + hwid1.equals(hwid2));
            
            if (hwid1.equals(hwid2)) {
                System.out.println("✓ Hardware fingerprinting is working correctly");
            } else {
                System.out.println("✗ Hardware fingerprinting is inconsistent");
            }
            
        } catch (Exception e) {
            System.err.println("✗ Hardware fingerprinting failed: " + e.getMessage());
        }
    }
    
    /**
     * Test license generation
     */
    private static void testLicenseGeneration() {
        System.out.println("\n--- Testing License Generation ---");
        
        try {
            // Generate random license
            String randomLicense = SecurityUtils.generateLicenseKey();
            System.out.println("Random License: " + randomLicense);
            System.out.println("Valid Format: " + SecurityUtils.isValidLicenseFormat(randomLicense));
            
            // Generate offline license
            String hwid = SecurityUtils.generateHardwareFingerprint();
            String offlineLicense = generateOfflineLicenseForHWID(hwid);
            System.out.println("Offline License: " + offlineLicense);
            System.out.println("Valid Format: " + SecurityUtils.isValidLicenseFormat(offlineLicense));
            
            // Test invalid formats
            String[] invalidLicenses = {
                "123", // Too short
                "A1B2C3D4E5F6789012345678901234567890", // Too long
                "A1B2C3D4E5F678901234567890123G", // Invalid character
                "a1b2c3d4e5f678901234567890123456" // Lowercase
            };
            
            for (String invalid : invalidLicenses) {
                System.out.println("Invalid License '" + invalid + "': " + SecurityUtils.isValidLicenseFormat(invalid));
            }
            
            System.out.println("✓ License generation tests completed");
            
        } catch (Exception e) {
            System.err.println("✗ License generation failed: " + e.getMessage());
        }
    }
    
    /**
     * Test authentication flow
     */
    private static void testAuthenticationFlow() {
        System.out.println("\n--- Testing Authentication Flow ---");
        
        try {
            AuthManager authManager = new AuthManager();
            LicenseManager licenseManager = new LicenseManager();
            
            // Test initialization
            CompletableFuture<Boolean> initFuture = authManager.initialize();
            initFuture.thenAccept(success -> {
                System.out.println("Auth Manager Initialization: " + (success ? "✓" : "✗"));
            });
            
            // Test with valid format license
            String testLicense = SecurityUtils.generateLicenseKey();
            CompletableFuture<AuthManager.AuthResult> authFuture = authManager.authenticate(testLicense);
            authFuture.thenAccept(result -> {
                System.out.println("Authentication Result: " + (result.isSuccess() ? "✓" : "✗"));
                System.out.println("Message: " + result.getMessage());
            });
            
            // Test license manager
            CompletableFuture<LicenseManager.LicenseValidationResult> licenseFuture = licenseManager.validateLicense(testLicense);
            licenseFuture.thenAccept(result -> {
                System.out.println("License Validation: " + (result.isValid() ? "✓" : "✗"));
                System.out.println("Message: " + result.getMessage());
            });
            
            System.out.println("✓ Authentication flow tests initiated");
            
        } catch (Exception e) {
            System.err.println("✗ Authentication flow failed: " + e.getMessage());
        }
    }
    
    /**
     * Test offline mode
     */
    private static void testOfflineMode() {
        System.out.println("\n--- Testing Offline Mode ---");
        
        try {
            LicenseManager licenseManager = new LicenseManager();
            
            // Test offline license generation
            String hwid = SecurityUtils.generateHardwareFingerprint();
            String offlineLicense = generateOfflineLicenseForHWID(hwid);
            
            System.out.println("Generated Offline License: " + offlineLicense);
            
            // Test offline license validation
            CompletableFuture<LicenseManager.LicenseValidationResult> future = licenseManager.validateLicense(offlineLicense);
            future.thenAccept(result -> {
                System.out.println("Offline License Validation: " + (result.isValid() ? "✓" : "✗"));
                if (result.isValid() && result.getLicenseInfo() != null) {
                    System.out.println("Username: " + result.getLicenseInfo().getUsername());
                    System.out.println("Plan: " + result.getLicenseInfo().getPlan());
                    System.out.println("Offline: " + result.getLicenseInfo().isOffline());
                }
            });
            
            // Test offline mode enable
            licenseManager.enableOfflineMode();
            System.out.println("Offline Mode Enabled: " + licenseManager.isOfflineMode());
            
            System.out.println("✓ Offline mode tests completed");
            
        } catch (Exception e) {
            System.err.println("✗ Offline mode failed: " + e.getMessage());
        }
    }
    
    /**
     * Generate offline license for specific hardware ID
     */
    private static String generateOfflineLicenseForHWID(String hwid) throws Exception {
        String base = "OFFLINE_" + hwid + "_" + System.getProperty("user.name");
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(base.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        
        StringBuilder license = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            license.append(String.format("%02X", hash[i]));
        }
        
        return license.toString();
    }
    
    /**
     * Generate a test license for development
     */
    public static String generateTestLicense() {
        try {
            String hwid = SecurityUtils.generateHardwareFingerprint();
            return generateOfflineLicenseForHWID(hwid);
        } catch (Exception e) {
            return SecurityUtils.generateLicenseKey();
        }
    }
    
    /**
     * Print system information for debugging
     */
    public static void printSystemInfo() {
        System.out.println("\n=== System Information ===");
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("Architecture: " + System.getProperty("os.arch"));
        System.out.println("User: " + System.getProperty("user.name"));
        System.out.println("Home: " + System.getProperty("user.home"));
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("CPU Cores: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Max Memory: " + formatBytes(Runtime.getRuntime().maxMemory()));
        
        try {
            String hwid = SecurityUtils.generateHardwareFingerprint();
            System.out.println("Hardware ID: " + hwid);
        } catch (Exception e) {
            System.err.println("Failed to generate Hardware ID: " + e.getMessage());
        }
    }
    
    /**
     * Format bytes to human readable format
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * Test encryption utilities
     */
    public static void testEncryption() {
        System.out.println("\n--- Testing Encryption ---");
        
        try {
            // Test AES encryption
            javax.crypto.SecretKey aesKey = SecurityUtils.generateAESKey();
            String originalText = "Hello, Krypton!";
            
            String encrypted = SecurityUtils.encryptString(originalText, aesKey);
            String decrypted = SecurityUtils.decryptString(encrypted, aesKey);
            
            System.out.println("Original: " + originalText);
            System.out.println("Encrypted: " + encrypted);
            System.out.println("Decrypted: " + decrypted);
            System.out.println("AES Test: " + (originalText.equals(decrypted) ? "✓" : "✗"));
            
            // Test string obfuscation
            String obfuscated = SecurityUtils.obfuscateString(originalText);
            String deobfuscated = SecurityUtils.deobfuscateString(obfuscated);
            
            System.out.println("Obfuscated: " + obfuscated);
            System.out.println("Deobfuscated: " + deobfuscated);
            System.out.println("Obfuscation Test: " + (originalText.equals(deobfuscated) ? "✓" : "✗"));
            
            // Test hashing
            String hash256 = SecurityUtils.hashSHA256(originalText);
            String hash512 = SecurityUtils.hashSHA512(originalText);
            
            System.out.println("SHA-256: " + hash256);
            System.out.println("SHA-512: " + hash512);
            System.out.println("Hashing Test: " + (hash256.length() == 64 && hash512.length() == 128 ? "✓" : "✗"));
            
            System.out.println("✓ Encryption tests completed");
            
        } catch (Exception e) {
            System.err.println("✗ Encryption tests failed: " + e.getMessage());
        }
    }
    
    /**
     * Test Discord webhook functionality
     */
    public static void testDiscordWebhook() {
        System.out.println("\n--- Testing Discord Webhook ---");
        
        try {
            AuthManager authManager = new AuthManager();
            
            // Test webhook connection
            boolean webhookSuccess = authManager.testDiscordWebhook();
            
            if (webhookSuccess) {
                System.out.println("✓ Discord webhook test successful!");
                System.out.println("✓ Auth logs should be working properly");
            } else {
                System.out.println("✗ Discord webhook test failed!");
                System.out.println("✗ Auth logs are not being sent to Discord");
                System.out.println("Possible issues:");
                System.out.println("  - Webhook URL is invalid or expired");
                System.out.println("  - Discord server is down");
                System.out.println("  - Network connectivity issues");
                System.out.println("  - Webhook permissions are incorrect");
            }
            
        } catch (Exception e) {
            System.err.println("✗ Discord webhook test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Main method for running tests
     */
    public static void main(String[] args) {
        System.out.println("Krypton Authentication Test Utility");
        System.out.println("==================================");
        
        printSystemInfo();
        testDiscordWebhook(); // Add webhook test first
        testAuthentication();
        testEncryption();
        
        System.out.println("\nTest License for Development: " + generateTestLicense());
    }
}
