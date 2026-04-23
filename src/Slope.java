/**
 * src/Slope.java
 *
 * Generates and manages the track segments for the Slope game. The slope is composed of
 * contiguous segments that can be classified as downhill, ramp, corkscrew, or gap segments.
 * Each segment owns a list of Plane objects that define its renderable geometry.
 *
 * Sources used:
 * https://docs.oracle.com/javase/8/docs/api/java/util/Random.html for random segment generation.
 * ChatGPT 5.4 for initial structure and design of files.
 * Claude Sonnet 4.6 for segment generation logic and improvements to the Slope class design,
 *      including the addition of a SegmentType enum.
 *
 * @since 2026-03-29
 */
import java.util.*;

public final class Slope {

    private final double nearZ = 12.0;
    private final double startZ = 8.0;
    private final double startHeight = 1.8;

    private final Random random = new Random();
    private final List<Segment> segments = new ArrayList<>();
    private final List<HazardBlock> hazards = new ArrayList<>();

    private double generatedFarZ;
    private double generatedFarY;
    private double baselineFarY;
    private double currentCenterX;
    private double pendingCenterX;
    private int pendingGapSegments = 0;
    private boolean forceDownhillAfterRamp = false;

    /**
     * Constructs a new Slope and generates the initial set of segments from the
     * start position.
     */
    public Slope() {
        reset();
    }

    /**
     * Clears all existing segments and regenerates the track from scratch.
     */
    public void reset() {
        segments.clear();
        hazards.clear();
        currentCenterX = 0.0;
        pendingCenterX = 0.0;
        pendingGapSegments = 0;
        forceDownhillAfterRamp = false;

        double zCursor = nearZ;
        double yCursor = startHeight;

        for (int i = 0; i < 4; i++) {
            double length = 16.0;
            double nextZ = zCursor - length;
            double nextY = yCursor - length * Settings.BASE_SLOPE_DROP_PER_UNIT;
            List<Plane> planes = List.of(
                    new Plane(zCursor, nextZ, yCursor, nextY, currentCenterX, currentCenterX)
            );
            segments.add(new Segment(Segment.SegmentType.DOWNHILL, zCursor, nextZ, planes));
            zCursor = nextZ;
            yCursor = nextY;
        }

        generatedFarZ = zCursor;
        generatedFarY = yCursor;
        baselineFarY = yCursor;

        ensureGenerated(startZ);
    }

    /**
     * Appends new segments until the track extends at least
     * Settings.GENERATION_DISTANCE world units ahead of the player.
     *
     * @param playerZ the sphere's current Z coordinate
     */
    public void ensureGenerated(double playerZ) {
        double targetFarZ = playerZ - Settings.GENERATION_DISTANCE;
        while (generatedFarZ > targetFarZ) {
            appendNextSegment();
        }
    }

    private void appendNextSegment() {
        if (pendingGapSegments > 0) {
            double gapLength = randomRange(Settings.MIN_GAP_LENGTH, Settings.MAX_GAP_LENGTH);
            double gapFarBaselineY = baselineFarY - gapLength * Settings.BASE_SLOPE_DROP_PER_UNIT;
            // GAP segments need a plane so getFarZ/getFarY delegates work, but hasSurface=false
            List<Plane> planes = List.of(
                    new Plane(generatedFarZ, generatedFarZ - gapLength,
                            generatedFarY, generatedFarY,
                            currentCenterX, currentCenterX)
            );
            Segment gap = new Segment(Segment.SegmentType.GAP,
                    generatedFarZ, generatedFarZ - gapLength, planes);
            segments.add(gap);
            generatedFarZ = gap.getFarZ();
            generatedFarY = gap.getFarY();
            baselineFarY = gapFarBaselineY;
            pendingGapSegments--;
            if (pendingGapSegments == 0) {
                forceDownhillAfterRamp = true;
            }
            return;
        }

        if (forceDownhillAfterRamp) {
            currentCenterX = pendingCenterX;
            addDownhillSegment();
            forceDownhillAfterRamp = false;
            return;
        }

        double roll = random.nextDouble();
        if (roll < Settings.RAMP_CHANCE) {
            addRampSegment();
        } else if (roll < Settings.RAMP_CHANCE + Settings.CORKSCREW_CHANCE) {
            addCorkscrewSegment();
        } else {
            addDownhillSegment();
        }
    }

    private void addDownhillSegment() {
        double length = randomRange(Settings.MIN_DOWNHILL_LENGTH, Settings.MAX_DOWNHILL_LENGTH);
        double farZ = generatedFarZ - length;
        double farY = baselineFarY - length * Settings.BASE_SLOPE_DROP_PER_UNIT;
        List<Plane> planes = List.of(
                new Plane(generatedFarZ, farZ, baselineFarY, farY, currentCenterX, currentCenterX)
        );
        Segment downhill = new Segment(Segment.SegmentType.DOWNHILL, generatedFarZ, farZ, planes);
        segments.add(downhill);
        maybeAddHazard(downhill);
        generatedFarZ = downhill.getFarZ();
        generatedFarY = downhill.getFarY();
        baselineFarY = downhill.getFarY();
    }

    private void addRampSegment() {
        double nextCenterX = chooseDifferentCenterX(currentCenterX);
        double length = randomRange(Settings.MIN_RAMP_LENGTH, Settings.MAX_RAMP_LENGTH);
        double farZ = generatedFarZ - length;
        double rampFarBaselineY = baselineFarY - length * Settings.BASE_SLOPE_DROP_PER_UNIT;
        double farY = rampFarBaselineY + randomRange(Settings.MIN_RAMP_RISE, Settings.MAX_RAMP_RISE);
        List<Plane> planes = List.of(
                new Plane(generatedFarZ, farZ, baselineFarY, farY, currentCenterX, currentCenterX)
        );
        Segment ramp = new Segment(Segment.SegmentType.RAMP, generatedFarZ, farZ, planes);
        segments.add(ramp);
        generatedFarZ = ramp.getFarZ();
        generatedFarY = ramp.getFarY();
        baselineFarY = rampFarBaselineY;
        pendingCenterX = nextCenterX;
        pendingGapSegments = random.nextInt(
                Settings.MAX_GAP_SEGMENTS - Settings.MIN_GAP_SEGMENTS + 1) + Settings.MIN_GAP_SEGMENTS;
    }

    private void addCorkscrewSegment() {
        double length = randomRange(Settings.MIN_CORKSCREW_LENGTH, Settings.MAX_CORKSCREW_LENGTH);
        double segNearZ = generatedFarZ;
        double segFarZ  = generatedFarZ - length;
        double segNearY = baselineFarY;
        double segFarY  = baselineFarY - length * Settings.BASE_SLOPE_DROP_PER_UNIT;

        // Subdivide into small planes, each getting a bank angle from a sine wave.
        double planeStep = 3.0;
        List<Plane> planes = new ArrayList<>();
        double bankRad = Math.toRadians(Settings.CORKSCREW_BANK_ANGLE);
        double halfWidth = Settings.SEGMENT_HALF_WIDTH;

        for (double z = segNearZ; z > segFarZ; z -= planeStep) {
            double pNearZ = z;
            double pFarZ  = Math.max(segFarZ, z - planeStep);

            double tNear = (segNearZ - pNearZ) / length;
            double tFar  = (segNearZ - pFarZ)  / length;

            double pNearY = segNearY + (segFarY - segNearY) * tNear;
            double pFarY  = segNearY + (segFarY - segNearY) * tFar;

            // Bank angle oscillates as a full sine cycle across the segment.
            double angleNear = Math.sin(tNear * 2.0 * Math.PI * Settings.CORKSCREW_CYCLES) * bankRad;
            double angleFar  = Math.sin(tFar  * 2.0 * Math.PI * Settings.CORKSCREW_CYCLES) * bankRad;

            // Y offset = ±halfWidth * tan(angle): positive angle tilts left up, right down.
            double nearLeftOff  =  halfWidth * Math.tan(angleNear);
            double nearRightOff = -halfWidth * Math.tan(angleNear);
            double farLeftOff   =  halfWidth * Math.tan(angleFar);
            double farRightOff  = -halfWidth * Math.tan(angleFar);

            planes.add(new Plane(pNearZ, pFarZ, pNearY, pFarY,
                    currentCenterX, currentCenterX,
                    nearLeftOff, nearRightOff, farLeftOff, farRightOff));
        }

        Segment corkscrew = new Segment(Segment.SegmentType.CORKSCREW, segNearZ, segFarZ, planes);
        segments.add(corkscrew);
        generatedFarZ = corkscrew.getFarZ();
        generatedFarY = corkscrew.getFarY();
        baselineFarY  = segFarY;
    }

    /**
     * Adds a hazard to a downhill segment, skipping early spawn area.
     */
    private void maybeAddHazard(Segment segment) {
        if (!segment.hasSurface()
                || segment.getType() == Segment.SegmentType.RAMP
                || segment.getType() == Segment.SegmentType.CORKSCREW) {
            return;
        }

        double segmentLength = segment.getNearZ() - segment.getFarZ();
        if (segmentLength < Settings.HAZARD_SIZE + Settings.MIN_HAZARD_Z_PADDING * 2.0) {
            return;
        }

        if (segment.getNearZ() >= startZ - 14.0) {
            return;
        }

        double roll = random.nextDouble();
        HazardBlock.MovementType movementType = null;
        if (roll < Settings.STATIC_HAZARD_CHANCE) {
            movementType = HazardBlock.MovementType.STATIC;
        } else if (roll < Settings.STATIC_HAZARD_CHANCE + Settings.BOBBING_HAZARD_CHANCE) {
            movementType = HazardBlock.MovementType.BOBBING;
        }

        if (movementType == null) {
            return;
        }

        double minZ = segment.getFarZ() + Settings.MIN_HAZARD_Z_PADDING;
        double maxZ = segment.getNearZ() - Settings.MIN_HAZARD_Z_PADDING;
        double centerZ = randomRange(minZ, maxZ);
        double trackCenterX = segment.getCenterXAt(centerZ);
        double maxXOffset = Settings.SEGMENT_HALF_WIDTH - Settings.HAZARD_SIZE / 2.0;
        double centerX = trackCenterX + randomRange(-maxXOffset, maxXOffset);
        double baseY = segment.getYAt(centerZ) + Settings.HAZARD_CLEARANCE;
        double bobAmplitude = movementType == HazardBlock.MovementType.BOBBING
                ? randomRange(Settings.MIN_BOB_AMPLITUDE, Settings.MAX_BOB_AMPLITUDE)
                : 0.0;
        double bobSpeed = movementType == HazardBlock.MovementType.BOBBING
                ? randomRange(Settings.MIN_BOB_SPEED, Settings.MAX_BOB_SPEED)
                : 0.0;
        double bobPhase = randomRange(0.0, Math.PI * 2.0);

        hazards.add(new HazardBlock(
                movementType,
                centerX,
                centerZ,
                baseY,
                Settings.HAZARD_SIZE,
                Settings.HAZARD_SIZE,
                Settings.HAZARD_SIZE,
                bobAmplitude,
                bobSpeed,
                bobPhase
        ));
    }

    /**
     * Picks a random lateral offset with a minimum shift magnitude, clamped to the
     * valid center range.
     */
    private double chooseDifferentCenterX(double currentX) {
        double offset = randomRange(-Settings.MAX_CENTER_SHIFT, Settings.MAX_CENTER_SHIFT);
        if (Math.abs(offset) < Settings.MIN_CENTER_SHIFT) {
            double sign = offset == 0.0
                    ? (random.nextBoolean() ? 1.0 : -1.0)
                    : Math.signum(offset);
            offset = Settings.MIN_CENTER_SHIFT * sign;
        }
        return Math.max(Settings.MIN_CENTER_X, Math.min(Settings.MAX_CENTER_X, currentX + offset));
    }

    private double randomRange(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    /**
     * Finds the segment containing the given Z coordinate.
     *
     * @param z the world Z coordinate to look up
     * @return the Segment that contains z, or null if none
     */
    public Segment findSegment(double z) {
        for (Segment segment : segments) {
            if (segment.contains(z)) {
                return segment;
            }
        }
        return null;
    }

    /**
     * Returns the surface Y elevation at the given world position, or
     * Double.NaN if off-track or over a gap.
     */
    public double getSurfaceY(double x, double z) {
        Segment segment = findSegment(z);
        if (segment == null || !segment.hasSurface()) {
            return Double.NaN;
        }

        double centerX = segment.getCenterXAt(z);
        if (Math.abs(x - centerX) > Settings.SEGMENT_HALF_WIDTH) {
            return Double.NaN;
        }

        return segment.getYAt(z);
    }

    /**
     * Returns the center X of the track at the given Z, or Double.NaN
     * if over a gap or outside all segments.
     */
    public double getSurfaceCenterX(double z) {
        Segment segment = findSegment(z);
        if (segment == null || !segment.hasSurface()) {
            return Double.NaN;
        }
        return segment.getCenterXAt(z);
    }

    /** Returns the half-width of the track in world units. */
    public double getSurfaceHalfWidth() {
        return Settings.SEGMENT_HALF_WIDTH;
    }

    /**
     * Returns the Y coordinate of the kill plane at the given Z position.
     * Returns Double.NEGATIVE_INFINITY over segments that suppress the kill plane.
     */
    public double getKillY(double z) {
        Segment seg = findSegment(z);
        if (seg != null && seg.suppressesKillPlane()) {
            return Double.NEGATIVE_INFINITY;
        }
        return getBaselineY(z) - Settings.KILL_DEPTH;
    }

    /**
     * Returns the theoretical baseline Y at the given Z coordinate.
     */
    public double getBaselineY(double z) {
        return startHeight - (nearZ - z) * Settings.BASE_SLOPE_DROP_PER_UNIT;
    }

    /**
     * Returns the slope derivative (dY/dZ) of the surface at the given Z position.
     */
    public double getSlopeDerivative(double z) {
        Segment segment = findSegment(z);
        if (segment == null || !segment.hasSurface()) {
            return 0.0;
        }
        return segment.getSlopeDerivative();
    }

    /** Returns whether the given world position lies on a valid track surface. */
    public boolean isOnSlope(double x, double z) {
        return !Double.isNaN(getSurfaceY(x, z));
    }

    /** Advances all moving hazard blocks by one frame. */
    public void updateHazards(double dt) {
        for (HazardBlock hazard : hazards) {
            hazard.update(dt);
        }
    }

    /** Returns whether a sphere at the supplied position overlaps any hazard. */
    public boolean collidesWithHazard(double x, double y, double z, double radius) {
        for (HazardBlock hazard : hazards) {
            if (hazard.collidesWith(x, y, z, radius)) {
                return true;
            }
        }
        return false;
    }

    public double getNearZ()   { return nearZ; }
    public double getStartZ()  { return startZ; }
    public double getStartY()  { return startHeight; }
    public double getStartX()  { return 0.0; }

    public List<Segment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public List<HazardBlock> getHazards() {
        return Collections.unmodifiableList(hazards);
    }
}
