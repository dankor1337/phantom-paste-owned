package dev.gambleclient.utils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class SecurityUtils {
    
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Generate a secure AES key
     */
    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AES_KEY_SIZE, secureRandom);
        return keyGen.generateKey();
    }
    
    /**
     * Generate RSA key pair
     */
    public static KeyPair generateRSAKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, secureRandom);
        return keyGen.generateKeyPair();
    }
    
    /**
     * Encrypt data with AES-GCM
     */
    public static byte[] encryptAES(byte[] data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        
        byte[] encrypted = cipher.doFinal(data);
        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        
        return result;
    }
    
    /**
     * Decrypt data with AES-GCM
     */
    public static byte[] decryptAES(byte[] encryptedData, SecretKey key) throws Exception {
        if (encryptedData.length < GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Encrypted data too short");
        }
        
        byte[] iv = Arrays.copyOfRange(encryptedData, 0, GCM_IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(encryptedData, GCM_IV_LENGTH, encryptedData.length);
        
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
        
        return cipher.doFinal(encrypted);
    }
    
    /**
     * Encrypt string with AES
     */
    public static String encryptString(String data, SecretKey key) throws Exception {
        byte[] encrypted = encryptAES(data.getBytes(StandardCharsets.UTF_8), key);
        return Base64.getEncoder().encodeToString(encrypted);
    }
    
    /**
     * Decrypt string with AES
     */
    public static String decryptString(String encryptedData, SecretKey key) throws Exception {
        byte[] encrypted = Base64.getDecoder().decode(encryptedData);
        byte[] decrypted = decryptAES(encrypted, key);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    /**
     * Encrypt data with RSA
     */
    public static byte[] encryptRSA(byte[] data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }
    
    /**
     * Decrypt data with RSA
     */
    public static byte[] decryptRSA(byte[] encryptedData, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedData);
    }
    
    /**
     * Create digital signature
     */
    public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }
    
    /**
     * Verify digital signature
     */
    public static boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }
    
    /**
     * Generate secure random string
     */
    public static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * Generate secure random hex string
     */
    public static String generateRandomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02x", secureRandom.nextInt(256)));
        }
        return sb.toString().toUpperCase();
    }
    
    /**
     * Hash data with SHA-256
     */
    public static String hashSHA256(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }
    
    /**
     * Hash data with SHA-512
     */
    public static String hashSHA512(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }
    
    /**
     * Convert bytes to hex string
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Convert hex string to bytes
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
    
    /**
     * Save key to file
     */
    public static void saveKey(Key key, String filename) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            fos.write(key.getEncoded());
        }
    }
    
    /**
     * Load AES key from file
     */
    public static SecretKey loadAESKey(String filename) throws Exception {
        try (FileInputStream fis = new FileInputStream(filename)) {
            byte[] keyBytes = fis.readAllBytes();
            return new SecretKeySpec(keyBytes, "AES");
        }
    }
    
    /**
     * Load RSA private key from file
     */
    public static PrivateKey loadRSAPrivateKey(String filename) throws Exception {
        try (FileInputStream fis = new FileInputStream(filename)) {
            byte[] keyBytes = fis.readAllBytes();
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePrivate(spec);
        }
    }
    
    /**
     * Load RSA public key from file
     */
    public static PublicKey loadRSAPublicKey(String filename) throws Exception {
        try (FileInputStream fis = new FileInputStream(filename)) {
            byte[] keyBytes = fis.readAllBytes();
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePublic(spec);
        }
    }
    
    /**
     * Obfuscate string using XOR with random key
     */
    public static String obfuscateString(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        byte[] key = new byte[16];
        secureRandom.nextBytes(key);
        
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[inputBytes.length + 16];
        
        // Copy key to beginning
        System.arraycopy(key, 0, output, 0, 16);
        
        // XOR encrypt the data
        for (int i = 0; i < inputBytes.length; i++) {
            output[i + 16] = (byte) (inputBytes[i] ^ key[i % key.length]);
        }
        
        return Base64.getEncoder().encodeToString(output);
    }
    
    /**
     * Deobfuscate string
     */
    public static String deobfuscateString(String obfuscated) {
        if (obfuscated == null || obfuscated.isEmpty()) {
            return obfuscated;
        }
        
        try {
            byte[] data = Base64.getDecoder().decode(obfuscated);
            if (data.length < 16) {
                return obfuscated; // Invalid data
            }
            
            byte[] key = Arrays.copyOfRange(data, 0, 16);
            byte[] encrypted = Arrays.copyOfRange(data, 16, data.length);
            byte[] decrypted = new byte[encrypted.length];
            
            // XOR decrypt the data
            for (int i = 0; i < encrypted.length; i++) {
                decrypted[i] = (byte) (encrypted[i] ^ key[i % key.length]);
            }
            
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return obfuscated; // Return original if decryption fails
        }
    }
    
    /**
     * Generate hardware fingerprint
     */
    public static String generateHardwareFingerprint() {
        try {
            StringBuilder fingerprint = new StringBuilder();
            
            // System properties
            fingerprint.append(System.getProperty("os.name"));
            fingerprint.append(System.getProperty("os.version"));
            fingerprint.append(System.getProperty("os.arch"));
            fingerprint.append(System.getProperty("user.name"));
            fingerprint.append(System.getProperty("user.home"));
            
            // CPU info
            fingerprint.append(Runtime.getRuntime().availableProcessors());
            
            // Memory info
            fingerprint.append(Runtime.getRuntime().maxMemory());
            
            // Network interfaces
            java.net.NetworkInterface.getNetworkInterfaces().asIterator().forEachRemaining(networkInterface -> {
                try {
                    if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                        byte[] mac = networkInterface.getHardwareAddress();
                        if (mac != null) {
                            for (byte b : mac) {
                                fingerprint.append(String.format("%02X", b));
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });
            
            // Create hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprint.toString().getBytes(StandardCharsets.UTF_8));
            
            return bytesToHex(hash).substring(0, 32).toUpperCase();
            
        } catch (Exception e) {
            // Fallback
            return "HWID_" + System.getProperty("user.name") + "_" + 
                   System.getProperty("os.name").replaceAll("\\s+", "") + "_" +
                   System.currentTimeMillis();
        }
    }
    
    /**
     * Validate license key format
     */
    public static boolean isValidLicenseFormat(String license) {
        if (license == null || license.length() != 32) {
            return false;
        }
        return license.matches("^[A-F0-9]{32}$");
    }
    
    /**
     * Generate license key
     */
    public static String generateLicenseKey() {
        return generateRandomHex(32);
    }
    
    /**
     * Create checksum for data integrity
     */
    public static String createChecksum(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return Base64.getEncoder().encodeToString(hash);
    }
    
    /**
     * Verify checksum
     */
    public static boolean verifyChecksum(byte[] data, String expectedChecksum) throws Exception {
        String actualChecksum = createChecksum(data);
        return actualChecksum.equals(expectedChecksum);
    }
    
    /**
     * Secure string comparison (constant time)
     */
    public static boolean secureStringEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }
    
    /**
     * Clear sensitive data from memory
     */
    public static void clearSensitiveData(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
    
    /**
     * Clear sensitive data from string
     */
    public static void clearSensitiveData(String data) {
        if (data != null) {
            // Note: Strings are immutable in Java, so we can't actually clear them
            // This is more of a reminder to handle sensitive data carefully
            data = null;
        }
    }
}
