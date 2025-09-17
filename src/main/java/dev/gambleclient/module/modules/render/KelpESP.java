package dev.gambleclient.module.modules.render;

import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.Render3DEvent;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.RenderUtils;
import dev.gambleclient.utils.BlockUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.KelpBlock;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.*;

/**
 * KelpChunkESP
 * Highlights chunks that contain a required number of fully grown kelp columns.
 * Uses the SAME render approach as StorageESP (camera transform + RenderUtils).
 */
public final class KelpESP extends Module {

    /* -------- Detection Settings -------- */
    private final BooleanSetting requireAge = new BooleanSetting(EncryptedString.of("RequireAge25"), true)
            .setDescription(EncryptedString.of("Column top kelp must have AGE >= 25"));
    private final BooleanSetting useMinHeight = new BooleanSetting(EncryptedString.of("UseHeight"), true);
    private final NumberSetting minHeight = new NumberSetting(EncryptedString.of("MinHeight"),
            1.0, 64.0, 12.0, 1.0);

    private final NumberSetting neededColumns = (NumberSetting) new NumberSetting(EncryptedString.of("NeededColumns"),
            1.0, 64.0, 2.0, 1.0)
            .setDescription(EncryptedString.of("Kelp columns required to mark chunk"));

    /* -------- Scan Range / Timing -------- */
    private final NumberSetting chunkRadius = new NumberSetting(EncryptedString.of("ChunkRadius"),
            1.0, 24.0, 8.0, 1.0);
    private final NumberSetting minYScan = new NumberSetting(EncryptedString.of("MinYScan"),
            -64.0, 320.0, 30.0, 1.0);
    private final NumberSetting maxYScan = new NumberSetting(EncryptedString.of("MaxYScan"),
            -64.0, 320.0, 80.0, 1.0);
    private final NumberSetting rescanDelay = new NumberSetting(EncryptedString.of("RescanDelayTicks"),
            20.0, 2000.0, 300.0, 20.0);
    private final NumberSetting chunksPerTick = new NumberSetting(EncryptedString.of("ChunksPerTick"),
            1.0, 200.0, 18.0, 1.0);

    /* -------- Rendering -------- */
    private final NumberSetting plateY = new NumberSetting(EncryptedString.of("PlateY"),
            -64.0, 320.0, 62.0, 1.0);
    private final NumberSetting plateThickness = new NumberSetting(EncryptedString.of("PlateThickness"),
            0.01, 2.0, 0.12, 0.01);
    private final BooleanSetting fill = new BooleanSetting(EncryptedString.of("Fill"), true);
    private final BooleanSetting outline = new BooleanSetting(EncryptedString.of("Outline"), true);
    private final BooleanSetting tracers = new BooleanSetting(EncryptedString.of("Tracers"), true);

    private final NumberSetting baseR = new NumberSetting(EncryptedString.of("BaseR"), 0.0, 255.0, 0.0, 1.0);
    private final NumberSetting baseG = new NumberSetting(EncryptedString.of("BaseG"), 0.0, 255.0, 200.0, 1.0);
    private final NumberSetting baseB = new NumberSetting(EncryptedString.of("BaseB"), 0.0, 255.0, 120.0, 1.0);
    private final NumberSetting baseA = new NumberSetting(EncryptedString.of("BaseA"), 0.0, 255.0, 90.0, 1.0);

    private final BooleanSetting gradient = new BooleanSetting(EncryptedString.of("Gradient"), true)
            .setDescription(EncryptedString.of("Brighten color as columns approach requirement"));
    private final NumberSetting maxBoost = new NumberSetting(EncryptedString.of("MaxBoost"), 0.0, 255.0, 110.0, 5.0);

    /* -------- Notifications -------- */
    private final BooleanSetting notifyChat = new BooleanSetting(EncryptedString.of("ChatNotify"), true);
    private final BooleanSetting notifySound = new BooleanSetting(EncryptedString.of("Sound"), true);

    public KelpESP() {
        super(
                EncryptedString.of("KelpChunkESP"),
                EncryptedString.of("Highlights chunks with fully grown kelp"),
                -1,
                Category.RENDER
        );
        addSettings(
                requireAge, useMinHeight, minHeight,
                neededColumns,
                chunkRadius, minYScan, maxYScan, rescanDelay, chunksPerTick,
                plateY, plateThickness, fill, outline, tracers,
                baseR, baseG, baseB, baseA,
                gradient, maxBoost,
                notifyChat, notifySound
        );
    }

    /* -------- Internal Data -------- */
    private static final IntProperty AGE_PROP = KelpBlock.AGE;
    private long tickCounter;

    private static final class ChunkInfo {
        int columnCount;
        long lastScanTick;
        boolean notified;
    }

    private final Map<Long, ChunkInfo> chunkCache = new HashMap<>();
    private final Deque<ChunkPos> workQueue = new ArrayDeque<>();

    @Override
    public void onEnable() {
        super.onEnable();
        chunkCache.clear();
        workQueue.clear();
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        chunkCache.clear();
        workQueue.clear();
        super.onDisable();
    }

    /* -------- Tick (Scanning) -------- */
    @EventListener
    private void onTick(TickEvent e) {
        if (mc == null || mc.world == null || mc.player == null) return;
        tickCounter++;

        if (tickCounter % 25 == 0 || workQueue.isEmpty()) {
            rebuildWorkQueue();
        }

        int perTick = Math.max(1, chunksPerTick.getIntValue());
        for (int i = 0; i < perTick && !workQueue.isEmpty(); i++) {
            ChunkPos cp = workQueue.pollFirst();
            if (cp != null) {
                scanChunk(cp);
            }
        }
    }

    private void rebuildWorkQueue() {
        workQueue.clear();
        if (mc.player == null) return;
        ChunkPos center = new ChunkPos(mc.player.getBlockPos());
        int radius = chunkRadius.getIntValue();
        long delay = rescanDelay.getIntValue();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                long key = cp.toLong();
                ChunkInfo info = chunkCache.get(key);
                if (info == null || (tickCounter - info.lastScanTick) >= delay) {
                    workQueue.addLast(cp);
                }
            }
        }
    }

    private void scanChunk(ChunkPos cp) {
        if (mc.world == null) return;
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
        if (chunk == null) return;

        int minYv = (int) Math.min(minYScan.getValue(), maxYScan.getValue());
        int maxYv = (int) Math.max(minYScan.getValue(), maxYScan.getValue());
        minYv = Math.max(minYv, chunk.getBottomY());
        maxYv = Math.min(maxYv, chunk.getBottomY() + chunk.getHeight() - 1);

        int needed = neededColumns.getIntValue();
        int found = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();

        // For each (x,z) inside chunk
        for (int lx = 0; lx < 16 && found < needed; lx++) {
            int wx = cp.getStartX() + lx;
            for (int lz = 0; lz < 16 && found < needed; lz++) {
                int wz = cp.getStartZ() + lz;

                boolean countedThisXZ = false;
                for (int y = minYv; y <= maxYv && !countedThisXZ; y++) {
                    m.set(wx, y, wz);
                    BlockState state = mc.world.getBlockState(m);
                    if (!(state.getBlock() instanceof KelpBlock)) continue;

                    // Descend to base
                    BlockPos.Mutable base = new BlockPos.Mutable().set(m);
                    while (base.getY() > minYv) {
                        BlockState below = mc.world.getBlockState(base.down());
                        if (!(below.getBlock() instanceof KelpBlock)) break;
                        base.move(0, -1, 0);
                    }
                    if (!(mc.world.getBlockState(base).getBlock() instanceof KelpBlock)) continue;

                    // Ascend to measure height
                    int height = 0;
                    BlockState topState = null;
                    BlockPos.Mutable climb = new BlockPos.Mutable().set(base);
                    while (climb.getY() <= maxYv) {
                        BlockState cs = mc.world.getBlockState(climb);
                        if (!(cs.getBlock() instanceof KelpBlock)) break;
                        height++;
                        topState = cs;
                        if (height > 128) break;
                        climb.move(0, 1, 0);
                    }
                    if (height == 0) continue;

                    if (columnQualifies(topState, height)) {
                        found++;
                        countedThisXZ = true;
                    }
                }
            }
        }

        ChunkInfo info = chunkCache.computeIfAbsent(cp.toLong(), k -> new ChunkInfo());
        info.columnCount = found;
        info.lastScanTick = tickCounter;

        if (found >= needed) {
            if (!info.notified) {
                if (notifyChat.getValue() && mc.player != null) {
                    mc.player.sendMessage(Text.literal("[KelpChunkESP] Chunk " + cp.x + "," + cp.z + " kelp=" + found), false);
                }
                if (notifySound.getValue() && mc.player != null) {
                    try {
                        mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.0f, 1.0f);
                    } catch (Throwable ignored) {}
                }
                info.notified = true;
            }
        } else {
            info.notified = false;
        }
    }

    private boolean columnQualifies(BlockState topState, int height) {
        boolean passAge = !requireAge.getValue();
        if (requireAge.getValue()) {
            if (topState != null && topState.contains(AGE_PROP)) {
                passAge = topState.get(AGE_PROP) >= 25;
            } else {
                passAge = false;
            }
        }
        boolean passHeight = !useMinHeight.getValue() || height >= minHeight.getIntValue();
        return passAge && passHeight;
    }

    /* -------- Rendering -------- */
    @EventListener
    private void onRender3D(Render3DEvent event) {
        if (mc == null || mc.world == null || mc.player == null) return;
        if (chunkCache.isEmpty()) return;

        Camera cam = RenderUtils.getCamera();
        MatrixStack matrices = event.matrixStack;

        if (cam != null) {
            Vec3d camPos = RenderUtils.getCameraPos();
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cam.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(cam.getYaw() + 180.0f));
            matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        }

        int needed = neededColumns.getIntValue();
        double y = plateY.getValue();
        double h = plateThickness.getValue();

        for (Map.Entry<Long, ChunkInfo> entry : chunkCache.entrySet()) {
            ChunkInfo info = entry.getValue();
            if (info.columnCount < needed) continue;

            ChunkPos cp = new ChunkPos(entry.getKey());
            int startX = cp.getStartX();
            int startZ = cp.getStartZ();

            // Base color
            int r = (int) baseR.getValue();
            int g = (int) baseG.getValue();
            int b = (int) baseB.getValue();
            int a = (int) baseA.getValue();

            if (gradient.getValue()) {
                double ratio = Math.min(1.0, info.columnCount / (double) needed);
                int boost = (int) Math.min(255, ratio * maxBoost.getValue());
                r = clamp(r + boost);
                g = clamp(g + boost / 2);
                b = clamp(b + boost / 3);
            }

            Color fillColor = new Color(r, g, b, a);
            Color lineColor = new Color(r, g, b, 255);

            if (fill.getValue()) {
                // Thin plate (slab) over chunk
                RenderUtils.renderFilledBox(matrices,
                        (float) startX, (float) y, (float) startZ,
                        (float) (startX + 16), (float) (y + h), (float) (startZ + 16),
                        fillColor);

            }

            if (outline.getValue()) {
                drawChunkOutline(matrices, startX, startZ, y, h, lineColor);
            }

            if (tracers.getValue()) {
                Vec3d from = mc.crosshairTarget != null ? mc.crosshairTarget.getPos() : mc.player.getEyePos();
                Vec3d to = new Vec3d(startX + 8.0, y + h / 2.0, startZ + 8.0);
                RenderUtils.renderLine(matrices, lineColor, from, to);
            }
        }

        if (cam != null) {
            matrices.pop();
        }
    }

    private void drawChunkOutline(MatrixStack matrices, int startX, int startZ, double y, double h, Color c) {
        double x1 = startX;
        double z1 = startZ;
        double x2 = startX + 16;
        double z2 = startZ + 16;
        double y2 = y + h;

        Vec3d v000 = new Vec3d(x1, y, z1);
        Vec3d v001 = new Vec3d(x1, y, z2);
        Vec3d v010 = new Vec3d(x1, y2, z1);
        Vec3d v011 = new Vec3d(x1, y2, z2);
        Vec3d v100 = new Vec3d(x2, y, z1);
        Vec3d v101 = new Vec3d(x2, y, z2);
        Vec3d v110 = new Vec3d(x2, y2, z1);
        Vec3d v111 = new Vec3d(x2, y2, z2);

        // Bottom
        RenderUtils.renderLine(matrices, c, v000, v100);
        RenderUtils.renderLine(matrices, c, v100, v101);
        RenderUtils.renderLine(matrices, c, v101, v001);
        RenderUtils.renderLine(matrices, c, v001, v000);
        // Top
        RenderUtils.renderLine(matrices, c, v010, v110);
        RenderUtils.renderLine(matrices, c, v110, v111);
        RenderUtils.renderLine(matrices, c, v111, v011);
        RenderUtils.renderLine(matrices, c, v011, v010);
        // Vertical
        RenderUtils.renderLine(matrices, c, v000, v010);
        RenderUtils.renderLine(matrices, c, v100, v110);
        RenderUtils.renderLine(matrices, c, v101, v111);
        RenderUtils.renderLine(matrices, c, v001, v011);
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /* -------- External Hooks (optional integration) -------- */
    public void handleBlockUpdate(BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos);
        ChunkInfo info = chunkCache.get(cp.toLong());
        if (info != null) info.lastScanTick = 0;
        if (!workQueue.contains(cp)) workQueue.addLast(cp);
    }

    public void handleChunkLoad(ChunkPos cp) {
        ChunkInfo info = chunkCache.get(cp.toLong());
        if (info != null) info.lastScanTick = 0;
        if (!workQueue.contains(cp)) workQueue.addLast(cp);
    }

    public void handleChunkUnload(ChunkPos cp) {
        chunkCache.remove(cp.toLong());
        workQueue.remove(cp);
    }

    public int getColumnCount(ChunkPos cp) {
        ChunkInfo info = chunkCache.get(cp.toLong());
        return info == null ? 0 : info.columnCount;
    }
}