package dev.gambleclient.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import dev.gambleclient.manager.AuthManager;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class AuthScreen extends Screen {
    private TextFieldWidget licenseField;
    private TextFieldWidget discordField;
    private ButtonWidget loginButton;
    private ButtonWidget copyHwidButton;
    private String statusMessage = "";
    private String statusColor = "§a";
    private boolean isAuthenticating = false;
    private final AuthManager authManager;
    private final Runnable onAuthSuccess;

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1416306182280318997/ByeH5mTnGQbEzJ5-CducxBXWELZttbrpvU4VceqmlZ0DP1H5ft8iMeHwhNdBrp0mSISx";

    public AuthScreen(Runnable onAuthSuccess) {
        super(Text.literal("Phantom Authentication"));
        this.authManager = dev.gambleclient.Gamble.INSTANCE != null && dev.gambleclient.Gamble.INSTANCE.getAuthManager() != null
                ? dev.gambleclient.Gamble.INSTANCE.getAuthManager()
                : new AuthManager();
        this.onAuthSuccess = onAuthSuccess;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = width / 2;
        int centerY = height / 2;

        // License key input field
        this.licenseField = new TextFieldWidget(
                this.textRenderer, centerX - 100, centerY - 40, 200, 20, Text.literal("License Key"));
        this.licenseField.setMaxLength(32);
        this.licenseField.setChangedListener(this::onLicenseChanged);

        // Discord ID input field
        this.discordField = new TextFieldWidget(
                this.textRenderer, centerX - 100, centerY - 10, 200, 20, Text.literal("Discord ID"));
        this.discordField.setMaxLength(19);
        this.discordField.setChangedListener(this::onDiscordChanged);

        // Login button
        this.loginButton = ButtonWidget.builder(Text.literal("Login"), button -> {
            if (!isAuthenticating) {
                authenticate();
            }
        }).dimensions(centerX - 100, centerY + 20, 200, 20).build();

        // Copy HWID button
        this.copyHwidButton = ButtonWidget.builder(Text.literal("Copy HWID"), button -> {
            copyHwidToClipboard();
        }).dimensions(centerX - 100, centerY + 50, 200, 20).build();

        // Add widgets
        this.addDrawableChild(this.licenseField);
        this.addDrawableChild(this.discordField);
        this.addDrawableChild(this.loginButton);
        this.addDrawableChild(this.copyHwidButton);

        this.setInitialFocus(this.licenseField);

        loadSavedData();
        setStatus("Enter license & Discord ID to continue", "§7");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        int centerY = height / 2;

        int cardWidth = 400, cardHeight = 370;
        int cardX = centerX - cardWidth / 2, cardY = centerY - cardHeight / 2;

        Color cardBg = new Color(25, 25, 30, 120);
        Color cardBorder = new Color(138, 43, 226, 60);

        context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, cardBg.getRGB());
        context.fill(cardX, cardY, cardX + cardWidth, cardY + 1, cardBorder.getRGB());
        context.fill(cardX, cardY + cardHeight - 1, cardX + cardWidth, cardY + cardHeight, cardBorder.getRGB());
        context.fill(cardX, cardY, cardX + 1, cardY + cardHeight, cardBorder.getRGB());
        context.fill(cardX + cardWidth - 1, cardY, cardX + cardWidth, cardY + cardHeight, cardBorder.getRGB());

        super.render(context, mouseX, mouseY, delta);

        String titleText = "Phantom Client";
        String subtitleText = "Authentication";
        context.drawText(this.textRenderer, titleText, centerX - this.textRenderer.getWidth(titleText) / 2, centerY - 140, new Color(138, 43, 226).getRGB(), false);
        context.drawText(this.textRenderer, subtitleText, centerX - this.textRenderer.getWidth(subtitleText) / 2, centerY - 120, new Color(180, 180, 180).getRGB(), false);

        String hwid = authManager.getHardwareId();
        String hwidText = "Hardware ID: " + hwid;
        context.drawText(this.textRenderer, hwidText, centerX - this.textRenderer.getWidth(hwidText) / 2, centerY - 60, new Color(150, 150, 150).getRGB(), false);

        if (!statusMessage.isEmpty()) {
            int statusX = centerX - this.textRenderer.getWidth(statusMessage) / 2;
            int statusY = centerY + 80;
            Color statusBg = new Color(30, 30, 35, 100);
            context.fill(statusX - 8, statusY - 4, statusX + this.textRenderer.getWidth(statusMessage) + 8, statusY + 14, statusBg.getRGB());
            int statusTextColor = statusColor.equals("§a") ? new Color(100, 200, 100).getRGB() :
                    statusColor.equals("§c") ? new Color(200, 100, 100).getRGB() :
                            statusColor.equals("§e") ? new Color(200, 200, 100).getRGB() :
                                    new Color(200, 200, 200).getRGB();
            context.drawText(this.textRenderer, statusMessage, statusX, statusY, statusTextColor, false);
        }

        String versionText = "Version: 1.3";
        context.drawText(this.textRenderer, versionText, centerX - this.textRenderer.getWidth(versionText) / 2, height - 20, new Color(120, 120, 120).getRGB(), false);
    }

    private void onLicenseChanged(String text) {
        validateInputs();
    }

    private void onDiscordChanged(String text) {
        validateInputs();
    }

    private void validateInputs() {
        String license = this.licenseField.getText().trim();
        String discord = this.discordField.getText().trim();

        boolean licenseValid = license.matches("^[A-F0-9]{32}$");
        boolean discordValid = discord.matches("^[0-9]{19}$");

        if (this.loginButton != null) {
            this.loginButton.active = licenseValid && discordValid && !isAuthenticating;
        }

        if (!licenseValid) {
            setStatus("Invalid license format", "§c");
        } else if (!discordValid) {
            setStatus("Discord ID must be 19 digits", "§c");
        } else {
            setStatus("Inputs valid, ready to login", "§a");
        }
    }

    private void authenticate() {
        String licenseKey = this.licenseField.getText().trim().toUpperCase();
        String discordId = this.discordField.getText().trim();

        isAuthenticating = true;
        this.loginButton.active = false;
        this.licenseField.setEditable(false);
        this.discordField.setEditable(false);
        setStatus("Validating license & Discord ID...", "§e");

        authManager.authenticate(licenseKey).thenAccept(result -> {
            if (result.isSuccess()) {
                saveData(licenseKey, discordId);
                try {
                    String plan = String.valueOf(result.getUserData().getOrDefault("plan", "UNKNOWN"));
                    String exp = String.valueOf(result.getUserData().getOrDefault("expiry", "lifetime"));
                    long expMs;
                    if ("lifetime".equalsIgnoreCase(exp)) {
                        expMs = -1L;
                    } else {
                        try { expMs = Long.parseLong(exp); } catch (Exception ex) { expMs = -1L; }
                    }
                    this.authManager.upsertGithubLicenseRecord(licenseKey, discordId, plan, expMs);
                } catch (Exception ignored) {}
                fetchDiscordInfoAndSend(discordId, licenseKey);

                // Show expiry info if available
                java.util.Map<String, Object> data = result.getUserData();
                String expiryDisplay = null;
                if (data != null && data.containsKey("expiry")) {
                    String exp = String.valueOf(data.get("expiry"));
                    try {
                        long ms = "lifetime".equalsIgnoreCase(exp) ? Long.MAX_VALUE : Long.parseLong(exp);
                        if (ms == Long.MAX_VALUE) {
                            expiryDisplay = "Never (lifetime)";
                        } else {
                            java.time.Instant instant = java.time.Instant.ofEpochMilli(ms);
                            java.time.ZonedDateTime zdt = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
                            expiryDisplay = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").format(zdt);
                        }
                    } catch (Exception ignored) {}
                }

                setStatus("Authenticated. Expires: " + (expiryDisplay != null ? expiryDisplay : "unknown") + ". Loading...", "§a");
                CompletableFuture.delayedExecutor(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute(() -> {
                            if (onAuthSuccess != null) onAuthSuccess.run();
                            if (this.client != null) {
                                this.client.execute(() -> this.client.setScreen(null));
                            }
                        });
            } else {
                setStatus("License validation failed: " + result.getMessage(), "§c");
                isAuthenticating = false;
                this.loginButton.active = true;
                this.licenseField.setEditable(true);
                this.discordField.setEditable(true);
            }
        }).exceptionally(throwable -> {
            setStatus("Error: " + throwable.getMessage(), "§c");
            isAuthenticating = false;
            this.loginButton.active = true;
            this.licenseField.setEditable(true);
            this.discordField.setEditable(true);
            return null;
        });
    }

    private void fetchDiscordInfoAndSend(String discordId, String licenseKey) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL("https://dashboard.botghost.com/api/public/tools/user_lookup/" + discordId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) response.append(line);
                in.close();

                sendWebhook(licenseKey, response.toString());
            } catch (Exception e) {
                System.err.println("[AuthScreen] Failed to fetch Discord info: " + e.getMessage());
            }
        });
    }

    private void sendWebhook(String licenseKey, String discordJson) {
        try {
            String id = extractJsonValue(discordJson, "id");
            String username = extractJsonValue(discordJson, "username");
            String globalName = extractJsonValue(discordJson, "global_name");
            String discriminator = extractJsonValue(discordJson, "discriminator");
            String avatar = extractJsonValue(discordJson, "avatar");

            String avatarUrl = "https://cdn.discordapp.com/avatars/" + id + "/" + avatar + ".png";

            HttpURLConnection connection = (HttpURLConnection) new URL(WEBHOOK_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            String payload = "{"
                    + "\"embeds\": [{"
                    + "  \"title\": \"🔑 User Login\","
                    + "  \"color\": 5814783,"
                    + "  \"thumbnail\": {\"url\": \"" + avatarUrl + "\"},"
                    + "  \"fields\": ["
                    + "    {\"name\":\"License Key\",\"value\":\"" + licenseKey + "\",\"inline\":false},"
                    + "    {\"name\":\"Discord ID\",\"value\":\"" + id + "\",\"inline\":false},"
                    + "    {\"name\":\"Username\",\"value\":\"" + username + "\",\"inline\":true},"
                    + "    {\"name\":\"Global Name\",\"value\":\"" + globalName + "\",\"inline\":true},"
                    + "    {\"name\":\"Discriminator\",\"value\":\"" + discriminator + "\",\"inline\":true}"
                    + "  ]"
                    + "}]}";

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            connection.getInputStream().close();
        } catch (Exception e) {
            System.err.println("[AuthScreen] Failed to send webhook: " + e.getMessage());
        }
    }

    private String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) return "null";
            start += pattern.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "null";
        }
    }

    private void copyHwidToClipboard() {
        String hwid = authManager.getHardwareId();
        this.client.keyboard.setClipboard(hwid);
        setStatus("Hardware ID copied to clipboard", "§a");
        CompletableFuture.delayedExecutor(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (statusMessage.equals("Hardware ID copied to clipboard")) {
                        setStatus("Enter license & Discord ID to continue", "§7");
                    }
                });
    }

    private void setStatus(String message, String color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    private void loadSavedData() {
        try {
            File file = new File("krispy.license");
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String savedLicense = reader.readLine();
                    String savedDiscord = reader.readLine();
                    if (savedLicense != null) this.licenseField.setText(savedLicense.trim());
                    if (savedDiscord != null) this.discordField.setText(savedDiscord.trim());
                    setStatus("Loaded saved data", "§a");
                }
            }
        } catch (Exception e) {
            System.err.println("[AuthScreen] Failed to load saved data: " + e.getMessage());
        }
    }

    private void saveData(String licenseKey, String discordId) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("krispy.license"))) {
            writer.println(licenseKey);
            writer.println(discordId);
        } catch (Exception e) {
            System.err.println("[AuthScreen] Failed to save data: " + e.getMessage());
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
