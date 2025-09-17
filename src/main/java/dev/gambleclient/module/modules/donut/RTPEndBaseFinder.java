package dev.gambleclient.module.modules.donut;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.module.setting.StringSetting;
import dev.gambleclient.utils.BlockUtil;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.embed.DiscordWebhook;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class RTPEndBaseFinder extends Module {
    private final NumberSetting minimumStorageCount = (NumberSetting) new NumberSetting(EncryptedString.of("Minimum Storage Count"), 1, 100, 4, 1)
            .setDescription(EncryptedString.of("The minimum amount of storage blocks in a chunk to record the chunk (spawners ignore this limit)"));
    
    private final BooleanSetting criticalSpawner = new BooleanSetting(EncryptedString.of("Critical Spawner"), true)
            .setDescription(EncryptedString.of("Mark chunk as stash even if only a single spawner is found"));
    
    private final NumberSetting rtpInterval = (NumberSetting) new NumberSetting(EncryptedString.of("RTP Interval"), 5, 60, 10, 1)
            .setDescription(EncryptedString.of("Interval between RTP commands in seconds"));
    
    private final BooleanSetting disconnectOnBaseFind = new BooleanSetting(EncryptedString.of("Disconnect on Base Find"), true)
            .setDescription(EncryptedString.of("Automatically disconnect when a base is found"));
    
    private final BooleanSetting lookDown = new BooleanSetting(EncryptedString.of("Look Down"), true)
            .setDescription(EncryptedString.of("Automatically look down to avoid enderman aggro"));
    
    private final BooleanSetting sendNotifications = new BooleanSetting(EncryptedString.of("Notifications"), true)
            .setDescription(EncryptedString.of("Sends Minecraft notifications when new stashes are found"));
    
    private final BooleanSetting enableWebhook = new BooleanSetting(EncryptedString.of("Webhook"), false)
            .setDescription(EncryptedString.of("Send webhook notifications when stashes are found"));
    
    private final StringSetting webhookUrl = new StringSetting(EncryptedString.of("Webhook URL"), "")
            .setDescription(EncryptedString.of("Discord webhook URL"));
    
    private final BooleanSetting selfPing = new BooleanSetting(EncryptedString.of("Self Ping"), false)
            .setDescription(EncryptedString.of("Ping yourself in the webhook message"));
    
    private final StringSetting discordId = new StringSetting(EncryptedString.of("Discord ID"), "")
            .setDescription(EncryptedString.of("Your Discord user ID for pinging"));

    // Performance optimization settings
    private final NumberSetting scanInterval = (NumberSetting) new NumberSetting(EncryptedString.of("Scan Interval"), 1, 20, 5, 1)
            .setDescription(EncryptedString.of("Interval between chunk scans in ticks (higher = less lag)"));
    
    private final NumberSetting maxChunksPerScan = (NumberSetting) new NumberSetting(EncryptedString.of("Max Chunks Per Scan"), 1, 10, 3, 1)
            .setDescription(EncryptedString.of("Maximum chunks to scan per tick (lower = less lag)"));
    
    private final BooleanSetting enableSpawnerCheck = new BooleanSetting(EncryptedString.of("Enable Spawner Check"), true)
            .setDescription(EncryptedString.of("Check for spawners (disable for maximum performance)"));

    public List<EndStashChunk> foundStashes = new ArrayList<>();
    private final Set<ChunkPos> processedChunks = new HashSet<>();
    private long lastRtpTime = 0;
    private Float originalPitch = null;
    
    // Performance optimization variables
    private int scanTickCounter = 0;
    private final Queue<ChunkPos> chunksToScan = new LinkedList<>();
    private final Set<ChunkPos> chunksInQueue = new HashSet<>();
    private long lastPerformanceCheck = 0;
    private int chunksScannedThisSecond = 0;

    public RTPEndBaseFinder() {
        super(EncryptedString.of("RTP End Base Finder"), 
              EncryptedString.of("Continuously RTPs to the End and searches for stashes"), 
              -1, 
              Category.DONUT);
        addSettings(this.minimumStorageCount, this.criticalSpawner, this.rtpInterval, this.disconnectOnBaseFind,
                   this.lookDown, this.sendNotifications, this.enableWebhook, this.webhookUrl, this.selfPing, this.discordId,
                   this.scanInterval, this.maxChunksPerScan, this.enableSpawnerCheck);
    }

    @Override
    public void onEnable() {
        foundStashes.clear();
        processedChunks.clear();
        lastRtpTime = 0;
        scanTickCounter = 0;
        chunksToScan.clear();
        chunksInQueue.clear();

        if (lookDown.getValue() && mc.player != null) {
            originalPitch = mc.player.getPitch();
        }

        System.out.println("Started RTP End Base Finder");
    }

    @Override
    public void onDisable() {
        processedChunks.clear();
        chunksToScan.clear();
        chunksInQueue.clear();

        if (originalPitch != null && mc.player != null) {
            mc.player.setPitch(originalPitch);
            originalPitch = null;
        }
    }

    @EventListener
    public void onTick(final TickEvent tickEvent) {
        if (mc.player == null || mc.world == null) {
            if (isEnabled()) {
                toggle();
            }
            return;
        }

        if (lookDown.getValue()) {
            mc.player.setPitch(90.0f);
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRtpTime >= rtpInterval.getIntValue() * 1000L) {
            mc.player.networkHandler.sendChatCommand("rtp end");
            lastRtpTime = currentTime;
        }

        // Optimized chunk scanning with throttling
        scanTickCounter++;
        if (scanTickCounter >= scanInterval.getIntValue()) {
            scanTickCounter = 0;
            updateChunkQueue();
            scanQueuedChunks();
        }
    }

    private void updateChunkQueue() {
        if (mc.player == null || mc.world == null) return;

        // Add new chunks to the queue
        for (WorldChunk worldChunk : BlockUtil.getLoadedChunks().toList()) {
            ChunkPos chunkPos = worldChunk.getPos();
            if (!processedChunks.contains(chunkPos) && !chunksInQueue.contains(chunkPos)) {
                chunksToScan.offer(chunkPos);
                chunksInQueue.add(chunkPos);
            }
        }
    }

    private void scanQueuedChunks() {
        int chunksScanned = 0;
        int maxChunks = maxChunksPerScan.getIntValue();

        while (!chunksToScan.isEmpty() && chunksScanned < maxChunks) {
            ChunkPos chunkPos = chunksToScan.poll();
            chunksInQueue.remove(chunkPos);
            
            if (chunkPos != null && mc.world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                WorldChunk worldChunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
                if (worldChunk != null) {
                    scanSingleChunk(worldChunk);
                }
            }
            
            chunksScanned++;
            chunksScannedThisSecond++;
        }
        
        // Performance monitoring
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPerformanceCheck >= 1000) {
            if (chunksScannedThisSecond > 0) {
                System.out.println("[RTPEndBaseFinder] Performance: " + chunksScannedThisSecond + " chunks scanned in last second");
            }
            chunksScannedThisSecond = 0;
            lastPerformanceCheck = currentTime;
        }
    }

    private void scanSingleChunk(WorldChunk worldChunk) {
        ChunkPos chunkPos = worldChunk.getPos();
        if (processedChunks.contains(chunkPos)) return;

        EndStashChunk chunk = new EndStashChunk(chunkPos);
        boolean hasSpawner = false;

        // Optimized spawner detection - only check block entities first
        if (enableSpawnerCheck.getValue()) {
            for (BlockEntity blockEntity : worldChunk.getBlockEntities().values()) {
                if (blockEntity instanceof MobSpawnerBlockEntity) {
                    chunk.spawners++;
                    hasSpawner = true;
                }
            }

            // If no spawners found in block entities, do a quick block state check
            if (!hasSpawner && criticalSpawner.getValue()) {
                hasSpawner = quickSpawnerCheck(worldChunk);
                if (hasSpawner) {
                    chunk.spawners++;
                }
            }
        }

        // Check for storage blocks
        for (BlockEntity blockEntity : worldChunk.getBlockEntities().values()) {
            BlockEntityType<?> type = blockEntity.getType();

            if (isStorageBlock(type)) {
                if (blockEntity instanceof ChestBlockEntity) {
                    chunk.chests++;
                } else if (blockEntity instanceof BarrelBlockEntity) {
                    chunk.barrels++;
                } else if (blockEntity instanceof ShulkerBoxBlockEntity) {
                    chunk.shulkers++;
                } else if (blockEntity instanceof EnderChestBlockEntity) {
                    chunk.enderChests++;
                } else if (blockEntity instanceof AbstractFurnaceBlockEntity) {
                    chunk.furnaces++;
                } else if (blockEntity instanceof DispenserBlockEntity) {
                    chunk.dispensersDroppers++;
                } else if (blockEntity instanceof HopperBlockEntity) {
                    chunk.hoppers++;
                }
            }
        }

        boolean isStash = false;
        boolean isCriticalSpawner = false;
        String detectionReason = "";

        if (criticalSpawner.getValue() && hasSpawner) {
            isStash = true;
            isCriticalSpawner = true;
            detectionReason = "Spawner(s) detected (Critical mode)";
        } else if (chunk.getTotalNonSpawner() >= minimumStorageCount.getIntValue()) {
            isStash = true;
            detectionReason = "Storage threshold reached (" + chunk.getTotalNonSpawner() + " blocks)";
        }

        if (isStash) {
            processedChunks.add(chunkPos);

            EndStashChunk prevChunk = null;
            int existingIndex = foundStashes.indexOf(chunk);

            if (existingIndex < 0) {
                foundStashes.add(chunk);
            } else {
                prevChunk = foundStashes.set(existingIndex, chunk);
            }

            if (sendNotifications.getValue() && (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk))) {
                String stashType = isCriticalSpawner ? "End spawner base" : "End stash";
                System.out.println("Found " + stashType + " at " + chunk.x + ", " + chunk.z + ". " + detectionReason);
            }

            if (enableWebhook.getValue() && (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk))) {
                sendWebhookNotification(chunk, isCriticalSpawner, detectionReason);
            }

            if (disconnectOnBaseFind.getValue()) {
                String stashTypeForDisconnect = isCriticalSpawner ? "End spawner base" : "End stash";
                disconnectPlayer(stashTypeForDisconnect, chunk);
            }
        }
    }

    // Optimized spawner check - only checks a few key positions instead of all blocks
    private boolean quickSpawnerCheck(WorldChunk worldChunk) {
        ChunkPos chunkPos = worldChunk.getPos();
        int xStart = chunkPos.getStartX();
        int zStart = chunkPos.getStartZ();
        
        // Check only a few strategic positions where spawners are commonly placed
        int[] checkY = {8, 16, 24, 32, 40, 48, 56, 64};
        int[] checkX = {2, 6, 10, 14};
        int[] checkZ = {2, 6, 10, 14};
        
        for (int y : checkY) {
            for (int x : checkX) {
                for (int z : checkZ) {
                    BlockPos pos = new BlockPos(xStart + x, y, zStart + z);
                    try {
                        if (mc.world.getBlockState(pos).getBlock() == Blocks.SPAWNER) {
                            return true;
                        }
                    } catch (Exception ignored) {
                        // Ignore any exceptions during block state access
                    }
                }
            }
        }
        return false;
    }

    private boolean isStorageBlock(BlockEntityType<?> type) {
        return type == BlockEntityType.CHEST ||
               type == BlockEntityType.BARREL ||
               type == BlockEntityType.SHULKER_BOX ||
               type == BlockEntityType.ENDER_CHEST ||
               type == BlockEntityType.FURNACE ||
               type == BlockEntityType.BLAST_FURNACE ||
               type == BlockEntityType.SMOKER ||
               type == BlockEntityType.DISPENSER ||
               type == BlockEntityType.DROPPER ||
               type == BlockEntityType.HOPPER;
    }

    private void disconnectPlayer(String stashType, EndStashChunk chunk) {
        System.out.println("Disconnecting due to " + stashType + " found at " + chunk.x + ", " + chunk.z);

        toggle();

        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            if (mc.player != null) {
                mc.player.networkHandler.onDisconnect(
                    new DisconnectS2CPacket(Text.literal("END STASH FOUND AT " + chunk.x + ", " + chunk.z + "!"))
                );
            }
        }, 1, TimeUnit.SECONDS);
    }

    private void sendWebhookNotification(EndStashChunk chunk, boolean isCriticalSpawner, String detectionReason) {
        String url = webhookUrl.getValue().trim();
        if (url.isEmpty()) {
            System.out.println("Webhook URL not configured!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String serverInfo = mc.getCurrentServerEntry() != null ?
                    mc.getCurrentServerEntry().address : "Unknown Server";

                String messageContent = "";
                if (selfPing.getValue() && !discordId.getValue().trim().isEmpty()) {
                    messageContent = String.format("<@%s>", discordId.getValue().trim());
                }

                String stashType = isCriticalSpawner ? "End Spawner Base" : "End Stash";
                String description = String.format("%s found at End coordinates %d, %d!", stashType, chunk.x, chunk.z);

                StringBuilder itemsFound = new StringBuilder();
                int totalItems = 0;

                if (chunk.chests > 0) {
                    itemsFound.append("Chests: ").append(chunk.chests).append("\\n");
                    totalItems += chunk.chests;
                }
                if (chunk.barrels > 0) {
                    itemsFound.append("Barrels: ").append(chunk.barrels).append("\\n");
                    totalItems += chunk.barrels;
                }
                if (chunk.shulkers > 0) {
                    itemsFound.append("Shulker Boxes: ").append(chunk.shulkers).append("\\n");
                    totalItems += chunk.shulkers;
                }
                if (chunk.enderChests > 0) {
                    itemsFound.append("Ender Chests: ").append(chunk.enderChests).append("\\n");
                    totalItems += chunk.enderChests;
                }
                if (chunk.furnaces > 0) {
                    itemsFound.append("Furnaces: ").append(chunk.furnaces).append("\\n");
                    totalItems += chunk.furnaces;
                }
                if (chunk.dispensersDroppers > 0) {
                    itemsFound.append("Dispensers/Droppers: ").append(chunk.dispensersDroppers).append("\\n");
                    totalItems += chunk.dispensersDroppers;
                }
                if (chunk.hoppers > 0) {
                    itemsFound.append("Hoppers: ").append(chunk.hoppers).append("\\n");
                    totalItems += chunk.hoppers;
                }

                if (isCriticalSpawner) {
                    itemsFound.append("Spawners: Present\\n");
                }

                DiscordWebhook webhook = new DiscordWebhook(url);
                webhook.setUsername("RTP End-Stashfinder");
                webhook.setAvatarUrl("https://i.imgur.com/OL2y1cr.png");
                webhook.setContent(messageContent);
                
                DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject();
                embed.setTitle("🌌 End Stashfinder Alert");
                embed.setDescription(description);
                embed.setColor(new Color(isCriticalSpawner ? 9830144 : 8388736));
                embed.addField("Detection Reason", detectionReason, false);
                embed.addField("Total Items Found", String.valueOf(totalItems), false);
                embed.addField("Items Breakdown", itemsFound.toString(), false);
                embed.addField("End Coordinates", chunk.x + ", " + chunk.z, true);
                embed.addField("Server", serverInfo, true);
                embed.addField("Time", "<t:" + (System.currentTimeMillis() / 1000) + ":R>", true);
                embed.setFooter("RTP End-Stashfinder", null);
                
                webhook.addEmbed(embed);
                webhook.execute();

                System.out.println("Webhook notification sent successfully");

            } catch (Throwable e) {
                System.out.println("Failed to send webhook: " + e.getMessage());
            }
        });
    }

    public static class EndStashChunk {
        public ChunkPos chunkPos;
        public transient int x, z;
        public int chests, barrels, shulkers, enderChests, furnaces, dispensersDroppers, hoppers, spawners;

        public EndStashChunk(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
            calculatePos();
        }

        public void calculatePos() {
            x = chunkPos.x * 16 + 8;
            z = chunkPos.z * 16 + 8;
        }

        public int getTotal() {
            return chests + barrels + shulkers + enderChests + furnaces + dispensersDroppers + hoppers + spawners;
        }

        public int getTotalNonSpawner() {
            return chests + barrels + shulkers + enderChests + furnaces + dispensersDroppers + hoppers;
        }

        public boolean countsEqual(EndStashChunk c) {
            if (c == null) return false;
            return chests == c.chests && barrels == c.barrels && shulkers == c.shulkers &&
                enderChests == c.enderChests && furnaces == c.furnaces &&
                dispensersDroppers == c.dispensersDroppers && hoppers == c.hoppers &&
                spawners == c.spawners;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EndStashChunk chunk = (EndStashChunk) o;
            return Objects.equals(chunkPos, chunk.chunkPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkPos);
        }
    }
}
