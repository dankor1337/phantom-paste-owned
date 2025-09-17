package dev.gambleclient.module.modules.ai;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.*;

public class DirectionalPathfinder {
    private Direction primaryDirection;
    private Direction originalPrimaryDirection;
    public Queue<BlockPos> currentDetour = new LinkedList<>();
    public boolean isDetouring = false;
    private final PathScanner pathScanner;

    private static final int APPROACH_STOP_DISTANCE = 7;
    private static final int DETOUR_START_BUFFER = 5;
    private static final int DETOUR_SIDE_CLEARANCE = 2;

    private int detourCount = 0;
    private int directionChangeCount = 0;
    private String lastDecisionReason = "";

    public DirectionalPathfinder(PathScanner scanner) {
        this.pathScanner = scanner;
    }

    public void setInitialDirection(Direction dir) {
        this.primaryDirection = dir;
        this.originalPrimaryDirection = dir;
    }

    public Direction getPrimaryDirection() {
        return primaryDirection;
    }

    public boolean isDetouring() {
        return isDetouring;
    }

    public Queue<BlockPos> peekAllWaypoints() {
        return new LinkedList<>(currentDetour);
    }

    public BlockPos getNextWaypoint() {
        if (currentDetour.isEmpty()) {
            isDetouring = false;
            return null;
        }
        return currentDetour.poll();
    }

    public BlockPos peekNextWaypoint() {
        return currentDetour.peek();
    }

    public static class PathPlan {
        public final boolean needsDetour;
        public final Queue<BlockPos> waypoints;
        public final String reason;
        public final Direction newPrimaryDirection;

        public PathPlan(boolean needsDetour, Queue<BlockPos> waypoints, String reason) {
            this.needsDetour = needsDetour;
            this.waypoints = waypoints;
            this.reason = reason;
            this.newPrimaryDirection = null;
        }

        public PathPlan(Direction newDirection, String reason) {
            this.needsDetour = false;
            this.waypoints = new LinkedList<>();
            this.reason = reason;
            this.newPrimaryDirection = newDirection;
        }
    }

    public PathPlan calculateDetour(BlockPos playerPos, PathScanner.ScanResult hazard) {
        if (hazard.getHazardDistance() > 10) {
            return createApproachPlan(playerPos, hazard);
        }

        HazardBounds bounds = scanHazardBoundaries(playerPos, hazard);

        List<BlockPos> leftPath = buildMinimalDetourPath(playerPos, bounds, true);
        List<BlockPos> rightPath = buildMinimalDetourPath(playerPos, bounds, false);

        if (leftPath != null && rightPath != null) {
            if (leftPath.size() <= rightPath.size()) {
                return createDetourPlan(leftPath, "Left detour");
            } else {
                return createDetourPlan(rightPath, "Right detour");
            }
        } else if (leftPath != null) {
            return createDetourPlan(leftPath, "Left detour");
        } else if (rightPath != null) {
            return createDetourPlan(rightPath, "Right detour");
        } else {
            return handleCompletelyBlocked(playerPos);
        }
    }

    private PathPlan createApproachPlan(BlockPos playerPos, PathScanner.ScanResult hazard) {
        int safeApproachDistance = Math.max(hazard.getHazardDistance() - APPROACH_STOP_DISTANCE, 0);
        if (safeApproachDistance <= 0) {
            return new PathPlan(false, new LinkedList<>(), "Already close to hazard");
        }
        BlockPos approachPoint = playerPos.offset(primaryDirection, safeApproachDistance);
        if (!validateSegment(playerPos, approachPoint, primaryDirection, safeApproachDistance)) {
            return handleCompletelyBlocked(playerPos);
        }
        List<BlockPos> approachPath = new ArrayList<>();
        approachPath.add(approachPoint);
        Queue<BlockPos> waypoints = new LinkedList<>(approachPath);
        currentDetour = new LinkedList<>(waypoints);
        isDetouring = true;
        return new PathPlan(true, waypoints, "Approaching distant hazard");
    }

    private List<BlockPos> buildMinimalDetourPath(BlockPos playerPos, HazardBounds bounds, boolean goLeft) {
        Direction sideDir = goLeft ? primaryDirection.rotateYCounterclockwise() : primaryDirection.rotateYClockwise();
        int sideDistance = (bounds.width / 2) + DETOUR_SIDE_CLEARANCE;
        for (int attempt = 0; attempt < 3; attempt++) {
            List<BlockPos> path = tryDetourPath(playerPos, bounds, sideDir, sideDistance + attempt);
            if (path != null) return path;
        }
        return null;
    }

    private List<BlockPos> tryDetourPath(BlockPos playerPos, HazardBounds bounds, Direction sideDir, int sideDistance) {
        List<BlockPos> waypoints = new ArrayList<>();
        int approachDistance = Math.max(bounds.startDistance - DETOUR_START_BUFFER, 1);
        int forwardPastHazard = bounds.depth + 3;

        BlockPos turnPoint = adjustToGroundLevel(playerPos.offset(primaryDirection, approachDistance));
        if (!validateSegment(playerPos, turnPoint, primaryDirection, approachDistance)) return null;
        waypoints.add(turnPoint);

        BlockPos sidePoint = adjustToGroundLevel(turnPoint.offset(sideDir, sideDistance));
        if (!validateSegment(turnPoint, sidePoint, sideDir, sideDistance)) return null;
        waypoints.add(sidePoint);

        BlockPos pastHazard = adjustToGroundLevel(sidePoint.offset(primaryDirection, forwardPastHazard));
        if (!validateSegment(sidePoint, pastHazard, primaryDirection, forwardPastHazard)) return null;
        waypoints.add(pastHazard);
        return waypoints;
    }

    private BlockPos adjustToGroundLevel(BlockPos pos) {
        PathScanner.ScanResult currentScan = pathScanner.scanDirection(pos, primaryDirection, 0, 1, false);
        if (currentScan.getHazardType() == PathScanner.HazardType.UNSAFE_GROUND || !isGroundSolid(pos)) {
            BlockPos oneDown = pos.down();
            if (isGroundSolid(oneDown)) return oneDown;
            BlockPos twoDown = pos.down(2);
            if (isGroundSolid(twoDown)) return twoDown;
        }
        BlockPos oneUp = pos.up();
        PathScanner.ScanResult upScan = pathScanner.scanDirection(oneUp, primaryDirection, 0, 1, false);
        if (upScan.isSafe() && !isGroundSolid(pos) && isGroundSolid(pos.down())) {
            return oneUp;
        }
        return pos;
    }

    private boolean isGroundSolid(BlockPos pos) {
        PathScanner.ScanResult groundCheck = pathScanner.scanDirection(pos.up(), primaryDirection, 0, 1, false);
        return groundCheck.isSafe() ||
                (groundCheck.getHazardType() != PathScanner.HazardType.LAVA &&
                        groundCheck.getHazardType() != PathScanner.HazardType.WATER &&
                        groundCheck.getHazardType() != PathScanner.HazardType.UNSAFE_GROUND);
    }

    private boolean validateSegment(BlockPos start, BlockPos end, Direction moveDir, int distance) {
        if (distance > 10) {
            BlockPos currentPos = start;
            for (int chunk = 0; chunk < distance; chunk += 5) {
                int chunkSize = Math.min(5, distance - chunk);
                for (int i = 1; i <= chunkSize; i++) {
                    BlockPos checkPos = currentPos.offset(moveDir, i);
                    int scanAhead = (distance > 10) ? 2 : 1;
                    PathScanner.ScanResult scan = pathScanner.scanDirection(checkPos, moveDir, scanAhead, 4, false);
                    if (!scan.isSafe()) return false;
                    if (!checkGroundSafety(checkPos)) return false;
                }
                currentPos = currentPos.offset(moveDir, chunkSize);
            }
            return true;
        }

        for (int i = 1; i <= distance; i++) {
            BlockPos checkPos = start.offset(moveDir, i);
            PathScanner.ScanResult scan = pathScanner.scanDirection(checkPos, moveDir, 1, 4, false);
            if (!scan.isSafe()) return false;
            if (moveDir != primaryDirection && i == distance) {
                PathScanner.ScanResult forwardCheck = pathScanner.scanDirection(checkPos, primaryDirection, 2, 4, false);
                if (!forwardCheck.isSafe() && forwardCheck.getHazardDistance() <= 1) return false;
            }
            if (!checkGroundSafety(checkPos)) return false;
        }
        return true;
    }

    private boolean checkGroundSafety(BlockPos pos) {
        for (int depth = 1; depth <= 2; depth++) {
            BlockPos below = pos.down(depth);
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                PathScanner.ScanResult scan = pathScanner.scanDirection(below, dir, 0, 1, false);
                if (!scan.isSafe() && scan.getHazardDistance() == 0 &&
                        scan.getHazardType() == PathScanner.HazardType.LAVA) {
                    return false;
                }
            }
        }
        return true;
    }

    private PathPlan createDetourPlan(List<BlockPos> waypoints, String reason) {
        Queue<BlockPos> waypointQueue = new LinkedList<>(waypoints);
        currentDetour = new LinkedList<>(waypointQueue);
        isDetouring = true;
        detourCount++;
        lastDecisionReason = "Detour #" + detourCount + ": " + reason;
        return new PathPlan(true, waypointQueue, lastDecisionReason);
    }

    private HazardBounds scanHazardBoundaries(BlockPos playerPos, PathScanner.ScanResult initialHazard) {
        int hazardDistance = initialHazard.getHazardDistance();
        BlockPos hazardCenter = playerPos.offset(primaryDirection, hazardDistance);

        int leftWidth = 0, rightWidth = 0, forwardDepth = 0;
        Direction leftDir = primaryDirection.rotateYCounterclockwise();
        Direction rightDir = primaryDirection.rotateYClockwise();

        for (int i = 1; i <= 10; i++) {
            BlockPos checkPos = hazardCenter.offset(leftDir, i);
            PathScanner.ScanResult scan = pathScanner.scanDirection(checkPos, primaryDirection, 1, 4, false);
            if (scan.isSafe()) break;
            leftWidth = i;
        }
        for (int i = 1; i <= 10; i++) {
            BlockPos checkPos = hazardCenter.offset(rightDir, i);
            PathScanner.ScanResult scan = pathScanner.scanDirection(checkPos, primaryDirection, 1, 4, false);
            if (scan.isSafe()) break;
            rightWidth = i;
        }
        for (int i = 0; i <= 20; i++) {
            BlockPos checkPos = hazardCenter.offset(primaryDirection, i);
            PathScanner.ScanResult scan = pathScanner.scanDirection(checkPos, primaryDirection, 1, 4, false);
            if (scan.isSafe()) break;
            forwardDepth = i;
        }

        int totalWidth = leftWidth + rightWidth + 1;
        int totalDepth = Math.max(forwardDepth, 1);
        return new HazardBounds(hazardDistance, totalWidth, totalDepth, hazardCenter);
    }

    private PathPlan handleCompletelyBlocked(BlockPos playerPos) {
        Direction[] alternatives = {
                primaryDirection.rotateYClockwise(),
                primaryDirection.rotateYCounterclockwise()
        };
        for (Direction newDir : alternatives) {
            PathScanner.ScanResult scan = pathScanner.scanDirection(playerPos, newDir, 20, 4, false);
            if (scan.isSafe()) {
                directionChangeCount++;
                lastDecisionReason = "Perpendicular direction change #" + directionChangeCount;
                return new PathPlan(newDir, lastDecisionReason);
            }
        }
        return new PathPlan((Direction) null, "No valid paths - RTP recovery needed");
    }

    public void completeDetour() {
        isDetouring = false;
        currentDetour.clear();
    }

    public String getDebugInfo() {
        if (primaryDirection == null) {
            return "Primary: NOT SET | Pathfinder not initialized";
        }
        return String.format(
                "Primary: %s (original: %s) | Detouring: %s | Detours: %d | Changes: %d | Reason: %s",
                primaryDirection.getName(),
                originalPrimaryDirection != null ? originalPrimaryDirection.getName() : "NOT SET",
                isDetouring,
                detourCount,
                directionChangeCount,
                lastDecisionReason
        );
    }

    private static class HazardBounds {
        final int startDistance;
        final int width;
        final int depth;
        final BlockPos center;
        HazardBounds(int startDistance, int width, int depth, BlockPos center) {
            this.startDistance = startDistance;
            this.width = width;
            this.depth = depth;
            this.center = center;
        }
    }
}