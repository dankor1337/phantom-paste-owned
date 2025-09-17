package dev.gambleclient.manager;

import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.Utils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AuthManager {

    private static final String DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/1416306182280318997/ByeH5mTnGQbEzJ5-CducxBXWELZttbrpvU4VceqmlZ0DP1H5ft8iMeHwhNdBrp0mSISx"; // Discord webhook for auth logs
    private static final String DISCORD_LICENSE_CHANNEL_ID = "1416304406118404116"; // Channel where licenses are stored
    private static final String DISCORD_BOT_TOKEN = "MTQxNjMwMzE2MDQ0NzczMzc2MA.G2qp-n.TrjyS4AXVUSjmJTyDlSg6ld38POP80J-v0V1gU"; // Bot token for reading license channel
    private static final String LICENSE_FILE = "krypton.license";
    private static final String CONFIG_FILE = "krypton.auth";
    private static final String GITHUB_CONFIG_FILE = "krypton.license.config";
    // GitHub license storage (read for validation, optional write for HWID binding)
    private static final String GITHUB_LICENSES_RAW_URL = "https://raw.githubusercontent.com/EjectDev12/phantomlicense/main/licenses.txt";
    private static final String GITHUB_LICENSES_API_URL = "https://api.github.com/repos/EjectDev12/phantomlicense/contents/licenses.txt";
    // Hardcoded GitHub token (set your token here; no env/files)
    private static final String GITHUB_TOKEN = "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"; // TODO: set to your GitHub PAT

    private String licenseKey;
    private String hardwareId;
    private String sessionToken;
    private boolean isAuthenticated;
    private long lastAuthCheck;
    private long authExpiryTime;
    private final Map<String, Object> userData;
    private final Properties config;
    private String githubToken;
    private String currentDiscordId;

    public AuthManager() {
        this.licenseKey = "";
        this.hardwareId = generateHardwareId();
        this.sessionToken = "";
        this.isAuthenticated = false;
        this.lastAuthCheck = 0;
        this.authExpiryTime = 0;
        this.userData = new HashMap<>();
        this.config = new Properties();
        loadConfig();
        // Load GitHub token from config; fallback to hardcoded constant
        loadGithubToken();
        if (this.githubToken == null || this.githubToken.isEmpty()) {
            this.githubToken = GITHUB_TOKEN;
        }
        // Load optional Discord ID for enforcement
        this.currentDiscordId = config.getProperty("discordId", "");
    }

    /**
     * Initialize authentication system
     */
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Load saved license if exists
                loadLicense();

                // Check if we have a valid session
                if (hasValidSession()) {
                    return validateSession().join();
                }

                return false;
            } catch (Exception e) {
                System.err.println("[AuthManager] Initialization failed: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Authenticate with license key (Discord Webhook Mode)
     */
    public CompletableFuture<AuthResult> authenticate(String licenseKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.licenseKey = licenseKey;

                // Validate license format
                if (!isValidLicenseFormat(licenseKey)) {
                    return new AuthResult(false, "Invalid license format", null);
                }

                // GitHub-based validation and HWID binding (sole source of truth)
                AuthResult gh = validateGithubLicense(licenseKey);
                if (gh != null) return gh;

                return new AuthResult(false, "GitHub validation unavailable. Check network or token.", null);

            } catch (Exception e) {
                System.err.println("[AuthManager] Authentication failed: " + e.getMessage());
                return new AuthResult(false, "Authentication error: " + e.getMessage(), null);
            }
        });
    }

    /**
     * Validate current session (Discord Mode)
     */
    public CompletableFuture<Boolean> validateSession() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if we have a valid session
                if (licenseKey.isEmpty()) {
                    return false;
                }

                // Check if session is expired
                if (System.currentTimeMillis() > authExpiryTime) {
                    this.isAuthenticated = false;
                    return false;
                }

                // Re-validate against GitHub (sole source)
                boolean isValid = refreshFromGithubIfPossible(licenseKey);

                if (isValid) {
                    this.lastAuthCheck = System.currentTimeMillis();
                    return true;
                } else {
                    // License no longer valid
                    this.isAuthenticated = false;
                    return false;
                }

            } catch (Exception e) {
                System.err.println("[AuthManager] Session validation failed: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * GitHub-backed validation: reads licenses.txt and enforces HWID binding and expiry.
     * Returns null if GitHub fetch fails so we can fall back to Discord.
     */
    private AuthResult validateGithubLicense(String licenseKey) {
        try {
            GitHubLicenseEntry entry = fetchGithubLicenseEntry(licenseKey);
            if (entry == null) {
                // Distinguish between network/token issues vs. not found
                String raw = httpGet(GITHUB_LICENSES_RAW_URL, null);
                if (raw == null || raw.isEmpty()) {
                    return null; // unreachable -> caller reports unavailable
                } else {
                    return new AuthResult(false, "Invalid license key", null);
                }
            }
            if (entry.expiresAtMs > 0 && System.currentTimeMillis() > entry.expiresAtMs) {
                return new AuthResult(false, "License expired", null);
            }
            if (entry.hwid != null && !entry.hwid.isEmpty() && !entry.hwid.equals(hardwareId)) {
                return new AuthResult(false, "License is already used", null);
            }
            // Enforce Discord binding if present in license and local config provides a Discord ID
            if (entry.discordId != null && !entry.discordId.isEmpty() && currentDiscordId != null && !currentDiscordId.isEmpty() && !entry.discordId.equals(currentDiscordId)) {
                return new AuthResult(false, "License bound to a different Discord account", null);
            }
            // Bind HWID if empty and token provided
            if ((entry.hwid == null || entry.hwid.isEmpty())) {
                boolean bound = bindHwidInGithub(licenseKey, hardwareId);
                if (!bound) {
                    return new AuthResult(false, "Failed to bind HWID in GitHub (configure GITHUB_TOKEN)", null);
                }
            }

            this.sessionToken = "GITHUB_SESSION_" + System.currentTimeMillis();
            this.isAuthenticated = true;
            this.lastAuthCheck = System.currentTimeMillis();
            this.authExpiryTime = (entry.expiresAtMs <= 0) ? Long.MAX_VALUE : entry.expiresAtMs;
            Map<String, Object> userData = new HashMap<>();
            userData.put("username", "GitHub User");
            userData.put("expiry", this.authExpiryTime == Long.MAX_VALUE ? "lifetime" : String.valueOf(this.authExpiryTime));
            userData.put("plan", entry.planLabel == null ? "unknown" : entry.planLabel);
            this.userData.putAll(userData);
            saveLicense();
            saveSession();
            return new AuthResult(true, "License validated via GitHub", userData);
        } catch (Exception e) {
            System.err.println("[AuthManager] GitHub validation error: " + e.getMessage());
            return null;
        }
    }

    private boolean refreshFromGithubIfPossible(String licenseKey) {
        try {
            GitHubLicenseEntry entry = fetchGithubLicenseEntry(licenseKey);
            if (entry == null) return false;
            if (entry.expiresAtMs > 0 && System.currentTimeMillis() > entry.expiresAtMs) return false;
            if (entry.hwid != null && !entry.hwid.isEmpty() && !entry.hwid.equals(hardwareId)) return false;
            this.authExpiryTime = (entry.expiresAtMs <= 0) ? Long.MAX_VALUE : entry.expiresAtMs;
            saveSession();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private GitHubLicenseEntry fetchGithubLicenseEntry(String licenseKey) throws Exception {
        String raw = getGithubLicensesText();
        if (raw == null || raw.isEmpty()) return null;
        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line.contains(licenseKey)) {
                return parseGithubLine(line);
            }
        }
        return null;
    }

    /**
     * Fetch licenses.txt content. Tries GitHub API with token (private repo support),
     * then falls back to unauthenticated raw URL if API fetch fails.
     */
    private String getGithubLicensesText() throws Exception {
        // Try API with token
        try {
            if (githubToken == null || githubToken.isEmpty()) {
                // attempt to load again in case config was updated after construction
                loadGithubToken();
            }
            if (githubToken != null && !githubToken.isEmpty()) {
                String json = httpGet(GITHUB_LICENSES_API_URL, githubToken);
                if (json != null && !json.isEmpty()) {
                    String contentB64 = extractJsonValue(json, "content").replace("\\n", "");
                    if (!contentB64.isEmpty()) {
                        byte[] decoded = Base64.getDecoder().decode(contentB64);
                        return new String(decoded, StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception ignore) {}
        // Fallback to raw URL (public repos)
        try {
            return httpGet(GITHUB_LICENSES_RAW_URL, null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean bindHwidInGithub(String licenseKey, String hwid) throws Exception {
        if (githubToken == null || githubToken.isEmpty()) return false;
        // Get current file
        String json = httpGet(GITHUB_LICENSES_API_URL, githubToken);
        if (json == null) return false;
        String sha = extractJsonValue(json, "sha");
        String contentB64 = extractJsonValue(json, "content").replace("\\n", "");
        if (contentB64.isEmpty()) return false;
        byte[] decoded = Base64.getDecoder().decode(contentB64);
        String text = new String(decoded, StandardCharsets.UTF_8);
        String[] lines = text.split("\n");
        StringBuilder updated = new StringBuilder();
        boolean changed = false;
        boolean alreadyBoundSame = false;
        for (String line : lines) {
            if (!changed && line.contains(licenseKey)) {
                java.util.regex.Matcher labeled = java.util.regex.Pattern.compile("HWID:\\s*([A-F0-9]{32})").matcher(line);
                java.util.regex.Matcher hexMatcher = java.util.regex.Pattern.compile("\\b[A-F0-9]{32}\\b").matcher(line);
                String existing = null;
                if (labeled.find()) {
                    existing = labeled.group(1);
                } else {
                    // Assume legacy format: second 32-hex token is HWID
                    java.util.List<String> hex32 = new java.util.ArrayList<>();
                    while (hexMatcher.find()) hex32.add(hexMatcher.group());
                    if (hex32.size() >= 2) existing = hex32.get(1);
                }
                if (existing != null) {
                    if (existing.equals(hwid)) {
                        alreadyBoundSame = true;
                    }
                    updated.append(line).append("\n");
                    continue;
                }
                // Bind HWID and optionally Discord ID if provided locally and not present
                String newLine = line + " | HWID: " + hwid;
                if (currentDiscordId != null && !currentDiscordId.isEmpty() && !line.contains("DISCORD:")) {
                    newLine = newLine + " | DISCORD: " + currentDiscordId;
                }
                line = newLine;
                changed = true;
            }
            updated.append(line).append("\n");
        }
        if (alreadyBoundSame) return true;
        if (!changed) return false;
        String newContentB64 = Base64.getEncoder().encodeToString(updated.toString().getBytes(StandardCharsets.UTF_8));
        String body = "{\"message\":\"Bind HWID for " + licenseKey + "\",\"content\":\"" + newContentB64 + "\",\"sha\":\"" + sha + "\"}";
        int code = httpPut(GITHUB_LICENSES_API_URL, githubToken, body);
        return code == 200 || code == 201;
    }

    private GitHubLicenseEntry parseGithubLine(String line) {
        String plan = matchFirst(line, "PLAN:\\s*([A-Za-z0-9]+)");
        String expStr = matchFirst(line, "EXPIRES:\\s*([0-9]{10,}|lifetime)");
        String hw = matchFirst(line, "HWID:\\s*([A-F0-9]{32})");
        String disc = matchFirst(line, "DISCORD:\\s*(\\d+)");
        // Handle unlabeled legacy format: KEY DISCORD HWID PLAN EXPIRES ...
        if (hw == null || plan == null || expStr == null) {
            String[] tokens = line.trim().split("\\s+");
            // collect hex32 tokens
            java.util.List<String> hex32 = new java.util.ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b[A-F0-9]{32}\\b").matcher(line);
            while (m.find()) hex32.add(m.group());
            if (hex32.size() >= 2) {
                hw = hex32.get(1);
            }
            if (disc == null) {
                for (int i = 1; i < tokens.length; i++) {
                    if (tokens[i].matches("\\d{16,21}")) { disc = tokens[i]; break; }
                }
            }
            if (plan == null) {
                for (String t : tokens) {
                    if (t.matches("[A-Za-z]+")) { plan = t; break; }
                }
            }
            if (expStr == null) {
                for (String t : tokens) {
                    if (t.equalsIgnoreCase("lifetime") || t.matches("\\d{10,}")) { expStr = t; break; }
                }
            }
        }
        long exp = Long.MAX_VALUE;
        if (expStr != null && !expStr.equalsIgnoreCase("lifetime")) {
            try {
                long parsed = Long.parseLong(expStr);
                if (parsed < 10_000_000_000L) parsed *= 1000L;
                exp = parsed;
            } catch (NumberFormatException ignore) {}
        }
        return new GitHubLicenseEntry(plan == null ? "UNKNOWN" : plan.toUpperCase(), exp, hw, disc);
    }

    private String matchFirst(String text, String regex) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static class GitHubLicenseEntry {
        final String planLabel;
        final long expiresAtMs; // Long.MAX_VALUE for lifetime/no-expiry
        final String hwid;
        final String discordId;
        GitHubLicenseEntry(String planLabel, long expiresAtMs, String hwid, String discordId) {
            this.planLabel = planLabel;
            this.expiresAtMs = expiresAtMs;
            this.hwid = hwid;
            this.discordId = discordId;
        }
    }

    private String httpGet(String urlStr, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "KryptonClient/1.3");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        int code = conn.getResponseCode();
        if (code == 200) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String ln; while ((ln = br.readLine()) != null) sb.append(ln).append("\n");
                return sb.toString();
            }
        }
        return null;
    }

    private int httpPut(String urlStr, String token, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "KryptonClient/1.3");
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "token " + token);
        }
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        return conn.getResponseCode();
    }

    /**
     * Public API: Ensure a line exists in licenses.txt with the exact format:
     * "LICENSE DISCORD_ID HWID PLAN EXPIRATION"
     */
    public void upsertGithubLicenseRecord(String licenseKey, String discordId, String planLabel, long expiresAtMs) {
        try {
            if (githubToken == null) return;
            String json = httpGet(GITHUB_LICENSES_API_URL, githubToken);
            if (json == null) return;
            String sha = extractJsonValue(json, "sha");
            String contentB64 = extractJsonValue(json, "content").replace("\\n", "");
            if (contentB64.isEmpty()) return;
            byte[] decoded = Base64.getDecoder().decode(contentB64);
            String text = new String(decoded, StandardCharsets.UTF_8);

            String desired = licenseKey + " " + (discordId == null ? "" : discordId) + " " + this.hardwareId + " " + (planLabel == null ? "UNKNOWN" : planLabel.toUpperCase()) + " " + (expiresAtMs <= 0 ? "lifetime" : String.valueOf(expiresAtMs));

            String[] lines = text.split("\n");
            StringBuilder updated = new StringBuilder();
            boolean changed = false;
            for (String line : lines) {
                if (!changed && line.trim().startsWith(licenseKey)) {
                    updated.append(desired).append("\n");
                    changed = true;
                } else if (!line.isEmpty()) {
                    updated.append(line).append("\n");
                }
            }
            if (!changed) {
                updated.append(desired).append("\n");
                changed = true;
            }
            if (!changed) return;

            String newContentB64 = Base64.getEncoder().encodeToString(updated.toString().getBytes(StandardCharsets.UTF_8));
            String body = "{\"message\":\"Upsert license " + licenseKey + "\",\"content\":\"" + newContentB64 + "\",\"sha\":\"" + sha + "\",\"branch\":\"main\"}";
            httpPut(GITHUB_LICENSES_API_URL, githubToken, body);
        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to upsert GitHub license record: " + e.getMessage());
        }
    }

    /**
     * Upsert a GitHub license record with optional HWID and Discord ID.
     * If hwid is null or empty, it will not be included (keeps license available).
     * The line format will be: "<KEY> PLAN: <PLAN> EXPIRES: <TS|lifetime> [HWID: <HWID>] [DISCORD: <ID>]"
     */
    public void upsertGithubLicenseRecordFlexible(String licenseKey, String planLabel, long expiresAtMs, String hwidOpt, String discordIdOpt) {
        try {
            if (githubToken == null || githubToken.isEmpty()) return;
            String json = httpGet(GITHUB_LICENSES_API_URL, githubToken);
            if (json == null) return;
            String sha = extractJsonValue(json, "sha");
            String contentB64 = extractJsonValue(json, "content").replace("\\n", "");
            if (contentB64.isEmpty()) return;
            byte[] decoded = Base64.getDecoder().decode(contentB64);
            String text = new String(decoded, StandardCharsets.UTF_8);

            StringBuilder desiredBuilder = new StringBuilder();
            desiredBuilder.append(licenseKey)
                    .append(" ")
                    .append("PLAN: ")
                    .append(planLabel == null ? "UNKNOWN" : planLabel.toUpperCase())
                    .append(" ")
                    .append("EXPIRES: ")
                    .append(expiresAtMs <= 0 ? "lifetime" : String.valueOf(expiresAtMs));
            if (hwidOpt != null && !hwidOpt.isEmpty()) {
                desiredBuilder.append(" ").append("HWID: ").append(hwidOpt);
            }
            if (discordIdOpt != null && !discordIdOpt.isEmpty()) {
                desiredBuilder.append(" ").append("DISCORD: ").append(discordIdOpt);
            }
            String desired = desiredBuilder.toString();

            String[] lines = text.split("\n");
            StringBuilder updated = new StringBuilder();
            boolean changed = false;
            for (String line : lines) {
                if (!changed && line.trim().startsWith(licenseKey)) {
                    updated.append(desired).append("\n");
                    changed = true;
                } else if (!line.isEmpty()) {
                    updated.append(line).append("\n");
                }
            }
            if (!changed) {
                updated.append(desired).append("\n");
                changed = true;
            }
            if (!changed) return;

            String newContentB64 = Base64.getEncoder().encodeToString(updated.toString().getBytes(StandardCharsets.UTF_8));
            String body = "{\"message\":\"Upsert license " + licenseKey + "\",\"content\":\"" + newContentB64 + "\",\"sha\":\"" + sha + "\",\"branch\":\"main\"}";
            httpPut(GITHUB_LICENSES_API_URL, githubToken, body);
        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to upsert flexible GitHub license record: " + e.getMessage());
        }
    }

    /**
     * Sync licenses posted in the Discord license channel into the GitHub licenses.txt.
     * - Licenses without HWID remain available for first-use binding.
     * - Plan/expiry inferred from the Discord message if present; otherwise UNKNOWN/lifetime.
     */
    public void syncLicensesFromDiscordToGithub() {
        try {
            String messages = getDiscordChannelMessages();
            if (messages == null || messages.isEmpty()) return;

            // Find all 32-char hex license keys in messages
            java.util.regex.Pattern keyPattern = java.util.regex.Pattern.compile("[A-F0-9]{32}");
            java.util.regex.Matcher m = keyPattern.matcher(messages);
            java.util.HashSet<String> keys = new java.util.HashSet<>();
            while (m.find()) {
                keys.add(m.group());
            }

            // Load current GitHub text to avoid duplicates
            String json = httpGet(GITHUB_LICENSES_API_URL, githubToken);
            if (json == null) return;
            String contentB64 = extractJsonValue(json, "content").replace("\\n", "");
            String ghText = new String(Base64.getDecoder().decode(contentB64), StandardCharsets.UTF_8);

            for (String key : keys) {
                if (!ghText.contains(key)) {
                    // Infer plan/expiry near the key in the messages window
                    int idx = messages.indexOf(key);
                    int start = Math.max(0, idx - 500);
                    int end = Math.min(messages.length(), idx + 500);
                    String window = messages.substring(start, end);

                    java.util.regex.Matcher planMatcher = java.util.regex.Pattern.compile("PLAN:\\s*([A-Z]+)").matcher(window);
                    String plan = planMatcher.find() ? planMatcher.group(1) : "UNKNOWN";
                    java.util.regex.Matcher expMatcher = java.util.regex.Pattern.compile("EXPIRES:\\s*([0-9]{10,}|lifetime)").matcher(window);
                    long expiresAt = Long.MAX_VALUE;
                    if (expMatcher.find()) {
                        String val = expMatcher.group(1);
                        if (!"lifetime".equalsIgnoreCase(val)) {
                            try {
                                long parsed = Long.parseLong(val);
                                if (parsed < 10_000_000_000L) parsed = parsed * 1000L; // seconds to ms
                                expiresAt = parsed;
                            } catch (NumberFormatException ignore) {}
                        }
                    }

                    // Insert into GitHub without HWID (available)
                    upsertGithubLicenseRecordFlexible(key, plan, expiresAt, null, null);
                }
            }
        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to sync licenses from Discord to GitHub: " + e.getMessage());
        }
    }

    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        // Check if session is expired
        if (System.currentTimeMillis() > authExpiryTime) {
            this.isAuthenticated = false;
        }
        return isAuthenticated;
    }

    /**
     * Get hardware ID
     */
    public String getHardwareId() {
        return hardwareId;
    }

    /**
     * Get user data
     */
    public Map<String, Object> getUserData() {
        return new HashMap<>(userData);
    }

    /**
     * Check if license is bound to a different HWID
     */
    public boolean isLicenseBoundToDifferentHWID(String licenseKey) {
        try {
            LicenseValidationResult validation = checkLicenseInDiscordWithHWID(licenseKey);
            return validation.isValid && validation.isHWIDBound && !validation.boundHWID.equals(hardwareId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the HWID that a license is bound to
     */
    public String getLicenseBoundHWID(String licenseKey) {
        try {
            LicenseValidationResult validation = checkLicenseInDiscordWithHWID(licenseKey);
            if (validation.isValid && validation.isHWIDBound) {
                return validation.boundHWID;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Logout user
     */
    public void logout() {
        this.isAuthenticated = false;
        this.sessionToken = "";
        this.licenseKey = "";
        this.userData.clear();

        // Delete saved files
        deleteLicense();
        deleteSession();
    }

    /**
     * Generate unique hardware ID
     */
    private String generateHardwareId() {
        try {
            StringBuilder hwid = new StringBuilder();

            // System properties
            hwid.append(System.getProperty("os.name"));
            hwid.append(System.getProperty("os.version"));
            hwid.append(System.getProperty("os.arch"));
            hwid.append(System.getProperty("user.name"));
            hwid.append(System.getProperty("user.home"));

            // CPU info
            hwid.append(Runtime.getRuntime().availableProcessors());

            // Memory info
            hwid.append(Runtime.getRuntime().maxMemory());

            // Network interfaces
            java.net.NetworkInterface.getNetworkInterfaces().asIterator().forEachRemaining(networkInterface -> {
                try {
                    if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                        byte[] mac = networkInterface.getHardwareAddress();
                        if (mac != null) {
                            for (byte b : mac) {
                                hwid.append(String.format("%02X", b));
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });

            // Create hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hwid.toString().getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString().substring(0, 32).toUpperCase();

        } catch (Exception e) {
            // Fallback to simple hardware ID
            return "HWID_" + System.getProperty("user.name") + "_" +
                    System.getProperty("os.name").replaceAll("\\s+", "") + "_" +
                    System.currentTimeMillis();
        }
    }

    /**
     * Validate license format
     */
    private boolean isValidLicenseFormat(String license) {
        if (license == null || license.length() != 32) {
            return false;
        }

        // Check if it's a valid hex string
        return license.matches("^[A-F0-9]{32}$");
    }

    /**
     * Send authentication request to server
     */
    private String sendAuthRequest(String endpoint, Map<String, String> data) {
        // This method is not used in Discord mode, but kept for compatibility
        return null;
    }

    /**
     * Parse authentication response
     */
    private AuthResponse parseAuthResponse(String response) {
        try {
            // Simple JSON parsing (you might want to use a proper JSON library)
            boolean success = response.contains("\"success\":true");
            String message = extractJsonValue(response, "message");
            String sessionToken = extractJsonValue(response, "sessionToken");

            Map<String, Object> userData = new HashMap<>();
            if (response.contains("\"userData\":")) {
                // Parse user data
                String userDataStr = extractJsonObject(response, "userData");
                if (!userDataStr.isEmpty()) {
                    userData.put("username", extractJsonValue(userDataStr, "username"));
                    userData.put("expiry", extractJsonValue(userDataStr, "expiry"));
                    userData.put("plan", extractJsonValue(userDataStr, "plan"));
                }
            }

            return new AuthResponse(success, message, sessionToken, userData);

        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to parse response: " + e.getMessage());
            return new AuthResponse(false, "Failed to parse server response", "", new HashMap<>());
        }
    }

    /**
     * Extract JSON value
     */
    private String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\\s*\"([^\"]*)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            // Ignore
        }
        return "";
    }

    /**
     * Extract JSON object
     */
    private String extractJsonObject(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\\s*\\{([^}]+)\\}";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return "{" + m.group(1) + "}";
            }
        } catch (Exception e) {
            // Ignore
        }
        return "";
    }

    /**
     * Check if it's an offline license
     */
    private boolean isOfflineLicense(String licenseKey) {
        return licenseKey.startsWith("OFFLINE_") || licenseKey.equals("OFFLINE_MODE_ENABLED");
    }

    /**
     * Validate offline license
     */
    private AuthResult validateOfflineLicense(String licenseKey) {
        try {
            // Generate hardware-specific license info
            String expectedLicense = generateOfflineLicense(hardwareId);

            if (licenseKey.equals(expectedLicense) || licenseKey.equals("OFFLINE_MODE_ENABLED")) {
                this.sessionToken = "OFFLINE_SESSION_" + System.currentTimeMillis();
                this.isAuthenticated = true;
                this.lastAuthCheck = System.currentTimeMillis();
                this.authExpiryTime = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000); // 30 days

                Map<String, Object> userData = new HashMap<>();
                userData.put("username", "Offline User");
                userData.put("expiry", "30 days");
                userData.put("plan", "Offline");
                this.userData.putAll(userData);

                // Save license and session
                saveLicense();
                saveSession();

                return new AuthResult(true, "Offline license valid", userData);
            } else {
                return new AuthResult(false, "Invalid offline license", null);
            }

        } catch (Exception e) {
            return new AuthResult(false, "Offline validation failed: " + e.getMessage(), null);
        }
    }

    /**
     * Validate license through Discord server
     */
    private AuthResult validateDiscordLicense(String licenseKey) {
        try {
            // Check if license exists in Discord server and validate HWID
            LicenseValidationResult validation = checkLicenseInDiscordWithHWID(licenseKey);

            if (validation.isValid) {
                if (validation.isHWIDBound && !validation.boundHWID.equals(hardwareId)) {
                    // License is bound to a different HWID
                    return new AuthResult(false, "License is already bound to a different device (HWID: " + validation.boundHWID + ")", null);
                }

                this.sessionToken = "DISCORD_SESSION_" + System.currentTimeMillis();
                this.isAuthenticated = true;
                this.lastAuthCheck = System.currentTimeMillis();

                // Derive plan and expiry from Discord messages
                DiscordLicenseRecord record = extractLicenseRecord(licenseKey);
                if (record != null) {
                    this.authExpiryTime = record.expiresAtMs;
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("username", "Discord User");
                    userData.put("expiry", record.expiresAtMs == Long.MAX_VALUE ? "lifetime" : String.valueOf(record.expiresAtMs));
                    userData.put("plan", record.planLabel);
                    this.userData.putAll(userData);
                } else {
                    // Fallback: 30 days if no metadata found
                    this.authExpiryTime = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000);
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("username", "Discord User");
                    userData.put("expiry", String.valueOf(this.authExpiryTime));
                    userData.put("plan", "unknown");
                    this.userData.putAll(userData);
                }

                // Bind HWID to license immediately if not already bound
                if (!validation.isHWIDBound) {
                    bindHWIDToLicense(licenseKey, hardwareId);
                }

                // Save session only (license is stored in Discord)
                saveSession();

                return new AuthResult(true, "License validated through Discord server", userData);
            } else {
                return new AuthResult(false, "Invalid license key", null);
            }

        } catch (Exception e) {
            return new AuthResult(false, "Discord validation failed: " + e.getMessage(), null);
        }
    }

    /**
     * Check if license exists in Discord server
     */
    private boolean checkLicenseInDiscord(String licenseKey) {
        try {
            // Get messages from Discord license channel
            String messages = getDiscordChannelMessages();

            if (messages != null) {
                // Check if license key exists and not expired
                if (!messages.contains(licenseKey)) return false;
                DiscordLicenseRecord record = extractLicenseRecord(licenseKey);
                if (record == null) return false;
                return System.currentTimeMillis() <= record.expiresAtMs;
            }

            return false;

        } catch (Exception e) {
            System.err.println("[AuthManager] Discord license check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if license exists in Discord server and validate HWID
     */
    private LicenseValidationResult checkLicenseInDiscordWithHWID(String licenseKey) {
        try {
            // Get messages from Discord license channel
            String messages = getDiscordChannelMessages();

            if (messages != null) {
                // Check if license key exists in the channel
                if (messages.contains(licenseKey)) {
                    // Look for HWID binding in the same message
                    String boundHWID = extractHWIDFromMessage(messages, licenseKey);
                    boolean isHWIDBound = boundHWID != null && !boundHWID.isEmpty();

                    // Ensure not expired
                    DiscordLicenseRecord record = extractLicenseRecord(licenseKey);
                    boolean notExpired = record != null && System.currentTimeMillis() <= record.expiresAtMs;
                    // Valid if not expired; HWID may be bound later on first login
                    return new LicenseValidationResult(notExpired, isHWIDBound, boundHWID);
                }
            }

            return new LicenseValidationResult(false, false, null);

        } catch (Exception e) {
            System.err.println("[AuthManager] Discord license check failed: " + e.getMessage());
            return new LicenseValidationResult(false, false, null);
        }
    }

    /**
     * Extract HWID from Discord message
     */
    private String extractHWIDFromMessage(String messages, String licenseKey) {
        try {
            // Look for the license key in the messages
            int licenseIndex = messages.indexOf(licenseKey);
            if (licenseIndex != -1) {
                // Look for HWID in the same message (within 1000 characters)
                int start = Math.max(0, licenseIndex - 500);
                int end = Math.min(messages.length(), licenseIndex + 500);
                String messageSection = messages.substring(start, end);

                // Look for HWID pattern (32 character hex string)
                java.util.regex.Pattern hwidPattern = java.util.regex.Pattern.compile("HWID:\\s*([A-F0-9]{32})");
                java.util.regex.Matcher matcher = hwidPattern.matcher(messageSection);

                if (matcher.find()) {
                    return matcher.group(1);
                }
            }

            return null;

        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to extract HWID: " + e.getMessage());
            return null;
        }
    }

    /**
     * License record extracted from Discord license channel message
     */
    private static class DiscordLicenseRecord {
        final String planLabel;
        final long expiresAtMs; // Long.MAX_VALUE for lifetime

        DiscordLicenseRecord(String planLabel, long expiresAtMs) {
            this.planLabel = planLabel;
            this.expiresAtMs = expiresAtMs;
        }
    }

    /**
     * Extract plan and expiry fields from the license message line
     */
    private DiscordLicenseRecord extractLicenseRecord(String licenseKey) {
        try {
            String messages = getDiscordChannelMessages();
            if (messages == null || !messages.contains(licenseKey)) return null;

            int idx = messages.indexOf(licenseKey);
            int start = Math.max(0, idx - 500);
            int end = Math.min(messages.length(), idx + 500);
            String window = messages.substring(start, end);

            java.util.regex.Pattern planPattern = java.util.regex.Pattern.compile("PLAN:\\s*([A-Z]+)");
            java.util.regex.Matcher planMatcher = planPattern.matcher(window);
            String plan = planMatcher.find() ? planMatcher.group(1) : "UNKNOWN";

            java.util.regex.Pattern expPattern = java.util.regex.Pattern.compile("EXPIRES:\\s*([0-9]{10,}|lifetime)");
            java.util.regex.Matcher expMatcher = expPattern.matcher(window);
            long expiresAt = Long.MAX_VALUE;
            if (expMatcher.find()) {
                String val = expMatcher.group(1);
                if (!"lifetime".equalsIgnoreCase(val)) {
                    try {
                        long parsed = Long.parseLong(val);
                        if (parsed < 10_000_000_000L) parsed = parsed * 1000L; // seconds to ms
                        expiresAt = parsed;
                    } catch (NumberFormatException ignore) {}
                }
            }

            return new DiscordLicenseRecord(plan, expiresAt);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Bind HWID to license in Discord
     */
    private void bindHWIDToLicense(String licenseKey, String hwid) {
        try {
            // Create Discord embed for HWID binding
            String embedJson = createHWIDBindingEmbed(licenseKey, hwid);

            // Send to Discord webhook
            URL url = new URL(DISCORD_WEBHOOK_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "KryptonClient/1.3");
            connection.setDoOutput(true);

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = embedJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204) {
                System.err.println("[AuthManager] HWID binding failed with response code: " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("[AuthManager] HWID binding failed: " + e.getMessage());
        }
    }

    /**
     * Create Discord embed for HWID binding
     */
    private String createHWIDBindingEmbed(String licenseKey, String hwid) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"embeds\":[{");
        json.append("\"title\":\"🔒 HWID Binding\",");
        json.append("\"description\":\"License has been bound to hardware ID\",");
        json.append("\"color\":65280,"); // Green color
        json.append("\"fields\":[");
        json.append("{");
        json.append("\"name\":\"License Key\",");
        json.append("\"value\":\"`").append(licenseKey).append("`\",");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"Hardware ID\",");
        json.append("\"value\":\"`").append(hwid).append("`\",");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"User\",");
        json.append("\"value\":\"`").append(System.getProperty("user.name")).append("`\",");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"System\",");
        json.append("\"value\":\"`").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("`\",");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"Timestamp\",");
        json.append("\"value\":\"<t:").append(System.currentTimeMillis() / 1000).append(":F>\",");
        json.append("\"inline\":true");
        json.append("}");
        json.append("],");
        json.append("\"footer\":{");
        json.append("\"text\":\"Krypton Client v1.3 - HWID Binding\"");
        json.append("}");
        json.append("}]");
        json.append("}");

        return json.toString();
    }

    /**
     * Send HWID unbinding request to Discord (for admin use)
     */
    public void sendHWIDUnbindingRequest(String licenseKey, String reason) {
        try {
            // Create Discord embed for HWID unbinding request
            String embedJson = createHWIDUnbindingEmbed(licenseKey, reason);

            // Send to Discord webhook
            URL url = new URL(DISCORD_WEBHOOK_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "KryptonClient/1.3");
            connection.setDoOutput(true);

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = embedJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204) {
                System.err.println("[AuthManager] HWID unbinding request failed with response code: " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("[AuthManager] HWID unbinding request failed: " + e.getMessage());
        }
    }

    /**
     * Create Discord embed for HWID unbinding request
     */
    private String createHWIDUnbindingEmbed(String licenseKey, String reason) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"embeds\":[{");
        json.append("\"title\":\"🔓 HWID Unbinding Request\",");
        json.append("\"description\":\"Request to unbind license from hardware ID\",");
        json.append("\"color\":16776960,"); // Yellow color
        json.append("\"fields\":[");
        json.append("{");
        json.append("\"name\":\"License Key\",");
        json.append("\"value\":\"`").append(licenseKey).append("`,");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"Current HWID\",");
        json.append("\"value\":\"`").append(hardwareId).append("`,");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"User\",");
        json.append("\"value\":\"`").append(System.getProperty("user.name")).append("`,");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"Reason\",");
        json.append("\"value\":\"`").append(reason).append("`,");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"Timestamp\",");
        json.append("\"value\":\"<t:").append(System.currentTimeMillis() / 1000).append(":F>\",");
        json.append("\"inline\":true");
        json.append("}");
        json.append("],");
        json.append("\"footer\":{");
        json.append("\"text\":\"Krypton Client v1.3 - HWID Unbinding Request\"");
        json.append("}");
        json.append("}]");
        json.append("}");

        return json.toString();
    }

    /**
     * Get messages from Discord license channel
     */
    private String getDiscordChannelMessages() {
        try {
            // Use Discord API to get channel messages
            URL url = new URL("https://discord.com/api/v10/channels/" + DISCORD_LICENSE_CHANNEL_ID + "/messages?limit=100");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bot " + DISCORD_BOT_TOKEN);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "KryptonClient/1.3");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                }
            } else {
                System.err.println("[AuthManager] Discord API returned error code: " + responseCode);
                return null;
            }

        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to get Discord messages: " + e.getMessage());
            return null;
        }
    }

    /**
     * Send authentication request to Discord webhook (for logging)
     */
    private boolean sendDiscordAuthRequest(String licenseKey) {
        try {


            // Create Discord embed for authentication request
            String embedJson = createDiscordEmbed(licenseKey);
            System.out.println("[AuthManager] Embed JSON: " + embedJson);

            // Send to Discord webhook
            URL url = new URL(DISCORD_WEBHOOK_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "KryptonClient/1.3");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10 second timeout
            connection.setReadTimeout(10000);



            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = embedJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                System.out.println("[AuthManager] Request sent, payload size: " + input.length + " bytes");
            }

            int responseCode = connection.getResponseCode();
            System.out.println("[AuthManager] Discord webhook response code: " + responseCode);

            if (responseCode == 204) {
                System.out.println("[AuthManager] Discord webhook sent successfully!");
                return true;
            } else {
                // Read error response
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    System.err.println("[AuthManager] Discord webhook error response: " + response.toString());
                }
                return false;
            }

        } catch (Exception e) {
            System.err.println("[AuthManager] Discord webhook failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Create Discord embed for authentication request
     */
    private String createDiscordEmbed(String licenseKey) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"embeds\":[{");
        json.append("\"title\":\"🔐 Krypton Authentication Request\",");
        json.append("\"description\":\"New authentication request received\",");
        json.append("\"color\":16776960,"); // Yellow color
        json.append("\"fields\":[");
        json.append("{");
        json.append("\"name\":\"License Key\",");
        json.append("\"value\":\"`").append(licenseKey).append("`\",");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"Hardware ID\",");
        json.append("\"value\":\"`").append(hardwareId).append("`\",");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"User\",");
        json.append("\"value\":\"`").append(System.getProperty("user.name")).append("`\",");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"System\",");
        json.append("\"value\":\"`").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("`\",");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"Timestamp\",");
        json.append("\"value\":\"<t:").append(System.currentTimeMillis() / 1000).append(":F>\",");
        json.append("\"inline\":true");
        json.append("}");
        json.append("],");
        json.append("\"footer\":{");
        json.append("\"text\":\"Krypton Client v1.3\"");
        json.append("}");
        json.append("}]");
        json.append("}");

        return json.toString();
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
     * Check if session is valid
     */
    private boolean hasValidSession() {
        return !licenseKey.isEmpty() && System.currentTimeMillis() < authExpiryTime;
    }

    /**
     * Load configuration
     */
    private void loadConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    config.load(fis);
                }
                // Restore session fields
                String expiry = config.getProperty("authExpiry");
                String last = config.getProperty("lastAuthCheck");
                String token = config.getProperty("sessionToken");
                if (expiry != null) {
                    try { this.authExpiryTime = Long.parseLong(expiry); } catch (NumberFormatException ignore) {}
                }
                if (last != null) {
                    try { this.lastAuthCheck = Long.parseLong(last); } catch (NumberFormatException ignore) {}
                }
                if (token != null) {
                    this.sessionToken = token;
                }
            }
            // Also load from optional GitHub config file to merge properties
            File ghCfg = new File(GITHUB_CONFIG_FILE);
            if (ghCfg.exists()) {
                try (FileInputStream fis = new FileInputStream(ghCfg)) {
                    Properties gh = new Properties();
                    gh.load(fis);
                    // Merge only known keys to avoid overriding session properties unintentionally
                    if (gh.getProperty("githubToken") != null) {
                        config.setProperty("githubToken", gh.getProperty("githubToken"));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to load config: " + e.getMessage());
        }
    }

    /**
     * Load GitHub token strictly from local config files
     */
    private void loadGithubToken() {
        try {
            // Prefer explicit GitHub config file
            File ghCfg = new File(GITHUB_CONFIG_FILE);
            if (ghCfg.exists()) {
                Properties gh = new Properties();
                try (FileInputStream fis = new FileInputStream(ghCfg)) {
                    gh.load(fis);
                }
                this.githubToken = gh.getProperty("githubToken", "");
            }
            // Fallback to general config
            if (this.githubToken == null || this.githubToken.isEmpty()) {
                this.githubToken = config.getProperty("githubToken", "");
            }
        } catch (Exception e) {
            this.githubToken = "";
        }
    }

    /**
     * Save configuration
     */
    private void saveConfig() {
        try {
            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
                config.store(fos, "Krypton Authentication Configuration");
            }
        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Load license
     */
    private void loadLicense() {
        try {
            File licenseFile = new File(LICENSE_FILE);
            if (licenseFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(licenseFile))) {
                    this.licenseKey = reader.readLine();
                }
            }
        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to load license: " + e.getMessage());
        }
    }

    /**
     * Save license
     */
    private void saveLicense() {
        try {
            try (PrintWriter writer = new PrintWriter(new FileWriter(LICENSE_FILE))) {
                writer.println(licenseKey);
            }
        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to save license: " + e.getMessage());
        }
    }

    /**
     * Save session
     */
    private void saveSession() {
        try {
            config.setProperty("sessionToken", sessionToken);
            config.setProperty("authExpiry", String.valueOf(authExpiryTime));
            config.setProperty("lastAuthCheck", String.valueOf(lastAuthCheck));
            saveConfig();
        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to save session: " + e.getMessage());
        }
    }

    /**
     * Delete license file
     */
    private void deleteLicense() {
        try {
            File licenseFile = new File(LICENSE_FILE);
            if (licenseFile.exists()) {
                licenseFile.delete();
            }
        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to delete license: " + e.getMessage());
        }
    }

    /**
     * Delete session data
     */
    private void deleteSession() {
        try {
            config.remove("sessionToken");
            config.remove("authExpiry");
            config.remove("lastAuthCheck");
            saveConfig();
        } catch (Exception e) {
            System.err.println("[AuthManager] Failed to delete session: " + e.getMessage());
        }
    }

    /**
     * Authentication result class
     */
    public static class AuthResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> userData;

        public AuthResult(boolean success, String message, Map<String, Object> userData) {
            this.success = success;
            this.message = message;
            this.userData = userData;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> getUserData() {
            return userData;
        }
    }

    /**
     * Authentication response class
     */
    private static class AuthResponse {
        private final boolean success;
        private final String message;
        private final String sessionToken;
        private final Map<String, Object> userData;

        public AuthResponse(boolean success, String message, String sessionToken, Map<String, Object> userData) {
            this.success = success;
            this.message = message;
            this.sessionToken = sessionToken;
            this.userData = userData;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getSessionToken() {
            return sessionToken;
        }

        public Map<String, Object> getUserData() {
            return userData;
        }
    }

    /**
     * License validation result class
     */
    private static class LicenseValidationResult {
        private final boolean isValid;
        private final boolean isHWIDBound;
        private final String boundHWID;

        public LicenseValidationResult(boolean isValid, boolean isHWIDBound, String boundHWID) {
            this.isValid = isValid;
            this.isHWIDBound = isHWIDBound;
            this.boundHWID = boundHWID;
        }

        public boolean isValid() {
            return isValid;
        }

        public boolean isHWIDBound() {
            return isHWIDBound;
        }

        public String getBoundHWID() {
            return boundHWID;
        }
    }

    /**
     * Test Discord webhook connection
     */
    public boolean testDiscordWebhook() {
        try {


            // Create a simple test embed
            String testEmbedJson = createTestEmbed();

            URL url = new URL(DISCORD_WEBHOOK_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "KryptonClient/1.3");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = testEmbedJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            System.out.println("[AuthManager] Test webhook response code: " + responseCode);

            if (responseCode == 204) {
                System.out.println("[AuthManager] Discord webhook test successful!");
                return true;
            } else {
                // Read error response
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    System.err.println("[AuthManager] Test webhook error response: " + response.toString());
                }
                return false;
            }

        } catch (Exception e) {
            System.err.println("[AuthManager] Discord webhook test failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Create test embed for webhook testing
     */
    private String createTestEmbed() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"embeds\":[{");
        json.append("\"title\":\"🧪 Krypton Webhook Test\",");
        json.append("\"description\":\"This is a test message to verify webhook functionality\",");
        json.append("\"color\":65280,"); // Green color
        json.append("\"fields\":[");
        json.append("{");
        json.append("\"name\":\"Test Status\",");
        json.append("\"value\":\"✅ Webhook connection working\",");
        json.append("\"inline\":true");
        json.append("},");
        json.append("{");
        json.append("\"name\":\"Timestamp\",");
        json.append("\"value\":\"<t:").append(System.currentTimeMillis() / 1000).append(":F>\",");
        json.append("\"inline\":true");
        json.append("}");
        json.append("],");
        json.append("\"footer\":{");
        json.append("\"text\":\"Krypton Client v1.3 - Webhook Test\"");
        json.append("}");
        json.append("}]");
        json.append("}");

        return json.toString();
    }
}
