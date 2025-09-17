package dev.gambleclient.gui;

import dev.gambleclient.Gamble;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.modules.client.Phantom;
import dev.gambleclient.module.setting.*;
import dev.gambleclient.utils.ColorUtil;
import dev.gambleclient.utils.KeyUtils;
import dev.gambleclient.utils.MathUtil;
import dev.gambleclient.utils.RenderUtils;
import dev.gambleclient.utils.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.Locale;

public final class ClickGUI extends Screen {
    private Category selectedCategory;
    private Module selectedModule;
    private String searchQuery;
    public Color currentColor;
    private boolean searchFocused;
    private boolean draggingSlider;
    private Setting draggingSliderSetting;
    private BindSetting listeningBind = null;

    private boolean selectingItem = false;
    private ItemSetting activeItemSetting = null;
    private int itemScrollRowOffset = 0;
    private static final List<Item> ALL_ITEMS = new ArrayList<>();

    private String overlaySearchQuery = "";
    private boolean overlaySearchFocused = false;

    // Existing: active string editing
    private StringSetting activeStringSetting = null;

    // ADDED: Active color editing support
    private ColorSetting activeColorSetting = null;
    private boolean draggingColorComponent = false;
    private int colorComponentIndex = -1; // 0 R, 1 G, 2 B

    private static final int ICON_CELL_SIZE = 30;
    private static final int ICON_OVERLAY_ROWS_VISIBLE = 10;
    private static final int ICON_OVERLAY_WIDTH = 620;
    private static final int ICON_OVERLAY_TOP_PADDING = 66;
    private static final int OVERLAY_SEARCH_HEIGHT = 26;
    private static final int OVERLAY_SEARCH_PADDING_X = 12;

    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final Color PANEL_COLOR = new Color(30, 30, 35, 255);
    private final Color ACCENT_COLOR_FALLBACK = new Color(92, 250, 121, 255);
    private final Color TEXT_COLOR = new Color(220, 220, 220, 255);
    private final Color SEARCH_BG = new Color(40, 40, 45, 255);
    private final Color SEARCH_BG_FOCUSED = new Color(55, 55, 62, 255);
    private final Color HOVER_NEUTRAL = new Color(255,255,255,28);

    private final Color OVERLAY_BG = new Color(18, 18, 22, 242);
    private final Color OVERLAY_BORDER = new Color(92, 250, 121, 110);
    private final Color OVERLAY_HOVER = new Color(92, 250, 121, 70);
    private final Color OVERLAY_SELECTED = new Color(92, 250, 121, 160);

    private static final int SETTINGS_PANEL_WIDTH = 380;
    private static final int CATEGORY_PANEL_WIDTH = 180;
    private static final int MODULE_PANEL_WIDTH = 350;
    private static final int HEADER_HEIGHT = 45;
    private static final int ITEM_HEIGHT = 32;
    private static final int SLIDER_ITEM_HEIGHT = 52;
    private static final int COLOR_ITEM_HEIGHT = 100; // ADDED: taller row for color editing UI
    private static final int PADDING = 15;
    private static final int PANEL_SPACING = 20;
    private static final int TOTAL_WIDTH = SETTINGS_PANEL_WIDTH + CATEGORY_PANEL_WIDTH + MODULE_PANEL_WIDTH + (PANEL_SPACING * 2);
    private static final int TOTAL_HEIGHT = 500;

    private int settingsScrollOffset = 0;
    private int maxSettingsScroll = 0;

    private float openProgress = 0f;
    private final EnumMap<Category, Float> categoryAnim = new EnumMap<>(Category.class);
    private final Map<Module, Float> moduleSelectAnim = new HashMap<>();
    private final Map<Module, Float> moduleEnabledAnim = new HashMap<>();
    private final Map<Setting, Float> settingHoverAnim = new HashMap<>();

    private final List<SettingLayout> settingLayouts = new ArrayList<>();

    private boolean closing = false;

    public ClickGUI() {
        super(Text.empty());
        this.selectedCategory = Category.COMBAT;
        this.searchQuery = "";
        this.searchFocused = false;
        this.draggingSlider = false;
        this.draggingSliderSetting = null;
        for (Category c : Category.values()) categoryAnim.put(c, 0f);
    }

    private static int toMCColor(Color c) {
        return net.minecraft.util.math.ColorHelper.Argb.getArgb(c.getAlpha(), c.getRed(), c.getGreen(), c.getBlue());
    }

    private Color getAccent() {
        try {
            if (Phantom.enableRainbowEffect.getValue()) {
                return ColorUtil.a(1, 255);
            }
            return new Color(
                    Phantom.redColor.getIntValue(),
                    Phantom.greenColor.getIntValue(),
                    Phantom.blueColor.getIntValue(),
                    255
            );
        } catch (Throwable ignored) {
            return ACCENT_COLOR_FALLBACK;
        }
    }

    private int panelAlpha() {
        try {
            return Phantom.windowAlpha.getIntValue();
        } catch (Throwable ignored) {
            return 200;
        }
    }

    private float lerp(float current, float target, double speed) {
        return current + (float) ((target - current) * Math.min(1.0, speed));
    }

    private Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    private Color blend(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(r, g, bl, al);
    }

    public boolean isDraggingAlready() { return this.draggingSlider; }
    public void setTooltip(final CharSequence tooltipText, final int tooltipX, final int tooltipY) {}
    public void setInitialFocus() { if (this.client != null) super.setInitialFocus(); }

    @Override
    public void render(final DrawContext drawContext, final int n, final int n2, final float n3) {
        if (Gamble.mc.currentScreen != this) return;

        if (this.currentColor == null) this.currentColor = new Color(0, 0, 0, 0);
        int targetAlpha = dev.gambleclient.module.modules.client.Phantom.renderBackground.getValue() ? 200 : 0;
        if (this.currentColor.getAlpha() != targetAlpha) {
            this.currentColor = ColorUtil.a(0.05f, targetAlpha, this.currentColor);
        }
        drawContext.fill(0, 0, Gamble.mc.getWindow().getWidth(), Gamble.mc.getWindow().getHeight(), this.currentColor.getRGB());

        RenderUtils.unscaledProjection();
        final int scaledX = n * (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
        final int scaledY = n2 * (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
        super.render(drawContext, scaledX, scaledY, n3);

        openProgress = lerp(openProgress, 1f, 0.15);

        this.renderBackground(drawContext);
        this.renderSettingsPanel(drawContext, scaledX, scaledY);
        this.renderCategoryPanel(drawContext, scaledX, scaledY);
        this.renderModulePanel(drawContext, scaledX, scaledY);

        if (this.selectingItem && this.activeItemSetting != null) {
            this.renderItemIconOverlay(drawContext, scaledX, scaledY);
        }

        RenderUtils.scaledProjection();
    }

    private void renderBackground(final DrawContext drawContext) {
        if (dev.gambleclient.module.modules.client.Phantom.renderBackground.getValue()) {
            drawContext.fill(0, 0, Gamble.mc.getWindow().getWidth(), Gamble.mc.getWindow().getHeight(),
                    toMCColor(new Color(0, 0, 0, (int) (100 * openProgress))));
        }
    }

    private void renderSettingsPanel(final DrawContext ctx, final int mouseX, final int mouseY) {
        final int sw = Gamble.mc.getWindow().getWidth();
        final int sh = Gamble.mc.getWindow().getHeight();
        final int startX = (sw - TOTAL_WIDTH) / 2;
        final int startY = (sh - TOTAL_HEIGHT) / 2;
        final int endX = startX + SETTINGS_PANEL_WIDTH;

        RenderUtils.renderRoundedQuad(ctx.getMatrices(), withAlpha(PANEL_COLOR, (int)(panelAlpha()*openProgress)),
                startX, startY, endX, startY + TOTAL_HEIGHT, 8,8,8,8,50);

        if (selectedModule == null) {
            TextRenderer.drawCenteredString("SELECT A MODULE", ctx,
                    startX + SETTINGS_PANEL_WIDTH/2, startY + 100,
                    toMCColor(new Color(120,120,120,(int)(255*openProgress))));
            return;
        }

        TextRenderer.drawString("SETTINGS: " + selectedModule.getName().toString().toUpperCase(),
                ctx, startX + PADDING, startY + 15, toMCColor(getAccent()));

        int availableHeight = TOTAL_HEIGHT - HEADER_HEIGHT - (PADDING*2);
        int contentStartY = startY + HEADER_HEIGHT + PADDING;

        // Build layout
        settingLayouts.clear();
        int builderY = 0;
        for (Object o : selectedModule.getSettings()) {
            if (!(o instanceof Setting s)) continue;
            int h;
            if (s instanceof NumberSetting || s instanceof MinMaxSetting) h = SLIDER_ITEM_HEIGHT;
            else if (s instanceof ColorSetting) h = COLOR_ITEM_HEIGHT;
            else h = ITEM_HEIGHT;
            settingLayouts.add(new SettingLayout(s, builderY, h));
            builderY += h + 6;
        }
        int totalContentHeight = builderY;
        maxSettingsScroll = Math.max(0, totalContentHeight - availableHeight);
        settingsScrollOffset = Math.max(0, Math.min(settingsScrollOffset, maxSettingsScroll));

        for (int i = 0; i < settingLayouts.size(); i++) {
            SettingLayout layout = settingLayouts.get(i);
            int drawY = contentStartY + layout.relativeY - settingsScrollOffset;
            if (drawY + layout.height < contentStartY - 4 || drawY > contentStartY + availableHeight + 4) continue;

            boolean hovered = isHoveredInRect(mouseX, mouseY, startX, drawY, SETTINGS_PANEL_WIDTH, layout.height);
            boolean editingString = (layout.setting instanceof StringSetting) && layout.setting == activeStringSetting;
            boolean editingColor = (layout.setting instanceof ColorSetting) && layout.setting == activeColorSetting;

            float prev = settingHoverAnim.getOrDefault(layout.setting, 0f);
            float anim = lerp(prev, (hovered && !draggingSlider && !selectingItem) || editingString || editingColor ? 1f : 0f, 0.25);
            settingHoverAnim.put(layout.setting, anim);

            if (anim > 0f) {
                Color hover = new Color(255,255,255,(int)(28*anim));
                RenderUtils.renderRoundedQuad(ctx.getMatrices(), hover,
                        startX+5, drawY, endX-5, drawY + layout.height, 6,6,6,6,20);
            }

            TextRenderer.drawString(layout.setting.getName().toString().toUpperCase(),
                    ctx, startX + PADDING, drawY + 8, toMCColor(TEXT_COLOR));

            renderSettingValue(ctx, layout.setting, startX, endX, drawY);

            if (i < settingLayouts.size() - 1) {
                ctx.fill(startX + 10, drawY + layout.height + 2, endX - 10, drawY + layout.height + 3,
                        toMCColor(new Color(255,255,255,12)));
            }
        }

        if (maxSettingsScroll > 0) {
            int barX = endX - 6;
            int barY = contentStartY;
            int barH = availableHeight;
            ctx.fill(barX,barY,barX+3,barY+barH,toMCColor(new Color(60,60,65,130)));
            double ratio = settingsScrollOffset / (double) maxSettingsScroll;
            int knobH = Math.max(24,(int)(barH * (availableHeight / (double) totalContentHeight)));
            int knobY = barY + (int)((barH - knobH) * ratio);
            ctx.fill(barX, knobY, barX+3, knobY + knobH, toMCColor(getAccent()));
        }
    }

    private void renderSettingValue(DrawContext ctx, Setting setting, int startX, int endX, int y) {
        Color a = getAccent();
        if (setting instanceof BooleanSetting b) {
            String v = b.getValue() ? "ON" : "OFF";
            Color c = b.getValue() ? a : new Color(130,130,130,255);
            TextRenderer.drawString(v, ctx, endX - PADDING - TextRenderer.getWidth(v), y + 8, toMCColor(c));

        } else if (setting instanceof NumberSetting n) {
            String v = String.format("%.2f", n.getValue());
            TextRenderer.drawString(v, ctx, endX - PADDING - TextRenderer.getWidth(v), y + 8, toMCColor(a));
            int sliderY = y + 28;
            int sx = startX + PADDING;
            int ex = endX - PADDING;
            int w = ex - sx;
            RenderUtils.renderRoundedQuad(ctx.getMatrices(), new Color(58,58,63,255), sx, sliderY, ex, sliderY+5,2,2,2,2,16);
            double prog = (n.getValue() - n.getMin()) / (n.getMax() - n.getMin());
            prog = Math.max(0, Math.min(1, prog));
            int fill = (int)(w*prog);
            if (fill>0) RenderUtils.renderRoundedQuad(ctx.getMatrices(), a, sx, sliderY, sx+fill, sliderY+5,2,2,2,2,16);
            RenderUtils.renderRoundedQuad(ctx.getMatrices(), Color.WHITE, sx+fill-4, sliderY-3, sx+fill+4, sliderY+8,4,4,4,4,12);

        } else if (setting instanceof ModeSetting<?> m) {
            String v = m.getValue().toString();
            TextRenderer.drawString(v, ctx, endX - PADDING - TextRenderer.getWidth(v), y + 8, toMCColor(a));

        } else if (setting instanceof BindSetting bind) {
            String v = listeningBind == bind ? "..." :
                    (bind.getValue()==-1 ? "NONE" : KeyUtils.getKey(bind.getValue()).toString());
            TextRenderer.drawString(v, ctx, endX - PADDING - TextRenderer.getWidth(v), y + 8, toMCColor(a));

        } else if (setting instanceof StringSetting s) {
            boolean editing = (s == activeStringSetting);
            String raw = s.getValue();
            String base = raw.isEmpty() ? "EMPTY" : raw;
            Color col = raw.isEmpty() && !editing ? new Color(150,150,150,255) : a;
            if (editing && (System.currentTimeMillis() % 1000) < 500) {
                base = raw + "|";
            }
            TextRenderer.drawString(base, ctx, endX - PADDING - TextRenderer.getWidth(base), y + 8, toMCColor(col));

        } else if (setting instanceof MinMaxSetting mm) {
            String v = String.format("%.1f - %.1f", mm.getCurrentMin(), mm.getCurrentMax());
            TextRenderer.drawString(v, ctx, endX - PADDING - TextRenderer.getWidth(v), y + 8, toMCColor(a));
            int sliderY = y + 28;
            int sx = startX + PADDING;
            int ex = endX - PADDING;
            int w = ex - sx;
            RenderUtils.renderRoundedQuad(ctx.getMatrices(), new Color(58,58,63,255), sx, sliderY, ex, sliderY+5,2,2,2,2,16);
            double minP = (mm.getCurrentMin() - mm.getMinValue())/(mm.getMaxValue()-mm.getMinValue());
            double maxP = (mm.getCurrentMax() - mm.getMinValue())/(mm.getMaxValue()-mm.getMinValue());
            minP = Math.max(0,Math.min(1,minP));
            maxP = Math.max(0,Math.min(1,maxP));
            int minX = sx + (int)(w*minP);
            int maxX = sx + (int)(w*maxP);
            if (maxX>minX) RenderUtils.renderRoundedQuad(ctx.getMatrices(), a, minX, sliderY, maxX, sliderY+5,2,2,2,2,16);
            RenderUtils.renderRoundedQuad(ctx.getMatrices(), Color.WHITE, minX-4, sliderY-3, minX+4, sliderY+8,4,4,4,4,12);
            RenderUtils.renderRoundedQuad(ctx.getMatrices(), Color.WHITE, maxX-4, sliderY-3, maxX+4, sliderY+8,4,4,4,4,12);

        } else if (setting instanceof ItemSetting is) {
            Item item = is.getItem();
            String id = item==null ? "NONE" : Registries.ITEM.getId(item).toString().toUpperCase();
            TextRenderer.drawString(id, ctx, endX - PADDING - TextRenderer.getWidth(id), y + 8, toMCColor(a));

        } else if (setting instanceof ColorSetting cs) {
            Color val = cs.getValue();
            String hex = String.format("#%02X%02X%02X", val.getRed(), val.getGreen(), val.getBlue());
            int previewSize = 16;
            int previewX = endX - PADDING - previewSize;
            int previewY = y + 7;
            RenderUtils.renderRoundedQuad(ctx.getMatrices(), val, previewX, previewY, previewX + previewSize, previewY + previewSize, 4,4,4,4,16);
            ctx.fill(previewX-1, previewY-1, previewX+previewSize+1, previewY, toMCColor(Color.DARK_GRAY));
            ctx.fill(previewX-1, previewY+previewSize, previewX+previewSize+1, previewY+previewSize+1, toMCColor(Color.DARK_GRAY));
            ctx.fill(previewX-1, previewY, previewX, previewY+previewSize, toMCColor(Color.DARK_GRAY));
            ctx.fill(previewX+previewSize, previewY, previewX+previewSize+1, previewY+previewSize, toMCColor(Color.DARK_GRAY));

            TextRenderer.drawString(hex, ctx, previewX - 6 - TextRenderer.getWidth(hex), y + 8, toMCColor(a));

            if (activeColorSetting == cs) {
                int sliderStartX = startX + PADDING;
                int sliderEndX = endX - PADDING - 20;
                int baseY = y + 32;

                drawColorComponentSlider(ctx, "R", 0, val.getRed(), 255, sliderStartX, sliderEndX, baseY, new Color(255,80,80,255));
                drawColorComponentSlider(ctx, "G", 1, val.getGreen(), 255, sliderStartX, sliderEndX, baseY + 20, new Color(80,255,80,255));
                drawColorComponentSlider(ctx, "B", 2, val.getBlue(), 255, sliderStartX, sliderEndX, baseY + 40, new Color(80,80,255,255));
                String hint = "L-CLICK SLIDER | L-CLICK PREVIEW TO CLOSE | R-CLICK RESET";
                TextRenderer.drawString(hint, ctx, sliderStartX, baseY + 62, toMCColor(new Color(140,140,140,200)));
            } else {
                String hint = "CLICK PREVIEW TO EDIT";
                TextRenderer.drawString(hint, ctx, startX + PADDING, y + 32, toMCColor(new Color(140,140,140,160)));
            }
        }
    }

    private void drawColorComponentSlider(DrawContext ctx, String name, int index, int value, int max, int sx, int ex, int y, Color col){
        TextRenderer.drawString(name + ":" + value, ctx, sx, y - 2, toMCColor(col));
        int barY = y + 8;
        RenderUtils.renderRoundedQuad(ctx.getMatrices(), new Color(50,50,55,255), sx, barY, ex, barY+5,2,2,2,2,14);
        double prog = value / (double) max;
        int fill = (int)((ex - sx) * prog);
        if (fill>0) RenderUtils.renderRoundedQuad(ctx.getMatrices(), col, sx, barY, sx+fill, barY+5,2,2,2,2,14);
        RenderUtils.renderRoundedQuad(ctx.getMatrices(), Color.WHITE, sx+fill-3, barY-3, sx+fill+3, barY+8,3,3,3,3,12);
    }

    private void applyColorComponent(ColorSetting cs, int compIndex, int mx, int sx, int ex){
        int clamped = Math.max(0, Math.min(255, (int)(((mx - sx) / (double)(ex - sx)) * 255)));
        Color old = cs.getValue();
        int r=old.getRed(), g=old.getGreen(), b=old.getBlue();
        if (compIndex==0) r = clamped;
        else if (compIndex==1) g = clamped;
        else if (compIndex==2) b = clamped;
        cs.setValue(new Color(r,g,b,255));
    }

    private void renderCategoryPanel(final DrawContext ctx, final int mouseX, final int mouseY) {
        final int sw = Gamble.mc.getWindow().getWidth();
        final int sh = Gamble.mc.getWindow().getHeight();
        final int startX = (sw - TOTAL_WIDTH) / 2 + SETTINGS_PANEL_WIDTH + PANEL_SPACING;
        final int startY = (sh - TOTAL_HEIGHT) / 2;
        final int endX = startX + CATEGORY_PANEL_WIDTH;

        RenderUtils.renderRoundedQuad(ctx.getMatrices(), withAlpha(PANEL_COLOR,(int)(panelAlpha()*openProgress)),
                startX,startY,endX,startY+TOTAL_HEIGHT,8,8,8,8,50);

        TextRenderer.drawString("Phantom+", ctx, startX + PADDING, startY + 15, toMCColor(getAccent()));

        int y = startY + HEADER_HEIGHT + PADDING;
        for (Category c : Category.values()) {
            boolean selected = c == selectedCategory;
            boolean hovered = isHoveredInRect(mouseX, mouseY, startX, y, CATEGORY_PANEL_WIDTH, ITEM_HEIGHT);

            float prev = categoryAnim.getOrDefault(c,0f);
            float anim = lerp(prev, selected ? 1f : 0f, 0.25);
            categoryAnim.put(c,anim);

            Color bg = null;
            if (selected) {
                bg = withAlpha(getAccent(), (int)(getAccent().getAlpha()*0.55));
            } else if (hovered) {
                bg = HOVER_NEUTRAL;
            }

            if (bg != null) {
                RenderUtils.renderRoundedQuad(ctx.getMatrices(), bg,
                        startX+5,y,endX-5,y+ITEM_HEIGHT,6,6,6,6,24);
            }

            Color textCol = selected ? Color.WHITE : TEXT_COLOR;
            TextRenderer.drawString(c.name.toString().toUpperCase(), ctx, startX + PADDING, y + 8, toMCColor(textCol));
            y += ITEM_HEIGHT + 5;
        }
    }

    private void renderModulePanel(final DrawContext ctx, final int mouseX, final int mouseY) {
        final int sw = Gamble.mc.getWindow().getWidth();
        final int sh = Gamble.mc.getWindow().getHeight();
        final int startX = (sw - TOTAL_WIDTH) / 2 + SETTINGS_PANEL_WIDTH + PANEL_SPACING + CATEGORY_PANEL_WIDTH + PANEL_SPACING;
        final int startY = (sh - TOTAL_HEIGHT) / 2;
        final int endX = startX + MODULE_PANEL_WIDTH;

        RenderUtils.renderRoundedQuad(ctx.getMatrices(), withAlpha(PANEL_COLOR,(int)(panelAlpha()*openProgress)),
                startX,startY,endX,startY+TOTAL_HEIGHT,8,8,8,8,50);

        TextRenderer.drawString("CATEGORY: " + selectedCategory.name.toString().toUpperCase(),
                ctx, startX + PADDING, startY + 15, toMCColor(TEXT_COLOR));

        // SEARCH BAR (Adjusted for vertical centering)
        final int searchHeight = 25;
        int searchY = startY + HEADER_HEIGHT - 15;
        int searchStartX = startX + PADDING;
        int searchEndX = endX - PADDING;
        Color searchBgColor = searchFocused ? SEARCH_BG_FOCUSED : SEARCH_BG;
        RenderUtils.renderRoundedQuad(ctx.getMatrices(), searchBgColor,
                searchStartX, searchY, searchEndX, searchY + searchHeight, 6,6,6,6,30);

        String searchText = searchQuery.isEmpty() ? "SEARCH..." : searchQuery;
        Color searchTextColor = searchQuery.isEmpty() ? new Color(120,120,120,255) : TEXT_COLOR;

        int fontHeight = MinecraftClient.getInstance().textRenderer.fontHeight;
        int centeredTextY = searchY + (searchHeight - fontHeight) / 2;

        TextRenderer.drawString(searchText, ctx, searchStartX + 10, centeredTextY, toMCColor(searchTextColor));
        if (searchFocused && System.currentTimeMillis()%1000 < 500) {
            int cursorX = searchStartX + 10 + TextRenderer.getWidth(searchQuery);
            // caret spans full glyph height
            ctx.fill(cursorX, centeredTextY - 1, cursorX + 1, centeredTextY + fontHeight, toMCColor(TEXT_COLOR));
        }

        List<Module> modules = Gamble.INSTANCE.getModuleManager().a(selectedCategory);
        int y = startY + HEADER_HEIGHT + searchHeight;
        for (Module m : modules) {
            if (!searchQuery.isEmpty() &&
                    !m.getName().toString().toLowerCase().contains(searchQuery.toLowerCase())) continue;

            boolean selected = m == selectedModule;
            boolean hovered = isHoveredInRect(mouseX, mouseY, startX, y, MODULE_PANEL_WIDTH, ITEM_HEIGHT);
            boolean enabled = m.isEnabled();

            float prevSel = moduleSelectAnim.getOrDefault(m,0f);
            float selAnim = lerp(prevSel, selected ? 1f : 0f, 0.25);
            moduleSelectAnim.put(m, selAnim);

            float prevEn = moduleEnabledAnim.getOrDefault(m,0f);
            float enAnim = lerp(prevEn, enabled ? 1f : 0f, 0.2);
            moduleEnabledAnim.put(m, enAnim);

            Color bg = null;
            if (selected) {
                bg = withAlpha(getAccent(), (int)(getAccent().getAlpha()*0.55));
            } else if (hovered) {
                bg = HOVER_NEUTRAL;
            }
            if (bg != null) {
                RenderUtils.renderRoundedQuad(ctx.getMatrices(), bg,
                        startX + 5, y, endX - 5, y + ITEM_HEIGHT, 6,6,6,6,24);
            }

            Color textCol = enabled ? blend(getAccent(), Color.WHITE, selAnim * 0.4f) : TEXT_COLOR;
            TextRenderer.drawString(m.getName().toString().toUpperCase(), ctx,
                    startX + PADDING, y + 8, toMCColor(textCol));

            int indicatorWidth = (int)(12 + 10 * enAnim);
            Color indicator = enabled ? withAlpha(getAccent(), 200) : new Color(80,80,85,200);
            RenderUtils.renderRoundedQuad(ctx.getMatrices(), indicator,
                    endX - 10 - indicatorWidth, y + 9, endX - 10, y + 23, 4,4,4,4,16);

            y += ITEM_HEIGHT + 3;
        }
    }

    private boolean isHoveredInRect(final int mouseX, final int mouseY, final int x, final int y, final int width, final int height) {
        return mouseX >= x && mouseX <= x + width && mouseY <= y + height && mouseY >= y;
    }
    private boolean isOverSettingsPanel(int mx,int my){
        int sw = Gamble.mc.getWindow().getWidth();
        int sh = Gamble.mc.getWindow().getHeight();
        int startX = (sw - TOTAL_WIDTH)/2;
        int startY = (sh - TOTAL_HEIGHT)/2;
        return isHoveredInRect(mx,my,startX,startY+HEADER_HEIGHT,SETTINGS_PANEL_WIDTH,TOTAL_HEIGHT-HEADER_HEIGHT);
    }
    private boolean isSearchBarHovered(final int mouseX, final int mouseY) {
        int sw = Gamble.mc.getWindow().getWidth();
        int sh = Gamble.mc.getWindow().getHeight();
        int startX = (sw - TOTAL_WIDTH) / 2 + SETTINGS_PANEL_WIDTH + PANEL_SPACING + CATEGORY_PANEL_WIDTH + PANEL_SPACING;
        int startY = (sh - TOTAL_HEIGHT) / 2;
        int searchY = startY + HEADER_HEIGHT - 15;
        int searchStartX = startX + PADDING;
        int searchEndX = startX + MODULE_PANEL_WIDTH - PADDING;
        return isHoveredInRect(mouseX, mouseY, searchStartX, searchY, searchEndX - searchStartX, 25);
    }

    @Override
    public boolean keyPressed(int keyCode,int scanCode,int modifiers){
        if (activeStringSetting != null) {
            if (keyCode == 259) {
                String current = activeStringSetting.getValue();
                if (!current.isEmpty()) {
                    activeStringSetting.setValue(current.substring(0, current.length() - 1));
                }
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                activeStringSetting = null;
                return true;
            }
            if (keyCode == 256) {
                activeStringSetting = null;
                return true;
            }
        }

        if (selectingItem){
            if (overlaySearchFocused){
                if (keyCode==259 && !overlaySearchQuery.isEmpty()){
                    overlaySearchQuery = overlaySearchQuery.substring(0, overlaySearchQuery.length()-1);
                    return true;
                }
                if (keyCode==256){ overlaySearchFocused=false; return true; }
            } else if (keyCode==256){
                closeItemOverlay(); return true;
            }
        }

        if (listeningBind != null){
            if (keyCode==256 || keyCode==259) listeningBind.setValue(-1);
            else listeningBind.setValue(keyCode);
            if (listeningBind.isModuleKey() && selectedModule!=null &&
                    selectedModule.getSettings().contains(listeningBind)) {
                selectedModule.setKeybind(listeningBind.getValue());
            }
            listeningBind=null;
            return true;
        }

        if (searchFocused){
            if (keyCode==259 && !searchQuery.isEmpty()){
                searchQuery = searchQuery.substring(0, searchQuery.length()-1);
                return true;
            } else if (keyCode==256){
                searchFocused=false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr,int modifiers){
        if (activeStringSetting != null) {
            if (isAllowed(chr)) {
                String cur = activeStringSetting.getValue();
                if (cur.length() < 64) {
                    activeStringSetting.setValue(cur + chr);
                }
                return true;
            }
            return super.charTyped(chr,modifiers);
        }

        if (selectingItem && overlaySearchFocused){
            if (isAllowed(chr)){ overlaySearchQuery += chr; return true; }
            return super.charTyped(chr,modifiers);
        }
        if (searchFocused && isAllowed(chr)){
            searchQuery += chr;
            return true;
        }
        return super.charTyped(chr,modifiers);
    }
    private boolean isAllowed(char c){
        return Character.isLetterOrDigit(c)||c=='_'||c=='-'||c==' '||c=='.';
    }

    @Override
    public boolean mouseClicked(double mx,double my,int button){
        double smx = mx * MinecraftClient.getInstance().getWindow().getScaleFactor();
        double smy = my * MinecraftClient.getInstance().getWindow().getScaleFactor();

        if (selectingItem){
            if (handleOverlaySearchClick((int)smx,(int)smy)) return true;
            if (!isInsideItemOverlay((int)smx,(int)smy)){ closeItemOverlay(); return true; }
            handleItemIconOverlayClick((int)smx,(int)smy,button);
            return true;
        }

        int sw = Gamble.mc.getWindow().getWidth();
        int sh = Gamble.mc.getWindow().getHeight();

        boolean clickedInsideStringRow = false;
        boolean clickedInsideColorRow = false;

        if (isSearchBarHovered((int)smx,(int)smy)){
            searchFocused = true;
            activeStringSetting = null;
            activeColorSetting = null;
            return true;
        } else searchFocused=false;

        int catX = (sw - TOTAL_WIDTH)/2 + SETTINGS_PANEL_WIDTH + PANEL_SPACING;
        int catY = (sh - TOTAL_HEIGHT)/2 + HEADER_HEIGHT + PADDING;
        for (Category c : Category.values()){
            if (isHoveredInRect((int)smx,(int)smy,catX,catY,CATEGORY_PANEL_WIDTH,ITEM_HEIGHT)){
                selectedCategory = c;
                selectedModule = null;
                activeStringSetting = null;
                activeColorSetting = null;
                return true;
            }
            catY += ITEM_HEIGHT + 5;
        }

        int modX = (sw - TOTAL_WIDTH)/2 + SETTINGS_PANEL_WIDTH + PANEL_SPACING + CATEGORY_PANEL_WIDTH + PANEL_SPACING;
        int modY = (sh - TOTAL_HEIGHT)/2 + HEADER_HEIGHT + 25;
        List<Module> modules = Gamble.INSTANCE.getModuleManager().a(selectedCategory);
        for (Module m : modules){
            if (!searchQuery.isEmpty() && !m.getName().toString().toLowerCase().contains(searchQuery.toLowerCase())) continue;
            if (isHoveredInRect((int)smx,(int)smy,modX,modY,MODULE_PANEL_WIDTH,ITEM_HEIGHT)){
                if (button==0) m.toggle();
                else if (button==1) selectedModule = m;
                activeStringSetting = null;
                activeColorSetting = null;
                return true;
            }
            modY += ITEM_HEIGHT + 3;
        }

        if (selectedModule != null){
            int settingsX = (sw - TOTAL_WIDTH)/2;
            int startY = (sh - TOTAL_HEIGHT)/2 + HEADER_HEIGHT + PADDING;
            for (SettingLayout l : settingLayouts){
                int drawY = startY + l.relativeY - settingsScrollOffset;
                if (isHoveredInRect((int)smx,(int)smy,settingsX,drawY,SETTINGS_PANEL_WIDTH,l.height)){
                    if (l.setting instanceof StringSetting) clickedInsideStringRow = true;
                    if (l.setting instanceof ColorSetting) clickedInsideColorRow = true;
                    handleSettingClick(l.setting,button,(int)smx,(int)smy,settingsX,drawY,l.height);
                    return true;
                }
            }
        }

        if (!clickedInsideStringRow && activeStringSetting != null) activeStringSetting = null;
        if (!clickedInsideColorRow && activeColorSetting != null && !draggingColorComponent) activeColorSetting = null;

        return super.mouseClicked(smx,smy,button);
    }

    private void handleSettingClick(Setting setting,int button,int mx,int my,int panelX,int settingY,int rowHeight){
        if (!(setting instanceof StringSetting) && activeStringSetting != null) {
            activeStringSetting = null;
        }

        if (!(setting instanceof ColorSetting) && activeColorSetting != null && !draggingColorComponent) {
            activeColorSetting = null;
        }

        if (setting instanceof BooleanSetting b){
            if (button==0) b.toggle();
        } else if (setting instanceof ModeSetting<?> m){
            if (button==0) m.cycleUp();
            else if (button==1) m.cycleDown();
        } else if (setting instanceof NumberSetting n){
            int sliderY = settingY + 28;
            int sx = panelX + PADDING;
            int ex = panelX + SETTINGS_PANEL_WIDTH - PADDING;
            if (isHoveredInRect(mx,my,sx,sliderY-6,ex-sx,18)){
                draggingSlider = true;
                draggingSliderSetting = n;
                updateSliderValue(n,mx,sx,ex);
            }
        } else if (setting instanceof MinMaxSetting mm){
            int sliderY = settingY + 28;
            int sx = panelX + PADDING;
            int ex = panelX + SETTINGS_PANEL_WIDTH - PADDING;
            if (isHoveredInRect(mx,my,sx,sliderY-6,ex-sx,18)){
                draggingSlider = true;
                draggingSliderSetting = mm;
                updateMinMaxSliderValue(mm,mx,sx,ex);
            }
        } else if (setting instanceof BindSetting bind){
            if (button==0) listeningBind = bind;
            else if (button==1){
                bind.setValue(-1);
                if (bind.isModuleKey() && selectedModule!=null && selectedModule.getSettings().contains(bind))
                    selectedModule.setKeybind(-1);
                listeningBind = null;
            }
        } else if (setting instanceof ItemSetting is){
            if (button==0) openItemOverlay(is);
            else if (button==1) is.setItem(null);
        } else if (setting instanceof StringSetting s) {
            if (button == 0) {
                activeStringSetting = s;
                searchFocused = false;
            } else if (button == 1) {
                s.setValue("");
                if (activeStringSetting == s) activeStringSetting = null;
            }
        } else if (setting instanceof ColorSetting cs) {
            int previewSize = 16;
            int previewX = panelX + SETTINGS_PANEL_WIDTH - PADDING - previewSize;
            int previewY = settingY + 7;
            boolean inPreview = mx >= previewX && mx <= previewX + previewSize && my >= previewY && my <= previewY + previewSize;
            if (button == 1) {
                cs.resetValue();
                if (activeColorSetting == cs) activeColorSetting = null;
                return;
            }
            if (button == 0) {
                if (inPreview) {
                    if (activeColorSetting == cs) activeColorSetting = null;
                    else {
                        activeColorSetting = cs;
                        activeStringSetting = null;
                    }
                } else if (activeColorSetting == cs) {
                    int sliderStartX = panelX + PADDING;
                    int sliderEndX = panelX + SETTINGS_PANEL_WIDTH - PADDING - 20;
                    int baseY = settingY + 32;
                    for (int i=0;i<3;i++){
                        int barY = baseY + i*20 + 8;
                        if (isHoveredInRect(mx,my,sliderStartX,barY-6,sliderEndX-sliderStartX,18)){
                            draggingColorComponent = true;
                            colorComponentIndex = i;
                            applyColorComponent(cs, i, mx, sliderStartX, sliderEndX);
                            break;
                        }
                    }
                } else {
                    activeColorSetting = cs;
                    activeStringSetting = null;
                }
            }
        }
    }

    private void updateSliderValue(NumberSetting setting,int mx,int sx,int ex){
        double prog = Math.max(0,Math.min(1,(mx - sx)/(double)(ex - sx)));
        double newVal = setting.getMin() + prog * (setting.getMax() - setting.getMin());
        setting.getValue(MathUtil.roundToNearest(newVal, setting.getFormat()));
    }
    private void updateMinMaxSliderValue(MinMaxSetting s,int mx,int sx,int ex){
        double prog = Math.max(0,Math.min(1,(mx - sx)/(double)(ex - sx)));
        double newVal = s.getMinValue() + prog * (s.getMaxValue() - s.getMinValue());
        double minProg = (s.getCurrentMin() - s.getMinValue()) / (s.getMaxValue() - s.getMinValue());
        double maxProg = (s.getCurrentMax() - s.getMinValue()) / (s.getMaxValue() - s.getMinValue());
        if (Math.abs(prog - minProg) < Math.abs(prog - maxProg)) {
            s.setCurrentMin(Math.min(newVal, s.getCurrentMax()));
        } else {
            s.setCurrentMax(Math.max(newVal, s.getCurrentMin()));
        }
    }

    @Override
    public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if (draggingSlider && draggingSliderSetting!=null){
            double smx = mx * MinecraftClient.getInstance().getWindow().getScaleFactor();
            int sw = Gamble.mc.getWindow().getWidth();
            int sx = (sw - TOTAL_WIDTH)/2 + PADDING;
            int ex = (sw - TOTAL_WIDTH)/2 + SETTINGS_PANEL_WIDTH - PADDING;
            if (draggingSliderSetting instanceof NumberSetting n) updateSliderValue(n,(int)smx,sx,ex);
            else if (draggingSliderSetting instanceof MinMaxSetting mm) updateMinMaxSliderValue(mm,(int)smx,sx,ex);
            return true;
        }
        if (draggingColorComponent && activeColorSetting != null){
            double smx = mx * MinecraftClient.getInstance().getWindow().getScaleFactor();
            int sw = Gamble.mc.getWindow().getWidth();
            int panelX = (sw - TOTAL_WIDTH)/2;
            int sliderStartX = panelX + PADDING;
            int sliderEndX = panelX + SETTINGS_PANEL_WIDTH - PADDING - 20;
            applyColorComponent(activeColorSetting, colorComponentIndex, (int)smx, sliderStartX, sliderEndX);
            return true;
        }
        return super.mouseDragged(mx,my,button,dx,dy);
    }

    @Override
    public boolean mouseReleased(double mx,double my,int button){
        if (draggingSlider){
            draggingSlider = false;
            draggingSliderSetting = null;
            return true;
        }
        if (draggingColorComponent){
            draggingColorComponent = false;
            colorComponentIndex = -1;
            return true;
        }
        return super.mouseReleased(mx,my,button);
    }

    @Override
    public boolean mouseScrolled(double mx,double my,double ha,double va){
        double smx = mx * MinecraftClient.getInstance().getWindow().getScaleFactor();
        double smy = my * MinecraftClient.getInstance().getWindow().getScaleFactor();
        if (selectingItem){
            if (va!=0){ itemScrollRowOffset -= (int)Math.signum(va); return true; }
        }
        else if (selectedModule!=null && va!=0 && isOverSettingsPanel((int)smx,(int)smy)){
            settingsScrollOffset += (int)(-va * 18);
            settingsScrollOffset = Math.max(0,Math.min(settingsScrollOffset,maxSettingsScroll));
            return true;
        }
        return super.mouseScrolled(mx,my,ha,va);
    }

    private void openItemOverlay(ItemSetting s){
        activeItemSetting = s;
        selectingItem = true;
        itemScrollRowOffset = 0;
        overlaySearchQuery = "";
        overlaySearchFocused = false;
        activeStringSetting = null;
        activeColorSetting = null;
    }
    private void closeItemOverlay(){
        selectingItem = false;
        activeItemSetting = null;
        itemScrollRowOffset = 0;
        overlaySearchQuery = "";
        overlaySearchFocused = false;
    }
    private OverlayBounds getItemOverlayBounds(){
        int sw = Gamble.mc.getWindow().getWidth();
        int sh = Gamble.mc.getWindow().getHeight();
        int w = ICON_OVERLAY_WIDTH;
        int cols = Math.max(12,(w-30)/ICON_CELL_SIZE);
        int h = ICON_OVERLAY_TOP_PADDING + (ICON_OVERLAY_ROWS_VISIBLE * ICON_CELL_SIZE) + 24;
        int x = (sw - w)/2;
        int y = (sh - h)/2;
        return new OverlayBounds(x,y,w,h,cols);
    }
    private boolean isInsideItemOverlay(int mx,int my){
        OverlayBounds b = getItemOverlayBounds();
        return mx>=b.x && mx<=b.x+b.w && my>=b.y && my<=b.y+b.h;
    }
    private List<Item> getOverlayFilteredItems(){
        String f = overlaySearchQuery==null?"":overlaySearchQuery.toLowerCase(Locale.ROOT);
        List<Item> list = new ArrayList<>();
        for (Item it : ALL_ITEMS){
            Identifier id = Registries.ITEM.getId(it);
            if (id==null) continue;
            String path = id.getPath();
            if (f.isEmpty() || path.contains(f)) list.add(it);
        }
        return list;
    }
    private void renderItemIconOverlay(DrawContext ctx,int mx,int my){
        OverlayBounds b = getItemOverlayBounds();
        RenderUtils.renderRoundedQuad(ctx.getMatrices(), OVERLAY_BG, b.x,b.y,b.x+b.w,b.y+b.h,10,10,10,10,60);
        ctx.fill(b.x,b.y,b.x+b.w,b.y+1,toMCColor(OVERLAY_BORDER));
        ctx.fill(b.x,b.y+b.h-1,b.x+b.w,b.y+b.h,toMCColor(OVERLAY_BORDER));
        ctx.fill(b.x,b.y,b.x+1,b.y+b.h,toMCColor(OVERLAY_BORDER));
        ctx.fill(b.x+b.w-1,b.y,b.x+b.w,b.y+b.h,toMCColor(OVERLAY_BORDER));

        TextRenderer.drawString("SELECT ITEM (ESC / CLICK OUTSIDE TO CLOSE)", ctx, b.x+10,b.y+10,toMCColor(getAccent()));

        int sx = b.x + OVERLAY_SEARCH_PADDING_X;
        int sy = b.y + 30;
        int sw = b.w - OVERLAY_SEARCH_PADDING_X*2;
        int sh = OVERLAY_SEARCH_HEIGHT;
        Color sbg = overlaySearchFocused ? SEARCH_BG_FOCUSED : SEARCH_BG;
        RenderUtils.renderRoundedQuad(ctx.getMatrices(), sbg, sx, sy, sx+sw, sy+sh,6,6,6,6,40);
        String sTxt = overlaySearchQuery.isEmpty() ? "SEARCH ITEM..." : overlaySearchQuery;
        Color sCol = overlaySearchQuery.isEmpty()? new Color(130,130,130,255) : TEXT_COLOR;

        int fontHeight = MinecraftClient.getInstance().textRenderer.fontHeight;
        int centeredTextY = sy + (sh - fontHeight)/2;

        TextRenderer.drawString(sTxt, ctx, sx+8, centeredTextY, toMCColor(sCol));
        if (overlaySearchFocused && (System.currentTimeMillis()/500)%2==0){
            int cx = sx+8+TextRenderer.getWidth(overlaySearchQuery);
            ctx.fill(cx, centeredTextY - 1, cx+1, centeredTextY + fontHeight, toMCColor(TEXT_COLOR));
        }

        int gridX = b.x + 10;
        int gridY = b.y + ICON_OVERLAY_TOP_PADDING;
        List<Item> list = getOverlayFilteredItems();
        int cols = b.columns;
        int totalRows = (int)Math.ceil(list.size()/(double)cols);
        int maxOffset = Math.max(0,totalRows - ICON_OVERLAY_ROWS_VISIBLE);
        if (itemScrollRowOffset > maxOffset) itemScrollRowOffset = maxOffset;
        if (itemScrollRowOffset < 0) itemScrollRowOffset = 0;
        int startRow = itemScrollRowOffset;
        int endRow = Math.min(startRow + ICON_OVERLAY_ROWS_VISIBLE, totalRows);

        for (int row=startRow; row<endRow; row++){
            for (int col=0; col<cols; col++){
                int index = row*cols + col;
                if (index >= list.size()) break;
                int x = gridX + col*ICON_CELL_SIZE;
                int y = gridY + (row - startRow)*ICON_CELL_SIZE;
                int ex = x + ICON_CELL_SIZE - 4;
                int ey = y + ICON_CELL_SIZE - 4;
                boolean hovered = mx>=x && mx<ex && my>=y && my<ey;
                Item item = list.get(index);
                boolean selected = activeItemSetting!=null && activeItemSetting.getItem()==item;
                Color bg = hovered ? OVERLAY_HOVER : new Color(255,255,255,18);
                if (selected) bg = OVERLAY_SELECTED;
                RenderUtils.renderRoundedQuad(ctx.getMatrices(), bg, x,y,ex,ey,5,5,5,5,16);
                ItemStack stack = new ItemStack(item);
                try {
                    ctx.drawItem(stack, x + (ICON_CELL_SIZE/2)-8, y + (ICON_CELL_SIZE/2)-8);
                    ctx.drawItemInSlot(this.textRenderer, stack, x + (ICON_CELL_SIZE/2)-8, y + (ICON_CELL_SIZE/2)-8);
                } catch (Throwable ignored){}
            }
        }

        if (maxOffset > 0){
            int barX = b.x + b.w - 14;
            int barY = gridY;
            int barH = ICON_OVERLAY_ROWS_VISIBLE * ICON_CELL_SIZE;
            ctx.fill(barX,barY,barX+6,barY+barH,toMCColor(new Color(40,40,46,180)));
            double ratio = itemScrollRowOffset / (double) maxOffset;
            int knobH = Math.max(18,(int)(barH * (ICON_OVERLAY_ROWS_VISIBLE / (double) totalRows)));
            int knobY = barY + (int)((barH - knobH) * ratio);
            ctx.fill(barX+1,knobY,barX+5,knobY+knobH,toMCColor(getAccent()));
        }
    }

    private boolean handleItemIconOverlayClick(int mx,int my,int button){
        if (activeItemSetting==null || button!=0) return false;
        OverlayBounds b = getItemOverlayBounds();
        int gridX = b.x + 10;
        int gridY = b.y + ICON_OVERLAY_TOP_PADDING;
        List<Item> list = getOverlayFilteredItems();
        int cols = b.columns;
        int totalRows = (int)Math.ceil(list.size()/(double)cols);
        int startRow = itemScrollRowOffset;
        int endRow = Math.min(startRow + ICON_OVERLAY_ROWS_VISIBLE, totalRows);
        for (int row=startRow; row<endRow; row++){
            for (int col=0; col<cols; col++){
                int index = row*cols + col;
                if (index >= list.size()) break;
                int x = gridX + col*ICON_CELL_SIZE;
                int y = gridY + (row - startRow)*ICON_CELL_SIZE;
                int ex = x + ICON_CELL_SIZE - 4;
                int ey = y + ICON_CELL_SIZE - 4;
                if (mx>=x && mx<ex && my>=y && my<ey){
                    activeItemSetting.setItem(list.get(index));
                    closeItemOverlay();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleOverlaySearchClick(int mx,int my){
        if (!selectingItem) return false;
        OverlayBounds b = getItemOverlayBounds();
        int sx = b.x + OVERLAY_SEARCH_PADDING_X;
        int sy = b.y + 30;
        int sw = b.w - OVERLAY_SEARCH_PADDING_X*2;
        int sh = OVERLAY_SEARCH_HEIGHT;
        boolean inside = mx>=sx && mx<=sx+sw && my>=sy && my<=sy+sh;
        if (inside){ overlaySearchFocused = true; return true; }
        if (overlaySearchFocused && isInsideItemOverlay(mx,my)){ overlaySearchFocused=false; return true; }
        return false;
    }

    @Override public boolean shouldPause(){ return false; }

    @Override
    public void close(){
        if (closing){ super.close(); return; }
        closing = true;
        disablePhantomSafely();
        super.close();
        closing = false;
    }
    @Override
    public void removed(){
        onGuiClose();
        super.removed();
    }
    public void onGuiClose(){
        currentColor=null;
        searchFocused=false;
        draggingSlider=false;
        draggingSliderSetting=null;
        listeningBind=null;
        closeItemOverlay();
        openProgress=0f;
        settingsScrollOffset=0;
        activeStringSetting = null;
        activeColorSetting = null;
        draggingColorComponent = false;
        colorComponentIndex = -1;
    }

    private void disablePhantomSafely(){
        try {
            Object mm = Gamble.INSTANCE.getModuleManager();
            try {
                Method m = mm.getClass().getMethod("getModuleByClass", Class.class);
                Object mod = m.invoke(mm, dev.gambleclient.module.modules.client.Phantom.class);
                if (mod instanceof Module) {
                    Module p = (Module) mod;
                    if (p.isEnabled()) p.setEnabled(false);
                    return;
                }
            } catch (NoSuchMethodException ignored) {}
            List<Module> clientMods = Gamble.INSTANCE.getModuleManager().a(Category.CLIENT);
            if (clientMods!=null){
                for (Module m : clientMods){
                    String name = m.getName().toString();
                    if (m instanceof dev.gambleclient.module.modules.client.Phantom ||
                            name.equalsIgnoreCase("Phantom++") ||
                            name.equalsIgnoreCase("Phantom+") ||
                            name.equalsIgnoreCase("Phantom")) {
                        if (m.isEnabled()) m.setEnabled(false);
                        return;
                    }
                }
            }
        } catch (Throwable ignored){}
    }

    private static final class SettingLayout {
        final Setting setting;
        final int relativeY;
        final int height;
        SettingLayout(Setting s,int y,int h){ this.setting=s; this.relativeY=y; this.height=h; }
    }
    private static final class OverlayBounds {
        int x,y,w,h,columns;
        OverlayBounds(int x,int y,int w,int h,int c){ this.x=x; this.y=y; this.w=w; this.h=h; this.columns=c; }
    }

    static {
        try { for (Item item : Registries.ITEM) ALL_ITEMS.add(item); } catch (Throwable ignored){}
    }
}