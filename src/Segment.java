/**
 * src/Segment.java
 *
 * Represents a single contiguous track section within the slope.
 *
 * A Segment describes the generation type and Z range of a section of track.
 * The actual renderable geometry is stored as a list of Plane objects owned by
 * the segment. Physics and rendering both delegate to these planes.
 *
 * Sources used:
 * https://en.wikipedia.org/wiki/Linear_interpolation for linear interpolation used in surface queries.
 * ChatGPT 5.4 for initial structure and design of files.
 * Claude Sonnet 4.6 for segment generation logic and improvements to the Slope class design,
 *     including the addition of a SegmentType enum.
 *
 * @since 2026-03-29
 */
import java.util.List;

public final class Segment {

    /**
     * Classifies the gameplay role and rendering behaviour of a segment.
     */
    public enum SegmentType {
        DOWNHILL,
        RAMP,
        GAP,
        CORKSCREW
    }

    private final SegmentType type;
    private final double nearZ;
    private final double farZ;
    private final List<Plane> planes;

    Segment(SegmentType type, double nearZ, double farZ, List<Plane> planes) {
        this.type = type;
        this.nearZ = nearZ;
        this.farZ = farZ;
        this.planes = planes;
    }

    /** Returns the segment classification. */
    public SegmentType getType() {
        return type;
    }

    /** Returns the Z coordinate of the near (player-facing) end. */
    public double getNearZ() {
        return nearZ;
    }

    /** Returns the Z coordinate of the far end. */
    public double getFarZ() {
        return farZ;
    }

    /** Returns the ordered list of planes that make up this segment's geometry. */
    public List<Plane> getPlanes() {
        return planes;
    }

    /** Returns whether this segment has a physical surface the sphere can stand on. */
    public boolean hasSurface() {
        return type != SegmentType.GAP;
    }

    /**
     * Returns true when the kill plane should be suppressed over this segment.
     * Currently only CORKSCREW segments suppress the kill plane.
     */
    public boolean suppressesKillPlane() {
        return type == SegmentType.CORKSCREW;
    }

    /** Returns true if z lies within this segment's Z range (inclusive). */
    public boolean contains(double z) {
        return z <= nearZ && z >= farZ;
    }

    /**
     * Returns the interpolated centre-line surface Y at the given Z, or
     * Double.NaN if this segment has no surface or z is out of range.
     */
    public double getYAt(double z) {
        if (!hasSurface()) {
            return Double.NaN;
        }
        Plane p = findPlane(z);
        return p != null ? p.getCenterYAt(z) : Double.NaN;
    }

    /** Returns the interpolated track centre X at the given Z. */
    public double getCenterXAt(double z) {
        Plane p = findPlane(z);
        return p != null ? p.getCenterXAt(z) : 0.0;
    }

    /** Returns the left track edge X at the given Z. */
    public double getLeftXAt(double z, double halfWidth) {
        Plane p = findPlane(z);
        return p != null ? p.getLeftXAt(z, halfWidth) : -halfWidth;
    }

    /** Returns the right track edge X at the given Z. */
    public double getRightXAt(double z, double halfWidth) {
        Plane p = findPlane(z);
        return p != null ? p.getRightXAt(z, halfWidth) : halfWidth;
    }

    /**
     * Returns the slope derivative dY/dZ for the first plane in the segment.
     * Used by physics to compute gravitational acceleration along the track.
     */
    public double getSlopeDerivative() {
        if (planes.isEmpty() || !hasSurface()) {
            return 0.0;
        }
        return planes.get(0).getSlopeDerivative();
    }

    /** Returns the Y of the far end of the last plane (used by Slope for chaining). */
    public double getFarY() {
        if (planes.isEmpty()) {
            return 0.0;
        }
        return planes.get(planes.size() - 1).farY;
    }

    /** Returns the Y of the near end of the first plane. */
    public double getNearY() {
        if (planes.isEmpty()) {
            return 0.0;
        }
        return planes.get(0).nearY;
    }

    private Plane findPlane(double z) {
        for (Plane p : planes) {
            if (p.contains(z)) {
                return p;
            }
        }
        // Fallback: return the closest plane if z is just outside due to floating point
        if (!planes.isEmpty()) {
            if (z > nearZ) return planes.get(0);
            return planes.get(planes.size() - 1);
        }
        return null;
    }
}
