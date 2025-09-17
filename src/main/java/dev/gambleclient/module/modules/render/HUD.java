package dev.gambleclient.module.modules.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import dev.gambleclient.Gamble;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.Render2DEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.Setting;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.ModeSetting;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.RenderUtils;
import dev.gambleclient.utils.TextRenderer;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public final class HUD extends Module {
    private static final CharSequence watermarkText = EncryptedString.of("Phantom");
    private static final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");

    private final BooleanSetting showWatermark = new BooleanSetting(EncryptedString.of("Watermark"), true);
    private final BooleanSetting showInfo = new BooleanSetting(EncryptedString.of("Info"), true);
    private final BooleanSetting showModules = new BooleanSetting("Modules", true);
    private final BooleanSetting showTime = new BooleanSetting("Time", true);
    private final BooleanSetting showCoordinates = new BooleanSetting("Coordinates", true);
    private final ModeSetting<HUDTheme> theme = new ModeSetting("Theme", HUDTheme.DARK, HUDTheme.class);
    private final ModeSetting<ModuleListSorting> moduleSortingMode = new ModeSetting("Sort Mode", ModuleListSorting.LENGTH, ModuleListSorting.class);
    private final BooleanSetting shadow = new BooleanSetting("Text Shadow", true);
    private final BooleanSetting rainbow = new BooleanSetting("Rainbow", false);

    public HUD() {
        super(EncryptedString.of("HUD"), EncryptedString.of("Clean centered HUD"), -1, Category.RENDER);
        addSettings(new Setting[]{showWatermark, showInfo, showModules, showTime, showCoordinates,
                theme, moduleSortingMode, shadow, rainbow});
    }

    @EventListener
    public void onRender2D(Render2DEvent event) {
        if (this.mc.currentScreen != Gamble.INSTANCE.GUI) {
            DrawContext ctx = event.context;
            int width = this.mc.getWindow().getWidth();
            int height = this.mc.getWindow().getHeight();

            RenderUtils.unscaledProjection();

            HUDTheme currentTheme = (HUDTheme) theme.getValue();

            if (showWatermark.getValue()) {
                renderCenteredWatermark(ctx, width, currentTheme);
            }

            if (showInfo.getValue() && mc.player != null) {
                renderTopLeftInfo(ctx, currentTheme);
            }

            if (showCoordinates.getValue() && mc.player != null) {
                renderBottomLeftCoords(ctx, height, currentTheme);
            }

            if (showModules.getValue()) {
                renderRightModules(ctx, width, currentTheme);
            }

            RenderUtils.scaledProjection();
        }
    }

    private void renderCenteredWatermark(DrawContext ctx, int width, HUDTheme theme) {
        String watermark = watermarkText.toString();
        String time = timeFormatter.format(new Date());
        String combinedText = watermark + " | " + time;

        int textWidth = TextRenderer.getWidth(combinedText);
        int padding = 12;
        int bgWidth = textWidth + padding * 2;
        int bgX = width / 2 - bgWidth / 2;
        int y = 15;

        // Main background
        RenderUtils.renderRoundedQuad(ctx.getMatrices(), theme.background,
                bgX, y - 6, bgX + bgWidth, y + 16, 4, 4);

        // Accent line
        RenderUtils.renderRoundedQuad(ctx.getMatrices(), getAccentColor(theme),
                bgX, y + 14, bgX + bgWidth, y + 16, 2, 2);

        // Centered text
        Color textColor = rainbow.getValue() ? getRainbowColor(0) : theme.text;
        drawCenteredText(ctx, combinedText, bgX, y - 6, bgWidth, 22, textColor);
    }

    private void renderTopLeftInfo(DrawContext ctx, HUDTheme theme) {
        int x = 15;
        int y = 15;

        String fps = "FPS " + mc.getCurrentFps();

        int textWidth = TextRenderer.getWidth(fps);
        int boxWidth = textWidth + 16;
        int boxHeight = 16;

        RenderUtils.renderRoundedQuad(ctx.getMatrices(), theme.background,
                x - 8, y - 6, x - 8 + boxWidth, y - 6 + boxHeight, 4, 4);

        Color fpsColor = rainbow.getValue() ? getRainbowColor(2) : theme.primary;
        drawCenteredText(ctx, fps, x - 8, y - 6, boxWidth, boxHeight, fpsColor);
    }

    private void renderBottomLeftCoords(DrawContext ctx, int height, HUDTheme theme) {
        if (mc.player == null) return;

        int x = 15;
        int y = height - 35;

        String coords = String.format("XYZ %.0f %.0f %.0f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());

        String dimension = getDimensionCoords();
        if (!dimension.isEmpty()) {
            coords += " " + dimension;
        }

        int textWidth = TextRenderer.getWidth(coords);
        int padding = 10;
        int boxWidth = textWidth + padding * 2;

        RenderUtils.renderRoundedQuad(ctx.getMatrices(), theme.background,
                x - padding, y - 6, x - padding + boxWidth, y + 16, 4, 4);

        Color coordColor = rainbow.getValue() ? getRainbowColor(4) : theme.accent;
        drawCenteredText(ctx, coords, x - padding, y - 6, boxWidth, 22, coordColor);
    }

    private void renderRightModules(DrawContext ctx, int width, HUDTheme theme) {
        List<Module> modules = getSortedModules();
        if (modules.isEmpty()) return;

        int y = 50;
        int rightMargin = 15;

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            String name = module.getName().toString();
            int textWidth = TextRenderer.getWidth(name);
            int padding = 8;
            int boxWidth = textWidth + padding * 2;
            int x = width - boxWidth - rightMargin;

            RenderUtils.renderRoundedQuad(ctx.getMatrices(), theme.background,
                    x, y - 4, x + boxWidth, y + 14, 3, 3);

            // Accent bar
            RenderUtils.renderRoundedQuad(ctx.getMatrices(), getModuleColor(theme, i),
                    x + boxWidth - 3, y - 4, x + boxWidth, y + 14, 0, 2);

            Color moduleColor = rainbow.getValue() ? getRainbowColor(i + 5) : theme.text;
            drawCenteredText(ctx, name, x, y - 4, boxWidth, 18, moduleColor);

            y += 22;
        }
    }

    // Centered text drawing (slightly higher vertically)
    private void drawCenteredText(DrawContext ctx, String text, int boxX, int boxY, int boxWidth, int boxHeight, Color color) {
        int textWidth = TextRenderer.getWidth(text);
        int textHeight = 10; // fallback height
        int textX = boxX + (boxWidth - textWidth) / 2;
        int textY = boxY + (boxHeight - textHeight) / 2 - 1; // move text up a little

        if (shadow.getValue()) {
            TextRenderer.drawString(text, ctx, textX + 1, textY + 1, new Color(0, 0, 0, 100).getRGB());
        }
        TextRenderer.drawString(text, ctx, textX, textY, color.getRGB());
    }

    private String getDimensionCoords() {
        if (mc.world == null) return "";

        String dimension = mc.world.getRegistryKey().getValue().getPath();
        if (dimension.contains("nether")) {
            return String.format("[%.0f %.0f]", mc.player.getX() * 8.0, mc.player.getZ() * 8.0);
        } else if (dimension.contains("overworld")) {
            return String.format("[%.0f %.0f]", mc.player.getX() / 8.0, mc.player.getZ() / 8.0);
        }
        return "";
    }

    private Color getAccentColor(HUDTheme theme) {
        return rainbow.getValue() ? getRainbowColor(10) : theme.accent;
    }

    private Color getModuleColor(HUDTheme theme, int index) {
        if (rainbow.getValue()) {
            return getRainbowColor(index + 20);
        }
        return switch (index % 3) {
            case 0 -> theme.primary;
            case 1 -> theme.secondary;
            default -> theme.accent;
        };
    }

    private Color getRainbowColor(int offset) {
        long time = System.currentTimeMillis();
        float hue = ((time + offset * 100) % 3600) / 3600.0f;
        return Color.getHSBColor(hue, 0.8f, 1.0f);
    }

    private List<Module> getSortedModules() {
        List<Module> modules = Gamble.INSTANCE.getModuleManager().b();
        ModuleListSorting sorting = (ModuleListSorting) moduleSortingMode.getValue();

        return switch (sorting) {
            case ALPHABETICAL -> modules.stream()
                    .sorted(Comparator.comparing(module -> module.getName().toString()))
                    .toList();
            case LENGTH -> modules.stream()
                    .sorted((a, b) -> Integer.compare(
                            TextRenderer.getWidth(b.getName()),
                            TextRenderer.getWidth(a.getName())))
                    .toList();
            case CATEGORY -> modules.stream()
                    .sorted(Comparator.comparing(Module::getCategory)
                            .thenComparing(module -> module.getName().toString()))
                    .toList();
        };
    }

    enum ModuleListSorting {
        LENGTH("Length"),
        ALPHABETICAL("Alphabetical"),
        CATEGORY("Category");

        private final String name;

        ModuleListSorting(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum HUDTheme {
        DARK("Dark",
                new Color(25, 25, 28, 180),
                new Color(255, 255, 255, 220),
                new Color(100, 149, 237),
                new Color(255, 107, 107),
                new Color(152, 195, 121)),

        PURPLE("Purple",
                new Color(30, 20, 40, 180),
                new Color(255, 255, 255, 220),
                new Color(147, 112, 219),
                new Color(186, 85, 211),
                new Color(138, 43, 226)),

        BLUE("Blue",
                new Color(15, 25, 35, 180),
                new Color(255, 255, 255, 220),
                new Color(64, 224, 255),
                new Color(30, 144, 255),
                new Color(0, 191, 255)),

        GREEN("Green",
                new Color(20, 30, 20, 180),
                new Color(255, 255, 255, 220),
                new Color(50, 205, 50),
                new Color(124, 252, 0),
                new Color(0, 255, 127));

        public final String name;
        public final Color background;
        public final Color text;
        public final Color primary;
        public final Color secondary;
        public final Color accent;

        HUDTheme(String name, Color bg, Color text, Color primary, Color secondary, Color accent) {
            this.name = name;
            this.background = bg;
            this.text = text;
            this.primary = primary;
            this.secondary = secondary;
            this.accent = accent;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
