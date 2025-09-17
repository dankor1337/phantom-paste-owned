package dev.gambleclient.module.modules.donut;

import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.Render3DEvent;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.module.setting.ColorSetting;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.RenderUtils;
import dev.gambleclient.utils.Utils;

import net.minecraft.block.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TraderLlamaEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.sound.SoundCategory;

import java.awt.*;
import java.util.concurrent.*;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;

public class ChunkFinder extends Module {

    // Visual Settings (kept as options)
    private final BooleanSetting tracers = new BooleanSetting(EncryptedString.of("Tracers"), true).setDescription(EncryptedString.of("Draw lines to flagged chunks"));
    private final BooleanSetting fill = new BooleanSetting(EncryptedString.of("Fill"), true).setDescription(EncryptedString.of("Fill chunk highlights"));
    private final BooleanSetting outline = new BooleanSetting(EncryptedString.of("Outline"), true).setDescription(EncryptedString.of("Draw chunk outlines"));

    // Block highlighting settings (kept as option)
    private final BooleanSetting highlightBlocks = new BooleanSetting(EncryptedString.of("HighlightBlocks"), true).setDescription(EncryptedString.of("Highlight individual flagged blocks"));

    // New Trader Detection Settings
    private final BooleanSetting detectTraders = new BooleanSetting(EncryptedString.of("BetterChunk"), true).setDescription(EncryptedString.of("Flag chunks with Wandering Traders or Trader Llamas"));
    private final ColorSetting traderChunkColor = new ColorSetting(EncryptedString.of("BetterChunkColor"), new Color(255, 165, 0, 120)).setDescription(EncryptedString.of("Color for trader chunks"));
    private final NumberSetting fillAlpha = new NumberSetting(EncryptedString.of("FillAlpha"), 0.0, 255.0, 120.0, 5.0);

    // New Hole ESP Settings
    private final BooleanSetting holeESP = new BooleanSetting(EncryptedString.of("HoleESP"), false).setDescription(EncryptedString.of("Enable hole detection and rendering"));
    private final BooleanSetting detect1x1Holes = new BooleanSetting(EncryptedString.of("Detect1x1Holes"), true).setDescription(EncryptedString.of("Detect 1x1 holes"));
    private final BooleanSetting detect3x1Holes = new BooleanSetting(EncryptedString.of("Detect3x1Holes"), true).setDescription(EncryptedString.of("Detect 3x1 holes"));
    private final NumberSetting minHoleDepth = new NumberSetting(EncryptedString.of("MinHoleDepth"), 4, 1, 20, 1);
    private final ColorSetting hole1x1Color = new ColorSetting(EncryptedString.of("1x1HoleColor"), new Color(255, 255, 255, 100)).setDescription(EncryptedString.of("Color for 1x1 holes"));
    private final ColorSetting hole3x1Color = new ColorSetting(EncryptedString.of("3x1HoleColor"), new Color(255, 255, 255, 100)).setDescription(EncryptedString.of("Color for 3x1 holes"));
    private final BooleanSetting holeOutline = new BooleanSetting(EncryptedString.of("HoleOutline"), true).setDescription(EncryptedString.of("Draw hole outlines"));
    private final BooleanSetting holeFill = new BooleanSetting(EncryptedString.of("HoleFill"), true).setDescription(EncryptedString.of("Fill holes"));

    // Fixed detection values (no longer user configurable)
    private static final int MIN_Y = 8;
    private static final int MAX_UNDERGROUND_Y = 60;
    private static final int HIGHLIGHT_Y = 60;

    // Fixed notification values (always enabled)
    private static final boolean PLAY_SOUND = true;
    private static final boolean CHAT_NOTIFICATION = true;
    private static final boolean FIND_ROTATED_DEEPSLATE = true;

    // Thread-safe storage
    private final Set<ChunkPos> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> notifiedChunks = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<ChunkPos, Set<BlockPos>> flaggedBlocks = new ConcurrentHashMap<>();

    // Trader chunk storage
    private final Set<ChunkPos> traderChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> notifiedTraderChunks = ConcurrentHashMap.newKeySet();

    // Hole storage
    private final Set<Box> holes1x1 = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Box> holes3x1 = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkPos> holeScannedChunks = ConcurrentHashMap.newKeySet();

    // Threading
    private ExecutorService scannerThread;
    private Future<?> currentScanTask;
    private Future<?> currentHoleScanTask;
    private Future<?> currentTraderScanTask;
    private volatile boolean shouldStop = false;

    // Add these fields to your ChunkFinder class
    private Set<Integer> notifiedTraders = new HashSet<>();

    // Performance tracking
    private long lastScanTime = 0;
    private long lastCleanupTime = 0;
    private long lastHoleScanTime = 0;
    private long lastTraderScanTime = 0;
    private static final long SCAN_COOLDOWN = 500;
    private static final long CLEANUP_INTERVAL = 5000;
    private static final long HOLE_SCAN_COOLDOWN = 1000;
    private static final long TRADER_SCAN_COOLDOWN = 2000;

    public ChunkFinder() {
        super(EncryptedString.of("ChunkFinder"), EncryptedString.of("Finds Sus Chunks, Holes, and Traders"), -1, Category.DONUT);

        // Add all settings including trader detection
        addSettings(this.tracers, this.fill, this.outline, this.highlightBlocks,
                this.detectTraders, this.traderChunkColor,
                this.holeESP, this.detect1x1Holes, this.detect3x1Holes, this.minHoleDepth,
                this.hole1x1Color, this.hole3x1Color, this.holeOutline, this.holeFill);
    }

    @Override
    public void onEnable() {
        flaggedChunks.clear();
        scannedChunks.clear();
        notifiedChunks.clear();
        flaggedBlocks.clear();
        traderChunks.clear();
        notifiedTraderChunks.clear();
        holes1x1.clear();
        holes3x1.clear();
        holeScannedChunks.clear();
        shouldStop = false;

        // Create dedicated scanner thread
        scannerThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ChunkScanner");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        scheduleChunkScan();
        if (holeESP.getValue()) {
            scheduleHoleScan();
        }
        if (detectTraders.getValue()) {
            scheduleTraderScan();
        }
        System.out.println("§b🔍 [ChunkFinder] ✨ Starting chunk scanning...");
    }

    @Override
    public void onDisable() {
        shouldStop = true;

        if (currentScanTask != null && !currentScanTask.isDone()) {
            currentScanTask.cancel(true);
        }

        if (currentHoleScanTask != null && !currentHoleScanTask.isDone()) {
            currentHoleScanTask.cancel(true);
        }

        if (currentTraderScanTask != null && !currentTraderScanTask.isDone()) {
            currentTraderScanTask.cancel(true);
        }

        if (scannerThread != null && !scannerThread.isShutdown()) {
            scannerThread.shutdownNow();
        }

        flaggedChunks.clear();
        scannedChunks.clear();
        notifiedChunks.clear();
        flaggedBlocks.clear();
        traderChunks.clear();
        notifiedTraderChunks.clear();
        holes1x1.clear();
        holes3x1.clear();
        holeScannedChunks.clear();
        System.out.println("§b🔍 [ChunkFinder] ✨ Chunk scanning stopped");
    }

    @EventListener
    public void onTick(TickEvent event) {
        if (mc.world == null || mc.player == null) return;

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastScanTime > SCAN_COOLDOWN) {
            scheduleChunkScan();
            lastScanTime = currentTime;
        }

        if (holeESP.getValue() && currentTime - lastHoleScanTime > HOLE_SCAN_COOLDOWN) {
            scheduleHoleScan();
            lastHoleScanTime = currentTime;
        }

        if (detectTraders.getValue() && currentTime - lastTraderScanTime > TRADER_SCAN_COOLDOWN) {
            scheduleTraderScan();
            lastTraderScanTime = currentTime;
        }

        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL) {
            cleanupDistantChunks();
            cleanupDistantHoles();
            cleanupDistantTraderChunks();
            lastCleanupTime = currentTime;
        }
    }

    private void scheduleTraderScan() {
        if (shouldStop || scannerThread == null || scannerThread.isShutdown()) return;

        if (currentTraderScanTask != null && !currentTraderScanTask.isDone()) {
            currentTraderScanTask.cancel(false);
        }

        currentTraderScanTask = scannerThread.submit(this::scanTradersBackground);
    }


    private void scanTradersBackground() {
        if (shouldStop || mc.world == null || mc.player == null) return;

        try {
            int entityCount = 0;
            // Scan all entities in the world instead of just loaded chunks
            for (Entity entity : mc.world.getEntities()) {
                if (shouldStop || entity == null) continue;

                entityCount++; // Count entities as we iterate

                // Check if it's a Wandering Trader or Trader Llama
                if (entity instanceof WanderingTraderEntity || entity instanceof TraderLlamaEntity) {
                    Vec3d pos = entity.getPos();
                    ChunkPos chunkPos = new ChunkPos((int) Math.floor(pos.x / 16), (int) Math.floor(pos.z / 16));

                    // Use entity ID to track individual traders
                    int entityId = entity.getId();
                    boolean hasNotifiedThisTrader = notifiedTraders.contains(entityId);

                    boolean wasAlreadyFlagged = traderChunks.contains(chunkPos);
                    traderChunks.add(chunkPos);

                    // Only notify if we haven't notified about this specific trader before
                    if (!hasNotifiedThisTrader) {
                        notifyTraderChunkFound(chunkPos);
                        notifiedTraders.add(entityId); // Track this specific trader
                        notifiedTraderChunks.add(chunkPos);
                    }
                }

                // Small delay to prevent excessive CPU usage if there are many entities
                if (entityCount > 0 && entityCount % 100 == 0) {
                    Thread.sleep(5);
                }
            }

            // Clean up trader chunks that no longer have traders
            Set<ChunkPos> chunksToRemove = new HashSet<>();
            Set<Integer> tradersToRemove = new HashSet<>();

            for (ChunkPos chunkPos : traderChunks) {
                if (!hasTraderInChunk(chunkPos)) {
                    chunksToRemove.add(chunkPos);
                }
            }

            // Clean up notified traders that no longer exist
            for (Integer traderId : notifiedTraders) {
                boolean traderExists = false;
                for (Entity entity : mc.world.getEntities()) {
                    if (entity != null && entity.getId() == traderId &&
                            (entity instanceof WanderingTraderEntity || entity instanceof TraderLlamaEntity)) {
                        traderExists = true;
                        break;
                    }
                }
                if (!traderExists) {
                    tradersToRemove.add(traderId);
                }
            }

            for (ChunkPos chunkPos : chunksToRemove) {
                traderChunks.remove(chunkPos);
                notifiedTraderChunks.remove(chunkPos);
            }

            for (Integer traderId : tradersToRemove) {
                notifiedTraders.remove(traderId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Error in ChunkFinder trader scanning: " + e.getMessage());
        }
    }
    private boolean hasTraderInChunk(ChunkPos chunkPos) {
        if (shouldStop || mc.world == null) return false;

        try {
            int startX = chunkPos.x * 16;
            int startZ = chunkPos.z * 16;
            int endX = startX + 16;
            int endZ = startZ + 16;

            // Get all entities in the world
            for (Entity entity : mc.world.getEntities()) {
                if (entity == null) continue;

                Vec3d pos = entity.getPos();
                int entityX = (int) Math.floor(pos.x);
                int entityZ = (int) Math.floor(pos.z);

                // Check if entity is within chunk bounds
                if (entityX >= startX && entityX < endX && entityZ >= startZ && entityZ < endZ) {
                    // Check if it's a Wandering Trader or Trader Llama
                    if (entity instanceof WanderingTraderEntity || entity instanceof TraderLlamaEntity) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors and continue
        }

        return false;
    }

    private void notifyTraderChunkFound(ChunkPos chunkPos) {
        mc.execute(() -> {
            try {
                if (CHAT_NOTIFICATION && mc.player != null) {
                    // Convert chunk coordinates to actual world coordinates
                    int worldX = chunkPos.x * 16;
                    int worldZ = chunkPos.z * 16;
                    String message = "§e🛒 [ChunkFinder] 📦 Trader Chunk Found: 📍 X: " + worldX + " Z: " + worldZ;
                    mc.player.sendMessage(Text.literal(message), false);
                }

                if (PLAY_SOUND && mc.player != null) {
                    // Changed from BLOCK_ANVIL_FALL to ENTITY_ENDER_DRAGON_GROWL
                    mc.player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                }

            } catch (Exception e) {
                // Ignore notification errors
            }
        });
    }

    private void cleanupDistantTraderChunks() {
        if (mc.player == null) return;

        int viewDist = mc.options.getViewDistance().getValue();
        int playerChunkX = (int) mc.player.getX() / 16;
        int playerChunkZ = (int) mc.player.getZ() / 16;

        traderChunks.removeIf(chunkPos -> {
            int dx = Math.abs(chunkPos.x - playerChunkX);
            int dz = Math.abs(chunkPos.z - playerChunkZ);
            return dx > viewDist || dz > viewDist;
        });

        notifiedTraderChunks.removeIf(chunkPos -> {
            int dx = Math.abs(chunkPos.x - playerChunkX);
            int dz = Math.abs(chunkPos.z - playerChunkZ);
            return dx > viewDist || dz > viewDist;
        });
    }

    private void scheduleChunkScan() {
        if (shouldStop || scannerThread == null || scannerThread.isShutdown()) return;

        if (currentScanTask != null && !currentScanTask.isDone()) {
            currentScanTask.cancel(false);
        }

        currentScanTask = scannerThread.submit(this::scanChunksBackground);
    }

    private void scheduleHoleScan() {
        if (shouldStop || scannerThread == null || scannerThread.isShutdown()) return;

        if (currentHoleScanTask != null && !currentHoleScanTask.isDone()) {
            currentHoleScanTask.cancel(false);
        }

        currentHoleScanTask = scannerThread.submit(this::scanHolesBackground);
    }

    private void scanHolesBackground() {
        if (shouldStop || mc.world == null || mc.player == null) return;

        try {
            List<WorldChunk> loadedChunks = getLoadedChunks();

            for (WorldChunk chunk : loadedChunks) {
                if (shouldStop || chunk == null || chunk.isEmpty()) continue;

                ChunkPos chunkPos = chunk.getPos();
                if (holeScannedChunks.contains(chunkPos)) continue;

                scanChunkForHoles(chunk);
                holeScannedChunks.add(chunkPos);

                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Error in ChunkFinder hole scanning: " + e.getMessage());
        }
    }

    private void scanChunkForHoles(WorldChunk chunk) {
        if (shouldStop || chunk == null || chunk.isEmpty()) return;

        ChunkPos chunkPos = chunk.getPos();
        int xStart = chunkPos.getStartX();
        int zStart = chunkPos.getStartZ();
        int yMin = Math.max(chunk.getBottomY(), MIN_Y);
        int yMax = Math.min(chunk.getBottomY() + chunk.getHeight(), MAX_UNDERGROUND_Y);

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    if (shouldStop) return;
                    BlockPos pos = new BlockPos(x, y, z);

                    try {
                        // Check for 1x1 holes
                        if (detect1x1Holes.getValue()) {
                            checkHole1x1(pos);
                        }

                        // Check for 3x1 holes
                        if (detect3x1Holes.getValue()) {
                            checkHole3x1(pos);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void checkHole1x1(BlockPos pos) {
        if (isValidHoleSection(pos)) {
            BlockPos.Mutable currentPos = pos.mutableCopy();
            while (isValidHoleSection(currentPos)) {
                currentPos.move(Direction.UP);
            }
            if (currentPos.getY() - pos.getY() >= minHoleDepth.getValue()) {
                Box holeBox = new Box(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, currentPos.getY(), pos.getZ() + 1
                );
                if (!holes1x1.contains(holeBox) && holes1x1.stream().noneMatch(existingHole -> existingHole.intersects(holeBox))) {
                    holes1x1.add(holeBox);
                }
            }
        }
    }

    private void checkHole3x1(BlockPos pos) {
        // Check 3x1 hole in X direction (3 blocks east-west, 1 block north-south)
        if (isValid3x1HoleSectionX(pos)) {
            BlockPos.Mutable currentPos = pos.mutableCopy();
            while (isValid3x1HoleSectionX(currentPos)) {
                currentPos.move(Direction.UP);
            }
            if (currentPos.getY() - pos.getY() >= minHoleDepth.getValue()) {
                Box holeBox = new Box(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 3, currentPos.getY(), pos.getZ() + 1
                );
                if (!holes3x1.contains(holeBox) && holes3x1.stream().noneMatch(existingHole -> existingHole.intersects(holeBox))) {
                    holes3x1.add(holeBox);
                }
            }
        }

        // Check 3x1 hole in Z direction (1 block east-west, 3 blocks north-south)
        if (isValid3x1HoleSectionZ(pos)) {
            BlockPos.Mutable currentPos = pos.mutableCopy();
            while (isValid3x1HoleSectionZ(currentPos)) {
                currentPos.move(Direction.UP);
            }
            if (currentPos.getY() - pos.getY() >= minHoleDepth.getValue()) {
                Box holeBox = new Box(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, currentPos.getY(), pos.getZ() + 3
                );
                if (!holes3x1.contains(holeBox) && holes3x1.stream().noneMatch(existingHole -> existingHole.intersects(holeBox))) {
                    holes3x1.add(holeBox);
                }
            }
        }
    }

    private boolean isValidHoleSection(BlockPos pos) {
        return isAirBlock(pos) &&
                !isAirBlock(pos.north()) && !isAirBlock(pos.south()) &&
                !isAirBlock(pos.east()) && !isAirBlock(pos.west());
    }

    private boolean isValid3x1HoleSectionX(BlockPos pos) {
        // Check if this is part of a 3x1 hole in X direction
        return isAirBlock(pos) &&
                isAirBlock(pos.east()) &&
                isAirBlock(pos.east(2)) &&
                !isAirBlock(pos.north()) &&
                !isAirBlock(pos.south()) &&
                !isAirBlock(pos.east(3)) &&
                !isAirBlock(pos.west()) &&
                !isAirBlock(pos.east().north()) &&
                !isAirBlock(pos.east().south()) &&
                !isAirBlock(pos.east(2).north()) &&
                !isAirBlock(pos.east(2).south());
    }

    private boolean isValid3x1HoleSectionZ(BlockPos pos) {
        // Check if this is part of a 3x1 hole in Z direction
        return isAirBlock(pos) &&
                isAirBlock(pos.south()) &&
                isAirBlock(pos.south(2)) &&
                !isAirBlock(pos.east()) &&
                !isAirBlock(pos.west()) &&
                !isAirBlock(pos.south(3)) &&
                !isAirBlock(pos.north()) &&
                !isAirBlock(pos.south().east()) &&
                !isAirBlock(pos.south().west()) &&
                !isAirBlock(pos.south(2).east()) &&
                !isAirBlock(pos.south(2).west());
    }

    private boolean isAirBlock(BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getBlockState(pos).isAir();
    }

    private void cleanupDistantHoles() {
        if (mc.player == null) return;

        int viewDist = mc.options.getViewDistance().getValue();
        int playerChunkX = (int) mc.player.getX() / 16;
        int playerChunkZ = (int) mc.player.getZ() / 16;

        holes1x1.removeIf(holeBox -> {
            int holeChunkX = (int) Math.floor(holeBox.getCenter().getX()) / 16;
            int holeChunkZ = (int) Math.floor(holeBox.getCenter().getZ()) / 16;
            int dx = Math.abs(holeChunkX - playerChunkX);
            int dz = Math.abs(holeChunkZ - playerChunkZ);
            return dx > viewDist || dz > viewDist;
        });

        holes3x1.removeIf(holeBox -> {
            int holeChunkX = (int) Math.floor(holeBox.getCenter().getX()) / 16;
            int holeChunkZ = (int) Math.floor(holeBox.getCenter().getZ()) / 16;
            int dx = Math.abs(holeChunkX - playerChunkX);
            int dz = Math.abs(holeChunkZ - playerChunkZ);
            return dx > viewDist || dz > viewDist;
        });

        holeScannedChunks.removeIf(chunkPos -> {
            int dx = Math.abs(chunkPos.x - playerChunkX);
            int dz = Math.abs(chunkPos.z - playerChunkZ);
            return dx > viewDist || dz > viewDist;
        });
    }

    private void scanChunksBackground() {
        if (shouldStop || mc.world == null || mc.player == null) return;

        try {
            List<WorldChunk> loadedChunks = getLoadedChunks();

            for (WorldChunk chunk : loadedChunks) {
                if (shouldStop || chunk == null || chunk.isEmpty()) continue;

                ChunkPos chunkPos = chunk.getPos();
                if (scannedChunks.contains(chunkPos)) continue;

                boolean wasAlreadyFlagged = flaggedChunks.contains(chunkPos);
                Set<BlockPos> chunkFlaggedBlocks = scanChunkForCoveredOres(chunk);
                boolean shouldFlag = !chunkFlaggedBlocks.isEmpty();

                if (shouldFlag) {
                    flaggedChunks.add(chunkPos);
                    flaggedBlocks.put(chunkPos, chunkFlaggedBlocks);
                    if (!wasAlreadyFlagged && !notifiedChunks.contains(chunkPos)) {
                        notifyChunkFound(chunkPos);
                        notifiedChunks.add(chunkPos);
                    }
                } else {
                    flaggedChunks.remove(chunkPos);
                    notifiedChunks.remove(chunkPos);
                    flaggedBlocks.remove(chunkPos);
                }
                scannedChunks.add(chunkPos);

                Thread.sleep(5);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Error in ChunkFinder background scanning: " + e.getMessage());
        }
    }

    private void notifyChunkFound(ChunkPos chunkPos) {
        mc.execute(() -> {
            try {
                if (CHAT_NOTIFICATION && mc.player != null) {
                    // Convert chunk coordinates to actual world coordinates
                    int worldX = chunkPos.x * 16;
                    int worldZ = chunkPos.z * 16;
                    String message = "§b🔍 [ChunkFinder] ✨ Sus Chunk Found: 📍 X: " + worldX + " Z: " + worldZ;
                    mc.player.sendMessage(Text.literal(message), false);
                }

                if (PLAY_SOUND && mc.player != null) {
                    // Changed from BLOCK_ANVIL_FALL to ENTITY_ENDER_DRAGON_GROWL
                    mc.player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                }

            } catch (Exception e) {
                // Ignore notification errors
            }
        });
    }

    private Set<BlockPos> scanChunkForCoveredOres(WorldChunk chunk) {
        Set<BlockPos> foundBlocks = ConcurrentHashMap.newKeySet();
        if (shouldStop || chunk == null || chunk.isEmpty()) return foundBlocks;

        ChunkPos chunkPos = chunk.getPos();
        int xStart = chunkPos.getStartX();
        int zStart = chunkPos.getStartZ();
        int yMin = Math.max(chunk.getBottomY(), MIN_Y);
        int yMax = Math.min(chunk.getBottomY() + chunk.getHeight(), MAX_UNDERGROUND_Y);

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    if (shouldStop) return foundBlocks;
                    BlockPos pos = new BlockPos(x, y, z);
                    try {
                        BlockState state = chunk.getBlockState(pos);
                        Block block = state.getBlock();

                        // TUFF and DEEPSLATE Y-level detection with coverage
                        if ((block == Blocks.TUFF || block == Blocks.DEEPSLATE) && y > 8 && y < 60) {
                            if (isBlockCovered(chunk, pos) && isPositionUnderground(pos)) {
                                foundBlocks.add(pos);
                            }
                        }

                        // Normal target blocks (covered underground blocks)
                        if (isTargetBlock(state) && y >= MIN_Y && y <= MAX_UNDERGROUND_Y) {
                            if (isBlockCovered(chunk, pos) && isPositionUnderground(pos)) {
                                foundBlocks.add(pos);
                            }
                        }

                        // Rotated deepslate detection (always enabled)
                        if (FIND_ROTATED_DEEPSLATE && isRotatedDeepslate(state)) {
                            if (isBlockCovered(chunk, pos) && isPositionUnderground(pos)) {
                                foundBlocks.add(pos);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // New detection: Plugged tunnels
        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                int sameCount = 0;
                Block plugType = null;
                List<BlockPos> currentPlug = new ArrayList<>();

                for (int y = yMin; y <= yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    Block block = state.getBlock();

                    if (isPlugBlock(block)) {
                        if (plugType == null || block == plugType) {
                            sameCount++;
                            plugType = block;
                            currentPlug.add(pos);
                        } else {
                            sameCount = 1;
                            plugType = block;
                            currentPlug.clear();
                            currentPlug.add(pos);
                        }

                        if (sameCount >= 25) {
                            if (isCoveredColumn(chunk, x, z, y - sameCount + 1, y, plugType)) {
                                List<BlockPos> verifiedBlocks = new ArrayList<>();
                                for (BlockPos plugPos : currentPlug) {
                                    if (isBlockCovered(chunk, plugPos) && isPositionUnderground(plugPos)) {
                                        verifiedBlocks.add(plugPos);
                                    }
                                }
                                foundBlocks.addAll(verifiedBlocks);
                            }
                        }
                    } else {
                        sameCount = 0;
                        plugType = null;
                        currentPlug.clear();
                    }
                }
            }
        }

        return foundBlocks;
    }

    private boolean isPlugBlock(Block block) {
        return block == Blocks.GRANITE || block == Blocks.ANDESITE || block == Blocks.DIRT || block == Blocks.DIORITE;
    }

    private boolean isCoveredColumn(WorldChunk chunk, int x, int z, int yStart, int yEnd, Block blockType) {
        for (int y = yStart; y <= yEnd; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!chunk.getBlockState(pos).isOf(blockType)) return false;
            for (Direction dir : Direction.values()) {
                BlockPos adj = pos.offset(dir);
                BlockState adjState = chunk.getBlockState(adj);
                if (adjState.isAir() || !adjState.isSolidBlock(mc.world, adj)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isPositionUnderground(BlockPos pos) {
        if (mc.world == null) return false;

        int checkHeight = Math.min(pos.getY() + 50, MAX_UNDERGROUND_Y + 20);
        int solidBlocksAbove = 0;

        for (int y = pos.getY() + 1; y <= checkHeight; y++) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState state = mc.world.getBlockState(checkPos);

            if (!state.isAir() && state.isSolidBlock(mc.world, checkPos)) {
                solidBlocksAbove++;
            }
        }

        return solidBlocksAbove >= 3;
    }

    private boolean isTargetBlock(BlockState state) {
        if (state == null || state.isAir()) return false;

        Block block = state.getBlock();

        if (block == Blocks.DEEPSLATE) {
            return !isRotatedDeepslate(state);
        }

        return block == Blocks.TUFF;
    }

    private boolean isRotatedDeepslate(BlockState state) {
        if (state == null || state.isAir()) return false;

        Block block = state.getBlock();

        if (block == Blocks.DEEPSLATE) {
            if (state.contains(PillarBlock.AXIS)) {
                Direction.Axis axis = state.get(PillarBlock.AXIS);
                return axis == Direction.Axis.X || axis == Direction.Axis.Z;
            }
        }

        return false;
    }

    private boolean isBlockCovered(WorldChunk chunk, BlockPos pos) {
        Direction[] directions = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction dir : directions) {
            BlockPos adjacentPos = pos.offset(dir);

            try {
                BlockState adjacentState = null;

                if (mc.world != null) {
                    adjacentState = mc.world.getBlockState(adjacentPos);
                } else {
                    return false;
                }

                if (adjacentState.isAir() || isTransparentBlock(adjacentState)) {
                    return false;
                }

                if (!adjacentState.isSolidBlock(mc.world, adjacentPos)) {
                    return false;
                }

            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    private boolean isTransparentBlock(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.GLASS ||
                block == Blocks.WATER ||
                block == Blocks.LAVA ||
                block == Blocks.ICE ||
                block == Blocks.PACKED_ICE ||
                block == Blocks.BLUE_ICE) {
            return true;
        }

        if (block == Blocks.WHITE_STAINED_GLASS ||
                block == Blocks.ORANGE_STAINED_GLASS ||
                block == Blocks.MAGENTA_STAINED_GLASS ||
                block == Blocks.LIGHT_BLUE_STAINED_GLASS ||
                block == Blocks.YELLOW_STAINED_GLASS ||
                block == Blocks.LIME_STAINED_GLASS ||
                block == Blocks.PINK_STAINED_GLASS ||
                block == Blocks.GRAY_STAINED_GLASS ||
                block == Blocks.LIGHT_GRAY_STAINED_GLASS ||
                block == Blocks.CYAN_STAINED_GLASS ||
                block == Blocks.PURPLE_STAINED_GLASS ||
                block == Blocks.BLUE_STAINED_GLASS ||
                block == Blocks.BROWN_STAINED_GLASS ||
                block == Blocks.GREEN_STAINED_GLASS ||
                block == Blocks.RED_STAINED_GLASS ||
                block == Blocks.BLACK_STAINED_GLASS ||
                block == Blocks.TINTED_GLASS) {
            return true;
        }

        return block == Blocks.OAK_LEAVES ||
                block == Blocks.SPRUCE_LEAVES ||
                block == Blocks.BIRCH_LEAVES ||
                block == Blocks.JUNGLE_LEAVES ||
                block == Blocks.ACACIA_LEAVES ||
                block == Blocks.DARK_OAK_LEAVES ||
                block == Blocks.MANGROVE_LEAVES ||
                block == Blocks.CHERRY_LEAVES;
    }

    private void cleanupDistantChunks() {
        if (mc.player == null) return;

        int viewDist = mc.options.getViewDistance().getValue();
        int playerChunkX = (int) mc.player.getX() / 16;
        int playerChunkZ = (int) mc.player.getZ() / 16;

        flaggedChunks.removeIf(chunkPos -> {
            int dx = Math.abs(chunkPos.x - playerChunkX);
            int dz = Math.abs(chunkPos.z - playerChunkZ);
            boolean shouldRemove = dx > viewDist || dz > viewDist;
            if (shouldRemove) {
                flaggedBlocks.remove(chunkPos);
            }
            return shouldRemove;
        });

        scannedChunks.removeIf(chunkPos -> {
            int dx = Math.abs(chunkPos.x - playerChunkX);
            int dz = Math.abs(chunkPos.z - playerChunkZ);
            return dx > viewDist || dz > viewDist;
        });

        notifiedChunks.removeIf(chunkPos -> {
            int dx = Math.abs(chunkPos.x - playerChunkX);
            int dz = Math.abs(chunkPos.z - playerChunkZ);
            return dx > viewDist || dz > viewDist;
        });
    }

    @EventListener
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Performance check
        if (mc.getCurrentFps() < 8 && mc.player.age > 100) {
            toggle(false);
            System.out.println("§b🔍 [ChunkFinder] ✨ Disabled due to low FPS");
            return;
        }

        // Apply camera transformations like StorageESP
        Camera cam = RenderUtils.getCamera();
        if (cam != null) {
            Vec3d camPos = RenderUtils.getCameraPos();
            MatrixStack matrices = event.matrixStack;
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cam.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(cam.getYaw() + 180.0f));
            matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        }

        // Render flagged chunks if they exist
        if (!flaggedChunks.isEmpty()) {
            for (ChunkPos chunkPos : flaggedChunks) {
                int a = (int) this.fillAlpha.getValue();
                renderChunkHighlight(event.matrixStack, chunkPos, new Color(0, 255, 0, a));
                if (highlightBlocks.getValue()) {
                    renderFlaggedBlocks(event.matrixStack, chunkPos);
                }
            }
        }

        // Render trader chunks if they exist and detection is enabled
        if (detectTraders.getValue() && !traderChunks.isEmpty()) {
            for (ChunkPos chunkPos : traderChunks) {
                int a2 = (int) this.fillAlpha.getValue();
                Color base = traderChunkColor.getValue();
                renderChunkHighlight(event.matrixStack, chunkPos, new Color(base.getRed(), base.getGreen(), base.getBlue(), a2));
            }
        }

        // Render holes if hole ESP is enabled
        if (holeESP.getValue()) {
            if (detect1x1Holes.getValue() && !holes1x1.isEmpty()) {
                renderHoles1x1(event.matrixStack);
            }
            if (detect3x1Holes.getValue() && !holes3x1.isEmpty()) {
                renderHoles3x1(event.matrixStack);
            }
        }

        // Pop the matrix stack
        event.matrixStack.pop();
    }

    private void renderHoles1x1(MatrixStack stack) {
        Color color = hole1x1Color.getValue();
        for (Box hole : holes1x1) {
            if (holeFill.getValue()) {
                RenderUtils.renderFilledBox(stack,
                        (float)hole.minX, (float)hole.minY, (float)hole.minZ,
                        (float)hole.maxX, (float)hole.maxY, (float)hole.maxZ,
                        color);
            }
            if (holeOutline.getValue()) {
                renderBoxOutline(stack,
                        (float)hole.minX, (float)hole.minY, (float)hole.minZ,
                        (float)hole.maxX, (float)hole.maxY, (float)hole.maxZ,
                        new Color(color.getRed(), color.getGreen(), color.getBlue(), 255));
            }
        }
    }

    private void renderHoles3x1(MatrixStack stack) {
        Color color = hole3x1Color.getValue();
        for (Box hole : holes3x1) {
            if (holeFill.getValue()) {
                RenderUtils.renderFilledBox(stack,
                        (float)hole.minX, (float)hole.minY, (float)hole.minZ,
                        (float)hole.maxX, (float)hole.maxY, (float)hole.maxZ,
                        color);
            }
            if (holeOutline.getValue()) {
                renderBoxOutline(stack,
                        (float)hole.minX, (float)hole.minY, (float)hole.minZ,
                        (float)hole.maxX, (float)hole.maxY, (float)hole.maxZ,
                        new Color(color.getRed(), color.getGreen(), color.getBlue(), 255));
            }
        }
    }

    private void renderChunkHighlight(MatrixStack stack, ChunkPos chunkPos, Color renderColor) {
        int startX = chunkPos.x * 16;
        int startZ = chunkPos.z * 16;
        int endX = startX + 16;
        int endZ = startZ + 16;

        double y = HIGHLIGHT_Y;
        double height = 0.1f;

        Box chunkBox = new Box(startX, y, startZ, endX, y + height, endZ);

        if (fill.getValue()) {
            RenderUtils.renderFilledBox(stack, (float)chunkBox.minX, (float)chunkBox.minY, (float)chunkBox.minZ, (float)chunkBox.maxX, (float)chunkBox.maxY, (float)chunkBox.maxZ, renderColor);
        }

        if (outline.getValue()) {
            renderBoxOutline(stack, (float)chunkBox.minX, (float)chunkBox.minY, (float)chunkBox.minZ, (float)chunkBox.maxX, (float)chunkBox.maxY, (float)chunkBox.maxZ, renderColor);
        }

        if (tracers.getValue()) {
            // Fixed tracer calculation
            Vec3d tracerStart = new Vec3d(0, 0, 75)
                    .rotateX(-(float) Math.toRadians(mc.gameRenderer.getCamera().getPitch()))
                    .rotateY(-(float) Math.toRadians(mc.gameRenderer.getCamera().getYaw()))
                    .add(mc.cameraEntity.getEyePos());

            Vec3d chunkCenter = new Vec3d(startX + 8, y + height / 2, startZ + 8);
            RenderUtils.renderLine(stack, renderColor, tracerStart, chunkCenter);
        }
    }

    private void renderFlaggedBlocks(MatrixStack stack, ChunkPos chunkPos) {
        Set<BlockPos> blocks = flaggedBlocks.get(chunkPos);
        if (blocks == null || blocks.isEmpty()) return;

        Color blockColor = new Color(255, 0, 0, 120);
        for (BlockPos blockPos : blocks) {
            Box blockBox = new Box(blockPos);
            RenderUtils.renderFilledBox(stack, (float)blockBox.minX, (float)blockBox.minY, (float)blockBox.minZ, (float)blockBox.maxX, (float)blockBox.maxY, (float)blockBox.maxZ, new Color(blockColor.getRed(), blockColor.getGreen(), blockColor.getBlue(), Math.max(40, blockColor.getAlpha())));
            renderBoxOutline(stack, (float)blockBox.minX, (float)blockBox.minY, (float)blockBox.minZ, (float)blockBox.maxX, (float)blockBox.maxY, (float)blockBox.maxZ, new Color(blockColor.getRed(), blockColor.getGreen(), blockColor.getBlue(), 200));
        }
    }

    private List<WorldChunk> getLoadedChunks() {
        List<WorldChunk> chunks = new ArrayList<>();
        int viewDist = mc.options.getViewDistance().getValue();

        for (int x = -viewDist; x <= viewDist; x++) {
            for (int z = -viewDist; z <= viewDist; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(
                        (int) mc.player.getX() / 16 + x,
                        (int) mc.player.getZ() / 16 + z
                );

                if (chunk != null) chunks.add(chunk);
            }
        }
        return chunks;
    }

    private void renderBoxOutline(MatrixStack stack, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, Color color) {
        // Render the 12 edges of the box as lines
        Vec3d[] corners = {
                new Vec3d(minX, minY, minZ), // 0
                new Vec3d(maxX, minY, minZ), // 1
                new Vec3d(maxX, minY, maxZ), // 2
                new Vec3d(minX, minY, maxZ), // 3
                new Vec3d(minX, maxY, minZ), // 4
                new Vec3d(maxX, maxY, minZ), // 5
                new Vec3d(maxX, maxY, maxZ), // 6
                new Vec3d(minX, maxY, maxZ)  // 7
        };

        // Bottom face edges
        RenderUtils.renderLine(stack, color, corners[0], corners[1]);
        RenderUtils.renderLine(stack, color, corners[1], corners[2]);
        RenderUtils.renderLine(stack, color, corners[2], corners[3]);
        RenderUtils.renderLine(stack, color, corners[3], corners[0]);

        // Top face edges
        RenderUtils.renderLine(stack, color, corners[4], corners[5]);
        RenderUtils.renderLine(stack, color, corners[5], corners[6]);
        RenderUtils.renderLine(stack, color, corners[6], corners[7]);
        RenderUtils.renderLine(stack, color, corners[7], corners[4]);

        // Vertical edges
        RenderUtils.renderLine(stack, color, corners[0], corners[4]);
        RenderUtils.renderLine(stack, color, corners[1], corners[5]);
        RenderUtils.renderLine(stack, color, corners[2], corners[6]);
        RenderUtils.renderLine(stack, color, corners[3], corners[7]);
    }
}