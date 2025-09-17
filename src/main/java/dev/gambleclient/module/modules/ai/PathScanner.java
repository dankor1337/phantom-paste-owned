// (Full logic preserved; only package & minor adapt comments)
package dev.gambleclient.module.modules.ai;

import net.minecraft.client.MinecraftClient;
import net.minecraft.block.*;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import java.util.HashSet;
import java.util.Set;

public class PathScanner {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private int scanWidthFallingBlocks = 2;
    private int scanWidthFluids = 4;
    private int tunnelWidth = 3;
    private int tunnelHeight = 3;

    public void updateScanWidths(int fallingBlocks, int fluids) {
        this.scanWidthFallingBlocks = fallingBlocks;
        this.scanWidthFluids = fluids;
    }
    public void updateTunnelDimensions(int width, int height) {
        this.tunnelWidth = width;
        this.tunnelHeight = height;
    }

    public static class ScanResult {
        private final boolean safe;
        private final HazardType hazardType;
        private final int hazardDistance;
        private final Set<BlockPos> hazardPositions;
        public ScanResult(boolean safe, HazardType hazardType, int hazardDistance) {
            this(safe, hazardType, hazardDistance, new HashSet<>());
        }
        public ScanResult(boolean safe, HazardType hazardType, int hazardDistance, Set<BlockPos> hazardPositions) {
            this.safe = safe;
            this.hazardType = hazardType;
            this.hazardDistance = hazardDistance;
            this.hazardPositions = hazardPositions;
        }
        public boolean isSafe() { return safe; }
        public HazardType getHazardType() { return hazardType; }
        public int getHazardDistance() { return hazardDistance; }
        public Set<BlockPos> getHazardPositions() { return hazardPositions; }
    }

    public enum HazardType {
        NONE,
        LAVA,
        WATER,
        FALLING_BLOCK,
        UNSAFE_GROUND,
        DANGEROUS_BLOCK,
        SANDWICH_TRAP
    }

    public ScanResult scanDirection(BlockPos start, Direction direction, int depth, int height, boolean strictGround) {
        World world = mc.world;
        Set<BlockPos> detectedHazards = new HashSet<>();

        SandwichTrapResult trapResult = checkForSandwichTraps(start, direction, depth);
        if (trapResult.found) {
            detectedHazards.add(trapResult.hazardPos);
            return new ScanResult(false, HazardType.SANDWICH_TRAP, trapResult.distance, detectedHazards);
        }

        int expectedYLevel = 0;
        for (int forward = 1; forward <= depth; forward++) {
            // Fluid scan
            for (int sideways = -scanWidthFluids; sideways <= scanWidthFluids; sideways++) {
                for (int vertical = -1; vertical <= height; vertical++) {
                    if (vertical < 0 && sideways != 0) continue;
                    BlockPos checkPos = offsetPosition(start, direction, forward, sideways, expectedYLevel + vertical);
                    BlockState state = world.getBlockState(checkPos);
                    HazardType fluidHazard = checkForFluids(state);
                    if (fluidHazard != HazardType.NONE) {
                        detectedHazards.add(checkPos.toImmutable());
                        return new ScanResult(false, fluidHazard, forward, detectedHazards);
                    }
                }
            }

            // Other hazards
            for (int sideways = -scanWidthFallingBlocks; sideways <= scanWidthFallingBlocks; sideways++) {
                for (int vertical = -1; vertical <= height; vertical++) {
                    BlockPos checkPos = offsetPosition(start, direction, forward, sideways, expectedYLevel + vertical);
                    boolean checkFalling = (sideways == 0 && vertical == 3);
                    HazardType hazard = checkBlock(world, checkPos, vertical, strictGround, forward, sideways, checkFalling);
                    if (hazard != HazardType.NONE) {
                        if (hazard == HazardType.UNSAFE_GROUND && forward == 1 && vertical == -1) {
                            boolean foundGround = false;
                            for (int i = 0; i <= 2; i++) {
                                if (canWalkOn(world, checkPos.down(i), world.getBlockState(checkPos.down(i)))) {
                                    foundGround = true;
                                    break;
                                }
                            }
                            if (foundGround) continue;
                        }
                        detectedHazards.add(checkPos.toImmutable());
                        return new ScanResult(false, hazard, forward, detectedHazards);
                    }
                }
            }
        }
        return new ScanResult(true, HazardType.NONE, -1, detectedHazards);
    }

    private static class SandwichTrapResult {
        final boolean found;
        final int distance;
        final BlockPos hazardPos;
        SandwichTrapResult(boolean found, int distance, BlockPos hazardPos) {
            this.found = found;
            this.distance = distance;
            this.hazardPos = hazardPos;
        }
    }

    private SandwichTrapResult checkForSandwichTraps(BlockPos start, Direction direction, int maxDepth) {
        World world = mc.world;
        BlockPos ceilingPos = start.up(2);
        BlockPos footAhead = start.offset(direction);
        BlockPos headAhead = footAhead.up();
        if (!world.getBlockState(ceilingPos).isAir() &&
                !world.getBlockState(footAhead).isAir() &&
                world.getBlockState(headAhead).isAir()) {
            return new SandwichTrapResult(true, 1, footAhead);
        }
        BlockPos footAheadP2 = start.offset(direction);
        BlockPos headAheadP2 = footAheadP2.up();
        BlockPos ceilingAheadP2 = headAheadP2.up();
        if (!world.getBlockState(footAheadP2).isAir() &&
                world.getBlockState(headAheadP2).isAir() &&
                !world.getBlockState(ceilingAheadP2).isAir()) {
            return new SandwichTrapResult(true, 1, footAheadP2);
        }
        for (int distance = 2; distance <= Math.min(maxDepth, 8); distance++) {
            BlockPos checkPos = start.offset(direction, distance);
            BlockPos p1Ceiling = checkPos.up(2);
            BlockPos p1FootAhead = checkPos.offset(direction);
            BlockPos p1HeadAhead = p1FootAhead.up();
            if (!world.getBlockState(p1Ceiling).isAir() &&
                    !world.getBlockState(p1FootAhead).isAir() &&
                    world.getBlockState(p1HeadAhead).isAir()) {
                return new SandwichTrapResult(true, distance + 1, p1FootAhead);
            }
            BlockPos p2Foot = checkPos;
            BlockPos p2Head = p2Foot.up();
            BlockPos p2Ceiling = p2Head.up();
            if (!world.getBlockState(p2Foot).isAir() &&
                    world.getBlockState(p2Head).isAir() &&
                    !world.getBlockState(p2Ceiling).isAir()) {
                return new SandwichTrapResult(true, distance, p2Foot);
            }
        }
        return new SandwichTrapResult(false, -1, null);
    }

    private HazardType checkForFluids(BlockState state) {
        if (state.getBlock() == Blocks.LAVA ||
                state.getFluidState().getFluid() == Fluids.LAVA ||
                state.getFluidState().getFluid() == Fluids.FLOWING_LAVA) return HazardType.LAVA;
        if (state.getBlock() == Blocks.WATER ||
                state.getFluidState().getFluid() == Fluids.WATER ||
                state.getFluidState().getFluid() == Fluids.FLOWING_WATER) return HazardType.WATER;
        return HazardType.NONE;
    }

    private HazardType checkBlock(World world, BlockPos pos, int yOffset, boolean strictGround,
                                  int forwardDist, int sidewaysDist, boolean checkFalling) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (yOffset == -1 && sidewaysDist == 0) {
            if (strictGround) {
                if (!canWalkOn(world, pos, state)) return HazardType.UNSAFE_GROUND;
            } else {
                if (!canWalkOn(world, pos, state)) {
                    boolean safeDropFound = false;
                    for (int i = 1; i <= 2; i++) {
                        BlockPos belowPos = pos.down(i);
                        BlockState belowState = world.getBlockState(belowPos);
                        if (canWalkOn(world, belowPos, belowState)) {
                            safeDropFound = true;
                            break;
                        }
                    }
                    if (!safeDropFound) return HazardType.UNSAFE_GROUND;
                }
            }
        }

        if (checkFalling && isFallingBlock(block)) {
            return HazardType.FALLING_BLOCK;
        }
        if (isDangerousBlock(block)) {
            return HazardType.DANGEROUS_BLOCK;
        }
        return HazardType.NONE;
    }

    public boolean hasDropAhead(BlockPos playerPos, Direction direction) {
        World world = mc.world;
        BlockPos frontGround = playerPos.offset(direction).down();
        BlockState frontGroundState = world.getBlockState(frontGround);
        BlockPos frontFoot = playerPos.offset(direction);
        BlockState frontFootState = world.getBlockState(frontFoot);
        if (frontFootState.isAir() && !frontGroundState.isAir()) {
            BlockPos twoBelow = frontGround.down();
            return world.getBlockState(twoBelow).isAir();
        }
        return false;
    }

    private boolean canWalkOn(World world, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (!state.isSolidBlock(world, pos)) return false;
        Block block = state.getBlock();
        if (block == Blocks.MAGMA_BLOCK || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) return false;
        return state.isFullCube(world, pos) ||
                block instanceof SlabBlock ||
                block instanceof StairsBlock;
    }

    private boolean isFallingBlock(Block block) {
        return block == Blocks.SAND ||
                block == Blocks.RED_SAND ||
                block == Blocks.GRAVEL ||
                block == Blocks.ANVIL ||
                block == Blocks.CHIPPED_ANVIL ||
                block == Blocks.DAMAGED_ANVIL;
    }

    private boolean isDangerousBlock(Block block) {
        return block == Blocks.TNT ||
                block == Blocks.FIRE ||
                block == Blocks.SOUL_FIRE ||
                block == Blocks.MAGMA_BLOCK ||
                block == Blocks.WITHER_ROSE ||
                block == Blocks.SWEET_BERRY_BUSH ||
                block == Blocks.POWDER_SNOW ||
                block == Blocks.CACTUS ||
                block == Blocks.INFESTED_DEEPSLATE ||
                block == Blocks.SCULK ||
                block == Blocks.SMALL_AMETHYST_BUD ||
                block == Blocks.MEDIUM_AMETHYST_BUD ||
                block == Blocks.LARGE_AMETHYST_BUD ||
                block == Blocks.AMETHYST_CLUSTER ||
                block == Blocks.INFESTED_STONE;
    }

    private BlockPos offsetPosition(BlockPos start, Direction forward, int forwardDist, int sidewaysDist, int verticalDist) {
        Direction left = forward.rotateYCounterclockwise();
        return start
                .offset(forward, forwardDist)
                .offset(left, sidewaysDist)
                .up(verticalDist);
    }
}