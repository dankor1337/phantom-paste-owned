package dev.gambleclient.module.modules.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.Render2DEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.RenderUtils;
import dev.gambleclient.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.Color;

public final class RegionMap extends Module {
    private final NumberSetting x = new NumberSetting(EncryptedString.of("X"), 0, 1920, 10, 1);
    private final NumberSetting y = new NumberSetting(EncryptedString.of("Y"), 0, 1080, 10, 1);
    private final NumberSetting scale = new NumberSetting(EncryptedString.of("Scale"), 15, 40, 20, 1);
    private final NumberSetting textScale = new NumberSetting(EncryptedString.of("Text Scale"), 0.5, 2.0, 0.7, 0.1);
    private final BooleanSetting showCoords = new BooleanSetting(EncryptedString.of("Show Coords"), true);
    private final BooleanSetting showRegionNames = new BooleanSetting(EncryptedString.of("Show Region Names"), true);
    private final BooleanSetting showGrid = new BooleanSetting(EncryptedString.of("Show Grid"), true);
    private final NumberSetting bgRed = new NumberSetting(EncryptedString.of("BG Red"), 0, 255, 30, 1);
    private final NumberSetting bgGreen = new NumberSetting(EncryptedString.of("BG Green"), 0, 255, 30, 1);
    private final NumberSetting bgBlue = new NumberSetting(EncryptedString.of("BG Blue"), 0, 255, 30, 1);
    private final NumberSetting bgAlpha = new NumberSetting(EncryptedString.of("BG Alpha"), 0, 255, 120, 1);
    private final NumberSetting arrowRed = new NumberSetting(EncryptedString.of("Arrow Red"), 0, 255, 255, 1);
    private final NumberSetting arrowGreen = new NumberSetting(EncryptedString.of("Arrow Green"), 0, 255, 0, 1);
    private final NumberSetting arrowBlue = new NumberSetting(EncryptedString.of("Arrow Blue"), 0, 255, 0, 1);
    private final NumberSetting gridRed = new NumberSetting(EncryptedString.of("Grid Red"), 0, 255, 0, 1);
    private final NumberSetting gridGreen = new NumberSetting(EncryptedString.of("Grid Green"), 0, 255, 0, 1);
    private final NumberSetting gridBlue = new NumberSetting(EncryptedString.of("Grid Blue"), 0, 255, 0, 1);

    private final int[][] regionData = {{82, 5}, {100, 3}, {101, 3}, {102, 3}, {103, 2}, {104, 2}, {105, 2}, {106, 2}, {91, 2}, {83, 5}, {44, 3}, {75, 3}, {42, 3}, {41, 2}, {40, 2}, {39, 2}, {38, 2}, {92, 2}, {84, 5}, {45, 3}, {14, 3}, {13, 3}, {12, 2}, {11, 2}, {10, 2}, {37, 2}, {93, 2}, {85, 5}, {46, 5}, {74, 5}, {3, 3}, {2, 2}, {1, 2}, {25, 2}, {36, 2}, {94, 2}, {86, 4}, {47, 4}, {72, 4}, {71, 4}, {5, 2}, {4, 2}, {24, 2}, {35, 2}, {95, 2}, {87, 4}, {51, 1}, {17, 1}, {9, 0}, {8, 0}, {7, 0}, {23, 0}, {34, 0}, {96, 2}, {88, 4}, {54, 1}, {18, 1}, {61, 0}, {62, 0}, {21, 0}, {22, 0}, {33, 0}, {97, 0}, {89, 0}, {26, 1}, {27, 0}, {28, 0}, {29, 0}, {30, 0}, {59, 0}, {32, 0}, {98, 0}, {90, 0}, {107, 1}, {108, 1}, {109, 1}, {110, 1}, {111, 1}, {112, 1}, {113, 1}, {99, 0}};
    private final Color[] regionColors = {new Color(159, 206, 99), new Color(0, 166, 99), new Color(79, 173, 234), new Color(47, 110, 186), new Color(245, 194, 66), new Color(252, 136, 3)};
    private final String[] regionNames = {"EU Central", "EU West", "NA East", "NA West", "Asia", "Oceania"};

    public RegionMap() { super(EncryptedString.of("Region Map"), EncryptedString.of("Shows DonutSMP region map with your position"), -1, Category.RENDER); addSettings(x, y, scale, textScale, showCoords, showRegionNames, showGrid, bgRed, bgGreen, bgBlue, bgAlpha, arrowRed, arrowGreen, arrowBlue, gridRed, gridGreen, gridBlue); }

    @EventListener
    public void onRender2D(final Render2DEvent event) {
        if (mc.player == null) return;
        DrawContext context = event.context;
        MatrixStack matrices = context.getMatrices();
        int mapX = x.getIntValue(), mapY = y.getIntValue(), cellSize = scale.getIntValue();
        int mapWidth = 9 * cellSize, mapHeight = 9 * cellSize;
        Color bgColor = new Color(bgRed.getIntValue(), bgGreen.getIntValue(), bgBlue.getIntValue(), bgAlpha.getIntValue());
        RenderUtils.renderRoundedQuad(matrices, bgColor, mapX, mapY, mapX + mapWidth, mapY + mapHeight, 0, 15);
        for (int row = 0; row < 9; row++) for (int col = 0; col < 9; col++) {
            Color rawRegionColor = regionColors[regionData[row * 9 + col][1]];
            Color regionColor = new Color(rawRegionColor.getRed(), rawRegionColor.getGreen(), rawRegionColor.getBlue(), bgAlpha.getIntValue());
            RenderUtils.renderRoundedQuad(matrices, regionColor, mapX + col * cellSize + 1, mapY + row * cellSize + 1, mapX + (col + 1) * cellSize, mapY + (row + 1) * cellSize, 0, 15);
        }
        if (showGrid.getValue()) {
            Color gridCol = new Color(gridRed.getIntValue(), gridGreen.getIntValue(), gridBlue.getIntValue());
            for (int i = 1; i < 9; i++) {
                RenderUtils.renderRoundedQuad(matrices, gridCol, mapX + i * cellSize, mapY, mapX + i * cellSize + 1, mapY + mapHeight, 0, 15);
                RenderUtils.renderRoundedQuad(matrices, gridCol, mapX, mapY + i * cellSize, mapX + mapWidth, mapY + i * cellSize + 1, 0, 15);
            }
        }
        for (int row = 0; row < 9; row++) for (int col = 0; col < 9; col++) {
            String text = String.valueOf(regionData[row * 9 + col][0]);
            float scaledWidth = TextRenderer.getWidth(text) * (float) textScale.getValue();
            float scaledHeight = 8 * (float) textScale.getValue();
            float textX = mapX + col * cellSize + (cellSize - scaledWidth) / 2f;
            float textY = mapY + row * cellSize + (cellSize - scaledHeight) / 2f;
            matrices.push();
            matrices.translate(textX, textY, 0);
            matrices.scale((float) textScale.getValue(), (float) textScale.getValue(), 1.0f);
            TextRenderer.drawString(text, context, 0, 0, Color.WHITE.getRGB());
            matrices.pop();
        }
        double pX = mc.player.getX(), pZ = mc.player.getZ();
        int gridX = (int) ((pX + 225000.0) / 50000.0), gridZ = (int) ((pZ + 225000.0) / 50000.0);
        if (gridX >= 0 && gridX < 9 && gridZ >= 0 && gridZ < 9) {
            int arrowX = mapX + (int) ((((pX + 225000.0) % 225000.0) / 225000.0) * mapWidth);
            int arrowY = mapY + (int) ((((pZ + 225000.0) % 225000.0) / 225000.0) * mapHeight);
            drawPlayerArrow(matrices, arrowX, arrowY, mc.player.getYaw());
        }
        int infoY = mapY + mapHeight + 5;
        if (showCoords.getValue()) {
            TextRenderer.drawString(String.format("X: %d, Z: %d", (int) pX, (int) pZ), context, mapX, infoY, Color.WHITE.getRGB());
            infoY += 15;
            int region = getCurrentRegion(pX, pZ);
            if (region != -1) {
                TextRenderer.drawString("Region: " + region, context, mapX, infoY, Color.WHITE.getRGB());
                infoY += 15;
            }
        }
        if (showRegionNames.getValue()) {
            infoY += 5;
            for (int i = 0; i < regionNames.length; i++) {
                RenderUtils.renderRoundedQuad(matrices, regionColors[i], mapX, infoY + i * 15, mapX + 12, infoY + i * 15 + 12, 2, 15);
                TextRenderer.drawString(regionNames[i], context, mapX + 17, infoY + i * 15 + 2, Color.WHITE.getRGB());
            }
        }
    }

    private void drawPlayerArrow(MatrixStack matrices, int centerX, int centerY, float yaw) {
        matrices.push();
        matrices.translate(centerX, centerY, 0);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(yaw - 90));
        Color color = new Color(arrowRed.getIntValue(), arrowGreen.getIntValue(), arrowBlue.getIntValue());
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        int size = 6;
        buffer.vertex(matrices.peek().getPositionMatrix(), 0, -size, 0).color(color.getRed(), color.getGreen(), color.getBlue(), 255);
        buffer.vertex(matrices.peek().getPositionMatrix(), -size, size, 0).color(color.getRed(), color.getGreen(), color.getBlue(), 255);
        buffer.vertex(matrices.peek().getPositionMatrix(), size, size, 0).color(color.getRed(), color.getGreen(), color.getBlue(), 255);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private int getCurrentRegion(double x, double z) {
        int gridX = (int) ((x + 225000.0) / 50000.0), gridZ = (int) ((z + 225000.0) / 50000.0);
        return (gridX >= 0 && gridX < 9 && gridZ >= 0 && gridZ < 9) ? regionData[gridZ * 9 + gridX][0] : -1;
    }
}