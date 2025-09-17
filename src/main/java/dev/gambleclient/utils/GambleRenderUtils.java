package dev.gambleclient.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

import static dev.gambleclient.Gamble.mc;

/**
 * A new, corrected, and robust rendering utility for Gamble Client.
 * This class guarantees that 3D shapes are rendered correctly in the world
 * by properly handling all transformations and render states.
 */
public final class GambleRenderUtils {

    /**
     * Draws a filled 3D box in the world. This method correctly handles all
     * necessary camera transformations.
     *
     * @param matrices The MatrixStack from the render event.
     * @param box      The world-space Box to draw.
     * @param color    The fill color.
     */
    public static void drawBox(MatrixStack matrices, Box box, Color color) {
        // 1. Get the camera's position
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        // 2. Create a new box with coordinates relative to the camera
        Box relativeBox = box.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // 3. Setup rendering system
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // 4. Draw the RELATIVE box
        WorldRenderer.drawBox(matrices, buffer,
                (float) relativeBox.minX, (float) relativeBox.minY, (float) relativeBox.minZ,
                (float) relativeBox.maxX, (float) relativeBox.maxY, (float) relativeBox.maxZ,
                color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);

        // 5. Finalize and clean up
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    /**
     * Draws the outline of a 3D box in the world. This method correctly handles
     * all necessary camera transformations.
     *
     * @param matrices The MatrixStack from the render event.
     * @param box      The world-space Box to draw.
     * @param color    The outline color.
     */
    public static void drawOutline(MatrixStack matrices, Box box, Color color) {
        // 1. Get the camera's position
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        // 2. Create a new box with coordinates relative to the camera
        Box relativeBox = box.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // 3. Setup rendering system
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // 4. Draw the RELATIVE box outline
        WorldRenderer.drawBox(matrices, buffer, relativeBox,
                color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1.0f);

        // 5. Finalize and clean up
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }
}