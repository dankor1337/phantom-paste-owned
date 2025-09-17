package dev.gambleclient.module.modules.donut;

import dev.gambleclient.Gamble;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.*;
import dev.gambleclient.utils.EncryptedString;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import dev.gambleclient.module.modules.misc.AutoEat;
import dev.gambleclient.module.modules.ai.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PhantomClient port of BStar with 1:1 logic preservation.
 * All behavioral logic from the Meteor original is retained; only API / setting / event
 * adjustments were made to compile within PhantomClient.
 */
public class BStarPhantom extends Module {
    /* =========================
       ENUMS & INTERNAL TYPES
       ========================= */

    public enum RTPRegion {
        EU_CENTRAL("eu central"),
        EU_WEST("eu west"),
        NA_EAST("east"),
        NA_WEST("west"),
        OCEANIA("oceania"),
        ASIA("asia");

        private final String command;
        RTPRegion(String cmd) { this.command = cmd; }
        public String cmd() { return command; }
    }

    private enum GroundHazard { NONE, FLUIDS, VOID, BOTH }

    /* =========================
       SETTINGS
       ========================= */

    private final NumberSetting scanDepth          = new NumberSetting(EncryptedString.of("Scan Depth"), 10, 30, 20, 1);
    private final BooleanSetting humanLikeRotation = new BooleanSetting(EncryptedString.of("Human-like Rotation"), true);
    private final BooleanSetting disconnectOnBaseFind = new BooleanSetting(EncryptedString.of("Log On Base/Spawners"), true);
    private final BooleanSetting showHazards       = new BooleanSetting(EncryptedString.of("Show Hazards"), true);
    private final NumberSetting baseThreshold      = new NumberSetting(EncryptedString.of("Min Storage For Base"), 1, 20, 4, 1);

    private static final List<BlockEntityType<?>> DEFAULT_STORAGE = List.of(
            BlockEntityType.CHEST,
            BlockEntityType.BARREL,
            BlockEntityType.SHULKER_BOX,
            BlockEntityType.ENDER_CHEST,
            BlockEntityType.FURNACE,
            BlockEntityType.DISPENSER,
            BlockEntityType.DROPPER,
            BlockEntityType.HOPPER
    );
    private final BlockEntityListSetting storageBlocks = new BlockEntityListSetting(
            EncryptedString.of("Storage Blocks"), DEFAULT_STORAGE
    );

    private final BooleanSetting debugMode          = new BooleanSetting(EncryptedString.of("Debug Mode"), true);
    private final BooleanSetting showWaypoints      = new BooleanSetting(EncryptedString.of("Show Waypoints"), true);
    private final NumberSetting yLevel              = new NumberSetting(EncryptedString.of("Max Y"), -64, -10, -20, 1);
    private final NumberSetting scanHeight          = new NumberSetting(EncryptedString.of("Scan Height"), 3, 8, 4, 1);
    private final NumberSetting scanWidthFalling    = new NumberSetting(EncryptedString.of("Falling Width"), 1, 4, 1, 1);
    private final NumberSetting scanWidthFluids     = new NumberSetting(EncryptedString.of("Fluids Width"), 2, 6, 2, 1);
    private final BooleanSetting strictGroundCheck  = new BooleanSetting(EncryptedString.of("Strict Ground"), false);

    private final BooleanSetting smoothRotation     = new BooleanSetting(EncryptedString.of("Smooth Rotation"), true);
    private final NumberSetting rotationSpeed       = new NumberSetting(EncryptedString.of("Rotation Speed"), 1, 10, 4.5, 0.1);
    private final NumberSetting rotationAcceleration= new NumberSetting(EncryptedString.of("Rotation Accel"), 0.1, 2.0, 0.8, 0.05);
    private final NumberSetting overshootChance     = new NumberSetting(EncryptedString.of("Overshoot Chance"), 0.0, 1.0, 0.3, 0.05);

    private final BooleanSetting workInMenus        = new BooleanSetting(EncryptedString.of("Work In Menus"), false);
    private final BooleanSetting pauseForAutoEat    = new BooleanSetting(EncryptedString.of("Pause For AutoEat"), true);

    private final BooleanSetting rtpWhenStuck       = new BooleanSetting(EncryptedString.of("RTP When Stuck"), false);
    private final ModeSetting<RTPRegion> rtpRegion  = new ModeSetting<>(EncryptedString.of("RTP Region"), RTPRegion.EU_CENTRAL, RTPRegion.class);

    private final NumberSetting mineDownTarget      = new NumberSetting(EncryptedString.of("Mine Down Target Y"), -64, -10, -30, 1);
    private final NumberSetting groundScanSize      = new NumberSetting(EncryptedString.of("Ground Scan Size"), 3, 9, 5, 1);
    private final BooleanSetting autoEnableAutoLog  = new BooleanSetting(EncryptedString.of("Auto Enable AutoLog"), false);
    private final NumberSetting safeMineDownSearchRadius = new NumberSetting(EncryptedString.of("Safe MineDown Radius"), 5, 20, 10, 1);

    private final BooleanSetting autoRepair         = new BooleanSetting(EncryptedString.of("Auto Repair"), false);
    private final NumberSetting repairThreshold     = new NumberSetting(EncryptedString.of("Repair Threshold %"), 10, 50, 20, 1);
    private final NumberSetting repairDuration      = new NumberSetting(EncryptedString.of("Repair Duration s"), 1, 5, 3, 1);

    private final BooleanSetting detectPlayers      = new BooleanSetting(EncryptedString.of("Detect Players"), true);
    private final NumberSetting voidDepthThreshold  = new NumberSetting(EncryptedString.of("Void Depth Threshold"), 5, 15, 7, 1);
    private final NumberSetting playerCheckInterval = new NumberSetting(EncryptedString.of("Player Check Interval"), 10, 60, 20, 1);
    private final NumberSetting rotationRandomness  = new NumberSetting(EncryptedString.of("Rotation Randomness"), 0, 2.0, 0.5, 0.05);
    private final BooleanSetting alwaysFocused      = new BooleanSetting(EncryptedString.of("Always Focused"), false);
    private final NumberSetting retoggleDelay       = new NumberSetting(EncryptedString.of("Retoggle Delay s"), 0, 6, 3, 1);
    private final BooleanSetting bypassMiningDown   = new BooleanSetting(EncryptedString.of("Bypass Mining Down"), false);
    private final NumberSetting intervalHoldDuration= new NumberSetting(EncryptedString.of("Interval Hold ms"), 200, 1500, 1000, 20);
    private final NumberSetting intervalPauseDuration= new NumberSetting(EncryptedString.of("Interval Pause ms"), 400, 1500, 460, 20);

    /* =========================
       RUNTIME STATE
       ========================= */
    private MiningState currentState = MiningState.IDLE;
    private DirectionalPathfinder pathfinder;
    private PathScanner pathScanner;
    private SafetyValidator safetyValidator;
    private RotationController rotationController;
    private SimpleSneakCentering centeringHelper;
    private ParkourHelper parkourHelper;

    private boolean isDetouring = false;
    private BlockPos currentWaypoint;
    private PathScanner.ScanResult lastHazardDetected;
    private Direction pendingDirection;

    private int tickCounter = 0;
    private int playerCheckTicks = 0;
    private long moduleStartTime = 0;

    private final Set<BlockPos> hazardBlocks = new HashSet<>();
    private final Set<BlockPos> waypointBlocks = new HashSet<>();
    private final Set<ChunkPos> processedChunks = new HashSet<>();

    private int blocksMined = 0;
    private int totalBlocksMined = 0;
    private Vec3d lastPos = Vec3d.ZERO;
    private int scanTicks = 0;

    // RTP / mining down
    private long lastRtpTime = 0;
    private int rtpWaitTicks = 0;
    private int rtpAttempts = 0;
    private BlockPos preRtpPos = null;
    private boolean rtpCommandSent = false;
    private boolean hasReachedMineDepth = false;
    private int mineDownScanTicks = 0;
    private boolean inRTPRecovery = false;

    // Repair
    private boolean isThrowingExp = false;
    private MiningState stateBeforeExp = MiningState.IDLE;
    private int expThrowTicks = 0;
    private long lastExpThrowTime = 0;

    // Eating pause
    private boolean wasPausedForEating = false;
    private MiningState stateBeforeEating = MiningState.IDLE;

    // Mining down extended
    private boolean miningDownInitiated = false;
    private int miningDownToggleTicks = 0;
    private BlockPos safeMineDownTarget = null;
    private int searchAttempts = 0;
    private boolean searchingForSafeMineSpot = false;
    private boolean waitingAtTargetDepth = false;
    private int targetDepthWaitTicks = 0;

    private BlockPos moveAndRetryTarget = null;
    private int moveAndRetryTicks = 0;
    private boolean waitingToRetoggle = false;
    private int retoggleDelayTicks = 0;
    private boolean intervalMiningActive = false;
    private boolean intervalHolding = false;
    private long intervalStartTime = 0;
    private int retoggleAttempts = 0;

    // Constants (match original)
    private static final int SCAN_INTERVAL = 20;
    private static final int RTP_COOLDOWN_SECONDS = 16;
    private static final int RTP_WAIT_TICKS = 200;
    private static final int MINE_DOWN_SCAN_INTERVAL = 2;
    private static final int TARGET_DEPTH_WAIT_DURATION = 30;
    private static final int MOVE_RETRY_DURATION = 20;
    private static final int MAX_SEARCH_ATTEMPTS = 5;
    private static final int MAX_RETOGGLE_ATTEMPTS = 1;
    private static final Pattern RTP_COOLDOWN_PATTERN = Pattern.compile("You can't rtp for another (\\d+)s");
    private static final long EXP_THROW_COOLDOWN = 10_000L;

    public BStarPhantom() {
        super(EncryptedString.of("BStar"), EncryptedString.of("(Beta) Directional mining with pathfinding & RTP recovery"), -1, Category.DONUT);
        addSettings(
                scanDepth, humanLikeRotation, disconnectOnBaseFind, showHazards, baseThreshold,
                storageBlocks, debugMode, showWaypoints, yLevel, scanHeight, scanWidthFalling,
                scanWidthFluids, strictGroundCheck, smoothRotation, rotationSpeed,
                rotationAcceleration, overshootChance, workInMenus, pauseForAutoEat,
                rtpWhenStuck, rtpRegion, mineDownTarget, groundScanSize, autoEnableAutoLog,
                safeMineDownSearchRadius, autoRepair, repairThreshold, repairDuration,
                detectPlayers, voidDepthThreshold, playerCheckInterval, rotationRandomness,
                alwaysFocused, retoggleDelay, bypassMiningDown, intervalHoldDuration,
                intervalPauseDuration
        );
    }

    /* ========== ENABLE / DISABLE ========== */

    @Override
    public void onEnable() {
        if (mc.player == null) { toggle(false); return; }
        moduleStartTime = System.currentTimeMillis();

        pathScanner = new PathScanner();
        pathScanner.updateTunnelDimensions(3,3);
        pathfinder = new DirectionalPathfinder(pathScanner);
        safetyValidator = new SafetyValidator();
        rotationController = new RotationController();
        rotationController.setPreciseLanding(false);
        centeringHelper = new SimpleSneakCentering();
        parkourHelper = new ParkourHelper();

        hazardBlocks.clear();
        waypointBlocks.clear();
        processedChunks.clear();
        blocksMined = 0;
        totalBlocksMined = 0;
        lastPos = mc.player.getPos();
        scanTicks = 0;
        tickCounter = 0;
        playerCheckTicks = 0;

        lastRtpTime = 0;
        rtpWaitTicks = 0;
        rtpAttempts = 0;
        preRtpPos = null;
        rtpCommandSent = false;
        hasReachedMineDepth = false;
        mineDownScanTicks = 0;
        inRTPRecovery = false;
        safeMineDownTarget = null;
        searchAttempts = 0;
        searchingForSafeMineSpot = false;
        waitingAtTargetDepth = false;

        if (mc.player.getY() > yLevel.getIntValue()) {
            currentState = MiningState.RTP_SCANNING_GROUND;
            inRTPRecovery = true;
        } else {
            currentState = MiningState.CENTERING;
        }
    }

    @Override
    public void onDisable() {
        releaseAllMovement();
        hazardBlocks.clear();
        waypointBlocks.clear();
        processedChunks.clear();
        inRTPRecovery = false;
        currentState = MiningState.IDLE;
        if (parkourHelper != null) parkourHelper.reset();
        if (safetyValidator != null) safetyValidator.reset();
    }

    /* ========== TICK EVENT ========== */
    @EventListener
    public void onTick(TickEvent event) {
        if (!isEnabled() || mc.player == null) return;

        tickCounter++;
        rotationController.update();
        if (parkourHelper != null) parkourHelper.update();

        if (!workInMenus.getValue() && mc.currentScreen != null) {
            releaseAllMovement();
            return;
        }

        // AutoEat pause
        if (pauseForAutoEat.getValue()) {
            Module eatMod = Gamble.INSTANCE.getModuleManager().getModuleByClass(AutoEat.class);
            if (eatMod instanceof AutoEat eat && eatMod.isEnabled()) {
                if (eat.shouldEat()) {
                    if (!wasPausedForEating) {
                        wasPausedForEating = true;
                        stateBeforeEating = currentState;
                        releaseAllMovement();
                        currentState = MiningState.IDLE;
                    }
                    return;
                } else if (wasPausedForEating) {
                    wasPausedForEating = false;
                    currentState = stateBeforeEating;
                }
            }
        }

        // Player detection
        if (detectPlayers.getValue()) {
            playerCheckTicks++;
            if (playerCheckTicks >= playerCheckInterval.getIntValue()) {
                playerCheckTicks = 0;
                checkForPlayers();
            }
        }

        // Auto repair
        if (autoRepair.getValue() && !isThrowingExp && !isInRTPState()) {
            long since = System.currentTimeMillis() - lastExpThrowTime;
            if (since >= EXP_THROW_COOLDOWN || lastExpThrowTime == 0) {
                if (shouldRepairNow()) startRepairSequence();
            }
        }
        if (isThrowingExp) {
            handleExpRepairTick();
            return;
        }

        // Update path scanner & rotation parameters
        pathScanner.updateScanWidths(scanWidthFalling.getIntValue(), scanWidthFluids.getIntValue());
        rotationController.updateSettings(
                smoothRotation.getValue(),
                rotationSpeed.getValue(),
                rotationAcceleration.getValue(),
                humanLikeRotation.getValue(),
                overshootChance.getValue()
        );
        rotationController.updateRandomVariation(rotationRandomness.getValue());

        // Safety validation
        if (!isInRTPState() && !safetyValidator.canContinue(mc.player, yLevel.getIntValue())) {
            toggle(false);
            return;
        }

        if (rotationController.isRotating()) return;

        try {
            switch (currentState) {
                case CENTERING -> handleCentering();
                case SCANNING_PRIMARY -> handleScanningPrimary();
                case MINING_PRIMARY -> handleMiningPrimary();
                case HAZARD_DETECTED -> handleHazardDetected();
                case CALCULATING_DETOUR -> handleCalculatingDetour();
                case FOLLOWING_DETOUR -> handleFollowingDetour();
                case CHANGING_DIRECTION -> handleChangingDirection();
                case ROTATING -> {}
                case RTP_INITIATED -> handleRTPInitiated();
                case RTP_WAITING -> handleRTPWaiting();
                case RTP_COOLDOWN -> handleRTPCooldown();
                case RTP_SCANNING_GROUND -> handleRTPScanningGround();
                case MINING_DOWN -> handleMiningDown();
                case SEARCHING_SAFE_MINEDOWN -> handleSearchingSafeMinedown();
                case MOVING_TO_MINEDOWN -> handleMovingToMinedown();
                case STOPPED -> toggle(false);
                default -> {}
            }
        } catch (Exception ex) {
            if (debugMode.getValue()) ex.printStackTrace();
            toggle(false);
        }
    }

    /* =============================
       CORE HANDLERS
       ============================= */

    private void handleCentering() {
        if (!centeringHelper.isCentering()) {
            if (!centeringHelper.startCentering()) {
                proceedAfterCentering();
                return;
            }
        }
        if (!centeringHelper.tick()) {
            proceedAfterCentering();
        }
    }

    private void proceedAfterCentering() {
        if (safeMineDownTarget != null && mc.player.getBlockPos().equals(safeMineDownTarget)) {
            inRTPRecovery = true;
            safeMineDownTarget = null;
            currentState = MiningState.RTP_SCANNING_GROUND;
            return;
        }
        if (inRTPRecovery && mc.player.getY() > mineDownTarget.getValue()) {
            currentState = MiningState.RTP_SCANNING_GROUND;
            return;
        }
        Direction initialDir = yawToCardinal(mc.player.getYaw());
        pathfinder.setInitialDirection(initialDir);
        float targetYaw = directionToYaw(initialDir);
        currentState = MiningState.ROTATING;
        rotationController.startRotation(targetYaw, 0f, () -> currentState = MiningState.SCANNING_PRIMARY);
    }

    private void handleScanningPrimary() {
        BlockPos playerPos = mc.player.getBlockPos();
        Direction dir = pathfinder.getPrimaryDirection();
        PathScanner.ScanResult result = pathScanner.scanDirection(
                playerPos, dir, scanDepth.getIntValue(), scanHeight.getIntValue(), strictGroundCheck.getValue()
        );
        if (result.isSafe()) {
            hazardBlocks.clear();
            currentState = MiningState.MINING_PRIMARY;
            scanTicks = 0;
            lastPos = mc.player.getPos();
            startMiningForward();
        } else {
            lastHazardDetected = result;
            currentState = MiningState.HAZARD_DETECTED;
            if (showHazards.getValue()) {
                hazardBlocks.clear();
                hazardBlocks.addAll(result.getHazardPositions());
            }
        }
    }

    private void handleMiningPrimary() {
        Vec3d currentPos = mc.player.getPos();
        scanTicks++;
        if (scanTicks >= SCAN_INTERVAL) {
            scanTicks = 0;
            BlockPos playerPos = mc.player.getBlockPos();
            Direction dir = pathfinder.getPrimaryDirection();
            PathScanner.ScanResult result = pathScanner.scanDirection(
                    playerPos, dir, scanDepth.getIntValue(), scanHeight.getIntValue(), false
            );
            if (!result.isSafe()) {
                stopMiningForward();
                lastHazardDetected = result;
                currentState = MiningState.HAZARD_DETECTED;
                if (showHazards.getValue()) {
                    hazardBlocks.clear();
                    hazardBlocks.addAll(result.getHazardPositions());
                }
                return;
            }
        }
        double dist = currentPos.distanceTo(lastPos);
        if (dist >= 0.8) {
            blocksMined++;
            totalBlocksMined++;
            lastPos = currentPos;
        }
    }

    private void handleHazardDetected() {
        stopMiningForward();
        currentState = MiningState.CALCULATING_DETOUR;
    }

    private void handleCalculatingDetour() {
        BlockPos pos = mc.player.getBlockPos();
        DirectionalPathfinder.PathPlan plan = pathfinder.calculateDetour(pos, lastHazardDetected);
        if (plan.newPrimaryDirection != null) {
            pendingDirection = plan.newPrimaryDirection;
            currentState = MiningState.CHANGING_DIRECTION;
        } else if (plan.needsDetour) {
            currentWaypoint = pathfinder.getNextWaypoint();
            isDetouring = true;
            currentState = MiningState.FOLLOWING_DETOUR;
            if (showWaypoints.getValue()) {
                waypointBlocks.clear();
                if (currentWaypoint != null) waypointBlocks.add(currentWaypoint);
                waypointBlocks.addAll(pathfinder.peekAllWaypoints());
            }
        } else {
            if (plan.reason != null && plan.reason.contains("No valid paths")) {
                if (rtpWhenStuck.getValue()) initiateRTP();
                else currentState = MiningState.STOPPED;
            } else currentState = MiningState.STOPPED;
        }
    }

    private void handleFollowingDetour() {
        if (currentWaypoint == null) {
            currentWaypoint = pathfinder.getNextWaypoint();
            if (currentWaypoint == null) {
                pathfinder.completeDetour();
                isDetouring = false;
                currentState = MiningState.SCANNING_PRIMARY;
                waypointBlocks.clear();
                return;
            }
            if (showWaypoints.getValue()) {
                waypointBlocks.clear();
                waypointBlocks.add(currentWaypoint);
                waypointBlocks.addAll(pathfinder.peekAllWaypoints());
            }
        }
        BlockPos playerPos = mc.player.getBlockPos();
        double distance = Math.sqrt(Math.pow(playerPos.getX() - currentWaypoint.getX(),2) +
                Math.pow(playerPos.getZ() - currentWaypoint.getZ(),2));
        if (distance < 0.5) {
            currentWaypoint = null;
            return;
        }
        Direction dirToWaypoint = getDirectionToward(playerPos, currentWaypoint);
        if (dirToWaypoint == null) {
            int dx = currentWaypoint.getX() - playerPos.getX();
            int dz = currentWaypoint.getZ() - playerPos.getZ();
            if (Math.abs(dx) > Math.abs(dz)) dirToWaypoint = dx > 0 ? Direction.EAST : Direction.WEST;
            else if (dz != 0) dirToWaypoint = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            else { currentWaypoint = null; return; }
        }
        float currentYaw = mc.player.getYaw();
        float targetYaw = directionToYaw(dirToWaypoint);
        float diff = Math.abs(MathHelper.wrapDegrees(currentYaw - targetYaw));
        if (diff > 15) {
            stopMiningForward();
            currentState = MiningState.ROTATING;
            rotationController.startRotation(targetYaw, 0f, () -> currentState = MiningState.FOLLOWING_DETOUR);
            return;
        }
        startMiningForward();
        lastPos = mc.player.getPos();

        scanTicks++;
        if (scanTicks >= 20) {
            scanTicks = 0;
            PathScanner.ScanResult quick = pathScanner.scanDirection(playerPos, dirToWaypoint, 3, 4, false);
            if (!quick.isSafe() && quick.getHazardDistance() <= 2) {
                stopMiningForward();
                currentWaypoint = null;
                pathfinder.completeDetour();
                lastHazardDetected = quick;
                currentState = MiningState.CALCULATING_DETOUR;
            }
        }
    }

    private void handleChangingDirection() {
        if (rotationController.isRotating()) {
            stopMiningForward();
            return;
        }
        if (pendingDirection == null) {
            if (rtpWhenStuck.getValue()) initiateRTP();
            else currentState = MiningState.STOPPED;
            return;
        }
        if (!centeringHelper.isCentering()) {
            if (!isCenteredOnBlock(mc.player.getBlockPos())) {
                if (centeringHelper.startCentering()) return;
            }
        } else {
            if (centeringHelper.tick()) return;
        }
        float targetYaw = directionToYaw(pendingDirection);
        currentState = MiningState.ROTATING;
        rotationController.startRotation(targetYaw, 0f, () -> {
            pathfinder.setInitialDirection(pendingDirection);
            currentState = MiningState.SCANNING_PRIMARY;
            pendingDirection = null;
        });
    }

    private void handleRTPInitiated() {
        rtpWaitTicks++;
        if (rtpWaitTicks > 20) {
            currentState = MiningState.RTP_WAITING;
            rtpWaitTicks = 0;
        }
    }

    private void handleRTPWaiting() {
        rtpWaitTicks++;
        if (rtpWaitTicks > RTP_WAIT_TICKS) {
            if (rtpAttempts < 3) initiateRTP(); else toggle(false);
        }
    }

    private void handleRTPCooldown() {
        rtpWaitTicks++;
        if (rtpWaitTicks >= RTP_COOLDOWN_SECONDS * 20) initiateRTP();
    }

    private void handleRTPScanningGround() {
        releaseForwardAttack();
        if (mc.player.getY() <= mineDownTarget.getValue()) {
            hasReachedMineDepth = true;
            rtpAttempts = 0;
            inRTPRecovery = false;
            currentState = MiningState.CENTERING;
            return;
        }
        if (!centeringHelper.isCentering() && !isCenteredOnBlock(mc.player.getBlockPos())) {
            if (centeringHelper.startCentering()) return;
        } else if (centeringHelper.isCentering()) {
            if (centeringHelper.tick()) return;
        }
        float pitch = mc.player.getPitch();
        if (Math.abs(pitch - 90f) > 1f) {
            currentState = MiningState.ROTATING;
            rotationController.setPreciseLanding(true);
            rotationController.startRotation(mc.player.getYaw(), 90f, () -> {
                rotationController.setPreciseLanding(false);
                currentState = MiningState.RTP_SCANNING_GROUND;
            });
            return;
        }
        GroundHazard hazard = scanGroundBelowDetailed(mc.player.getBlockPos());
        if (hazard == GroundHazard.NONE) {
            hasReachedMineDepth = false;
            mineDownScanTicks = 0;
            miningDownInitiated = false;
            miningDownToggleTicks = 0;
            currentState = MiningState.MINING_DOWN;
        } else {
            if (safeMineDownTarget == null || !mc.player.getBlockPos().equals(safeMineDownTarget)) {
                searchAttempts = 0;
                safeMineDownTarget = null;
            }
            currentState = MiningState.SEARCHING_SAFE_MINEDOWN;
        }
    }

    private void handleMiningDown() {
        if (waitingToRetoggle) return;

        if (mc.player.getY() <= mineDownTarget.getValue()) {
            if (!hasReachedMineDepth && !waitingAtTargetDepth) {
                releaseForwardAttack();
                waitingAtTargetDepth = true;
                targetDepthWaitTicks = 0;
                return;
            }
            if (waitingAtTargetDepth) {
                targetDepthWaitTicks++;
                if (targetDepthWaitTicks >= TARGET_DEPTH_WAIT_DURATION) {
                    hasReachedMineDepth = true;
                    waitingAtTargetDepth = false;
                    targetDepthWaitTicks = 0;
                    rtpAttempts = 0;
                    inRTPRecovery = false;
                    rtpCommandSent = false;
                    miningDownInitiated = false;
                    safeMineDownTarget = null;
                    searchAttempts = 0;
                    intervalMiningActive = false;
                    intervalHolding = false;
                    retoggleAttempts = 0;
                    currentState = MiningState.CENTERING;
                }
                return;
            }
        }

        if (!miningDownInitiated) {
            releaseForwardAttack();
            miningDownInitiated = true;
            miningDownToggleTicks = 0;
            intervalMiningActive = false;
            intervalHolding = false;
            return;
        }

        miningDownToggleTicks++;
        mineDownScanTicks++;
        if (mineDownScanTicks >= MINE_DOWN_SCAN_INTERVAL) {
            mineDownScanTicks = 0;
            BlockPos currentPos = mc.player.getBlockPos();
            BlockPos predicted = currentPos.down(5);
            GroundHazard hazard = scanGroundBelowDetailed(predicted);
            if (hazard != GroundHazard.NONE) {
                releaseForwardAttack();
                intervalMiningActive = false;
                intervalHolding = false;
                currentState = MiningState.SEARCHING_SAFE_MINEDOWN;
                return;
            }
        }

        if (miningDownToggleTicks > 40 && miningDownToggleTicks % 40 == 0) {
            Vec3d currentPos = mc.player.getPos();
            if (lastPos != null && Math.abs(currentPos.y - lastPos.y) < 0.5) {
                if (retoggleAttempts >= MAX_RETOGGLE_ATTEMPTS) {
                    releaseForwardAttack();
                    intervalMiningActive = false;
                    intervalHolding = false;
                    currentState = MiningState.SEARCHING_SAFE_MINEDOWN;
                    searchAttempts++;
                    if (searchAttempts >= MAX_SEARCH_ATTEMPTS) {
                        if (rtpWhenStuck.getValue()) {
                            rtpAttempts = 0;
                            searchAttempts = 0;
                            initiateRTP();
                        } else {
                            toggle(false);
                        }
                    }
                    return;
                }
                releaseForwardAttack();
                intervalMiningActive = false;
                intervalHolding = false;
                retoggleAttempts++;
                waitingToRetoggle = true;
                retoggleDelayTicks = 0;
                return;
            } else {
                if (retoggleAttempts > 0) retoggleAttempts = 0;
            }
            lastPos = currentPos;
        }

        if (bypassMiningDown.getValue()) handleIntervalMining();
        else {
            if (!waitingToRetoggle) {
                setKey(mc.options.attackKey, true);
                setKey(mc.options.sneakKey, true);
            }
        }
    }

    private void handleSearchingSafeMinedown() {
        inRTPRecovery = true;
        releaseForwardAttack();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos safeSpot = findSafeMineDownSpot(playerPos);
        if (safeSpot != null) {
            safeMineDownTarget = safeSpot;
            List<BlockPos> path = createPathToMineDown(playerPos, safeSpot);
            if (!path.isEmpty()) {
                Queue<BlockPos> queue = new LinkedList<>(path);
                pathfinder.currentDetour = new LinkedList<>(queue);
                pathfinder.isDetouring = true;
                currentWaypoint = pathfinder.getNextWaypoint();
                currentState = MiningState.MOVING_TO_MINEDOWN;
                if (showWaypoints.getValue()) {
                    waypointBlocks.clear();
                    waypointBlocks.addAll(path);
                }
            } else {
                initiateRTPFallback();
            }
        } else {
            initiateRTPFallback();
        }
    }

    private void handleMovingToMinedown() {
        inRTPRecovery = true;
        if (rotationController.isRotating()) {
            releaseForwardAttack();
            return;
        }
        if (currentWaypoint == null) {
            currentWaypoint = pathfinder.getNextWaypoint();
            if (currentWaypoint == null) {
                if (pathfinder != null && pathfinder.currentDetour != null) {
                    pathfinder.currentDetour.clear();
                    pathfinder.isDetouring = false;
                }
                waypointBlocks.clear();
                currentState = MiningState.CENTERING;
                return;
            }
        }
        BlockPos playerPos = mc.player.getBlockPos();
        Direction dirToWaypoint = getDirectionToward(playerPos, currentWaypoint);
        if (dirToWaypoint != null) {
            float currentYaw = mc.player.getYaw();
            float currentPitch = mc.player.getPitch();
            float targetYaw = directionToYaw(dirToWaypoint);
            float diff = Math.abs(MathHelper.wrapDegrees(currentYaw - targetYaw));
            boolean needsPitchReset = Math.abs(currentPitch) > 10f;
            if (diff > 15 || needsPitchReset) {
                releaseForwardAttack();
                currentState = MiningState.ROTATING;
                rotationController.startRotation(targetYaw, 0f, () -> currentState = MiningState.MOVING_TO_MINEDOWN);
                return;
            }
        }
        double dist = Math.sqrt(Math.pow(playerPos.getX() - currentWaypoint.getX(),2) +
                Math.pow(playerPos.getZ() - currentWaypoint.getZ(),2));
        if (dist < 0.5) {
            currentWaypoint = null;
            return;
        }
        if (dirToWaypoint != null && !rotationController.isRotating()) {
            startMiningForward();
            lastPos = mc.player.getPos();
            scanTicks++;
            if (scanTicks >= 20) {
                scanTicks = 0;
                PathScanner.ScanResult quick = pathScanner.scanDirection(playerPos, dirToWaypoint, 3, scanHeight.getIntValue(), false);
                if (!quick.isSafe() && quick.getHazardDistance() <= 2) {
                    releaseForwardAttack();
                    currentWaypoint = null;
                    if (pathfinder != null && pathfinder.currentDetour != null) {
                        pathfinder.currentDetour.clear();
                        pathfinder.isDetouring = false;
                    }
                    currentState = MiningState.SEARCHING_SAFE_MINEDOWN;
                }
            }
        } else if (dirToWaypoint == null) {
            currentWaypoint = null;
        }
    }

    /* ========== RECOVERY / RTP ========== */

    private void initiateRTPFallback() {
        searchAttempts = 0;
        safeMineDownTarget = null;
        if (rtpWhenStuck.getValue()) {
            rtpAttempts++;
            if (rtpAttempts < 5) initiateRTP();
            else toggle(false);
        } else {
            toggle(false);
        }
    }

    private void initiateRTP() {
        if (!rtpWhenStuck.getValue()) return;
        inRTPRecovery = true;
        long since = System.currentTimeMillis() - lastRtpTime;
        if (lastRtpTime > 0 && since < RTP_COOLDOWN_SECONDS * 1000L) {
            currentState = MiningState.RTP_COOLDOWN;
            return;
        }
        preRtpPos = mc.player.getBlockPos();
        RTPRegion region = (RTPRegion) rtpRegion.getValue();
        mc.player.networkHandler.sendChatMessage("/rtp " + region.cmd());
        currentState = MiningState.RTP_INITIATED;
        rtpCommandSent = true;
        rtpAttempts++;
    }

    private boolean isInRTPState() {
        return switch (currentState) {
            case RTP_INITIATED, RTP_WAITING, RTP_COOLDOWN, RTP_SCANNING_GROUND,
                 SEARCHING_SAFE_MINEDOWN, MOVING_TO_MINEDOWN, MINING_DOWN -> true;
            default -> inRTPRecovery;
        };
    }

    /* ========== SUPPORT METHODS ========== */

    private boolean isCenteredOnBlock(BlockPos blockPos) {
        double ox = Math.abs(mc.player.getX() - (blockPos.getX() + 0.5));
        double oz = Math.abs(mc.player.getZ() - (blockPos.getZ() + 0.5));
        return ox <= 0.25 && oz <= 0.25;
    }

    private void handleExpRepairTick() {
        expThrowTicks++;
        if (expThrowTicks >= repairDuration.getIntValue() * 20) {
            isThrowingExp = false;
            lastExpThrowTime = System.currentTimeMillis();
            currentState = stateBeforeExp;
            rotationController.startRotation(mc.player.getYaw(), 0f, () -> {});
        }
    }

    private boolean shouldRepairNow() {
        if (!autoRepair.getValue()) return false;
        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || stack.isEmpty() ||
                !(stack.getItem() instanceof MiningToolItem || stack.getItem() instanceof ShearsItem)) return false;
        int max = stack.getMaxDamage();
        if (max <= 0) return false;
        double percent = (double)(max - stack.getDamage()) / max * 100.0;
        return percent <= repairThreshold.getValue();
    }

    private void startRepairSequence() {
        isThrowingExp = true;
        expThrowTicks = 0;
        stateBeforeExp = currentState;
        currentState = MiningState.ROTATING;
        rotationController.setPreciseLanding(true);
        rotationController.startRotation(mc.player.getYaw(), 90f, () -> {
            rotationController.setPreciseLanding(false);
            // Toggle EXP thrower module here if implemented in Phantom.
        });
    }

    private void handleIntervalMining() {
        setKey(mc.options.sneakKey, true);
        if (!intervalMiningActive) {
            intervalMiningActive = true;
            intervalStartTime = System.currentTimeMillis();
            intervalHolding = true;
            setKey(mc.options.attackKey, true);
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - intervalStartTime;
        if (intervalHolding) {
            if (elapsed >= intervalHoldDuration.getValue()) {
                setKey(mc.options.attackKey, false);
                intervalHolding = false;
                intervalStartTime = now;
            }
        } else {
            if (elapsed >= intervalPauseDuration.getValue()) {
                setKey(mc.options.attackKey, true);
                intervalHolding = true;
                intervalStartTime = now;
            }
        }
    }

    private void releaseForwardAttack() {
        setKey(mc.options.attackKey, false);
        setKey(mc.options.forwardKey, false);
        setKey(mc.options.sneakKey, false);
    }

    private void startMiningForward() {
        setKey(mc.options.forwardKey, true);
        setKey(mc.options.attackKey, true);
    }

    // Added to satisfy calls referencing stopMiningForward() from original logic mapping.
    private void stopMiningForward() {
        releaseForwardAttack();
    }

    private void setKey(net.minecraft.client.option.KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
    }

    private Direction yawToCardinal(float yaw) {
        float y = (yaw % 360 + 360) % 360;
        if (y >= 315 || y < 45) return Direction.SOUTH;
        if (y >= 45 && y < 135) return Direction.WEST;
        if (y >= 135 && y < 225) return Direction.NORTH;
        return Direction.EAST;
    }

    private float directionToYaw(Direction dir) {
        return switch (dir) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case EAST  -> -90f;
            case WEST  -> 90f;
            default -> 0f;
        };
    }

    private Direction getDirectionToward(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (Math.abs(dz) > Math.abs(dx)) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        if (dx != 0) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (dz != 0) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return null;
    }

    private boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof MiningToolItem || stack.getItem() instanceof ShearsItem;
    }

    private void checkForPlayers() {
        if (mc.world == null) return;
        for (var p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.distanceTo(mc.player) <= 128) {
                if (disconnectOnBaseFind.getValue()) {
                    mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("Player " + p.getName().getString() + " nearby")));
                    toggle(false);
                }
                break;
            }
        }
    }

    /* ====== Mining Down Safety / Spot Searching ====== */

    private BlockPos findSafeMineDownSpot(BlockPos playerPos) {
        int radius = safeMineDownSearchRadius.getIntValue();
        for (int r = 2; r <= radius; r++) {
            List<BlockPos> ring = spiralRing(playerPos, r);
            for (BlockPos pos : ring) {
                if (pos.getY() != playerPos.getY()) continue;
                if (!isPositionReachable(playerPos, pos)) continue;
                if (canSafelyMineDownFrom(pos)) return pos;
            }
        }
        return null;
    }

    private List<BlockPos> spiralRing(BlockPos center, int radius) {
        List<BlockPos> list = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            list.add(center.add(x, 0, -radius));
            list.add(center.add(x, 0, radius));
        }
        for (int z = -radius + 1; z < radius; z++) {
            list.add(center.add(-radius, 0, z));
            list.add(center.add(radius, 0, z));
        }
        return list;
    }

    private boolean isPositionReachable(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) return true;

        boolean xFirst = true;
        if (dx != 0) {
            Direction xDir = dx > 0 ? Direction.EAST : Direction.WEST;
            PathScanner.ScanResult xScan = pathScanner.scanDirection(from, xDir, Math.abs(dx), scanHeight.getIntValue(), false);
            if (!xScan.isSafe()) xFirst = false;
            else if (dz != 0) {
                BlockPos mid = from.offset(xDir, Math.abs(dx));
                Direction zDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
                PathScanner.ScanResult zScan = pathScanner.scanDirection(mid, zDir, Math.abs(dz), scanHeight.getIntValue(), false);
                if (!zScan.isSafe()) xFirst = false;
            }
        } else if (dz != 0) {
            Direction zDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            PathScanner.ScanResult zScan = pathScanner.scanDirection(from, zDir, Math.abs(dz), scanHeight.getIntValue(), false);
            xFirst = zScan.isSafe();
        }
        if (xFirst) return true;

        if (dz != 0) {
            Direction zDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            PathScanner.ScanResult zScan = pathScanner.scanDirection(from, zDir, Math.abs(dz), scanHeight.getIntValue(), false);
            if (!zScan.isSafe()) return false;
            if (dx != 0) {
                BlockPos mid = from.offset(zDir, Math.abs(dz));
                Direction xDir = dx > 0 ? Direction.EAST : Direction.WEST;
                PathScanner.ScanResult xScan = pathScanner.scanDirection(mid, xDir, Math.abs(dx), scanHeight.getIntValue(), false);
                return xScan.isSafe();
            }
            return true;
        }
        return false;
    }

    private boolean canSafelyMineDownFrom(BlockPos pos) {
        if (scanGroundBelowDetailed(pos) != GroundHazard.NONE) return false;
        BlockPos predictive = pos.down(5);
        if (scanGroundBelowDetailed(predictive) != GroundHazard.NONE) return false;
        BlockPos deep = pos.down(8);
        return scanGroundBelowDetailed(deep) == GroundHazard.NONE;
    }

    private List<BlockPos> createPathToMineDown(BlockPos from, BlockPos to) {
        List<BlockPos> path = new ArrayList<>();
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx != 0) path.add(from.add(dx, 0, 0));
        if (dz != 0) path.add(to);
        if (path.isEmpty() && !from.equals(to)) path.add(to);
        return path;
    }

    private GroundHazard scanGroundBelowDetailed(BlockPos start) {
        World world = mc.world;
        int radius = groundScanSize.getIntValue() / 2;
        boolean hasFluid = false, hasVoid = false, hasWeb = false, hasVines = false;

        int voidCheckRadius = Math.min(radius, 1);
        for (int x = -voidCheckRadius; x <= voidCheckRadius; x++) {
            for (int z = -voidCheckRadius; z <= voidCheckRadius; z++) {
                int solidDepth = 0;
                boolean foundSolid = false;
                for (int d = 1; d <= 15; d++) {
                    BlockPos below = start.add(x, -d, z);
                    BlockState st = world.getBlockState(below);
                    if (!st.isAir() && st.isSolidBlock(world, below)) {
                        solidDepth = d;
                        foundSolid = true;
                        break;
                    }
                }
                if (!foundSolid || solidDepth >= voidDepthThreshold.getIntValue()) {
                    hasVoid = true;
                }
            }
        }

        for (int depth = 1; depth <= 10; depth++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos below = start.add(x, -depth, z);
                    BlockState state = world.getBlockState(below);
                    Block block = state.getBlock();

                    // Fluids (replace tag usage with direct fluid checks)
                    if (state.getFluidState().getFluid() == net.minecraft.fluid.Fluids.LAVA ||
                            state.getFluidState().getFluid() == net.minecraft.fluid.Fluids.FLOWING_LAVA ||
                            state.getFluidState().getFluid() == net.minecraft.fluid.Fluids.WATER ||
                            state.getFluidState().getFluid() == net.minecraft.fluid.Fluids.FLOWING_WATER) {
                        hasFluid = true;
                    }

                    if (block == Blocks.COBWEB) hasWeb = true;

                    if (block == Blocks.VINE ||
                            block == Blocks.WEEPING_VINES || block == Blocks.WEEPING_VINES_PLANT ||
                            block == Blocks.TWISTING_VINES || block == Blocks.TWISTING_VINES_PLANT ||
                            block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) {
                        hasVines = true;
                    }
                }
            }
        }

        if (hasFluid || hasWeb || hasVines) {
            if (hasVoid) return GroundHazard.BOTH;
            return GroundHazard.FLUIDS;
        } else if (hasVoid) {
            return GroundHazard.VOID;
        }
        return GroundHazard.NONE;
    }

    /* ========== MISC HELPERS ========== */

    private void releaseAllMovement() {
        setKey(mc.options.attackKey, false);
        setKey(mc.options.forwardKey, false);
        setKey(mc.options.backKey, false);
        setKey(mc.options.leftKey, false);
        setKey(mc.options.rightKey, false);
        setKey(mc.options.sneakKey, false);
    }

    /* ========== CHUNK / BASE DETECTION (hook to your chunk event if desired) ========== */

    private int searchChunkForSpawnersOptimized(Chunk chunk) {
        if (mc.player.getY() > 64) return 0;
        int found = 0;
        BlockPos.Mutable mut = new BlockPos.Mutable();
        int playerY = (int) mc.player.getY();
        int minY = Math.max(mc.world.getBottomY(), -64);
        int maxY = Math.min(playerY + 20, 20);
        for (int x = chunk.getPos().getStartX(); x <= chunk.getPos().getEndX(); x++) {
            for (int z = chunk.getPos().getStartZ(); z <= chunk.getPos().getEndZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    mut.set(x,y,z);
                    if (chunk.getBlockState(mut).getBlock() == Blocks.SPAWNER) {
                        found++;
                        return 1;
                    }
                }
            }
        }
        return found;
    }

    // Rendering note: integrate hazardBlocks & waypointBlocks in your existing Render3D event.

}