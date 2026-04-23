/**
 * Represents a single renderable quad of track geometry.
 *
 * A Plane spans a Z range and stores the center Y and center X at its near and
 * far ends. For banked (corkscrew) planes the left and right edges carry
 * independent Y offsets; flat planes leave these offsets at zero.
 *
 * Physics queries use only the centre Y values so the sphere follows the
 * logical midline of a banked section rather than a tilted surface.
 */
public final class Plane {

    public final double nearZ;
    public final double farZ;
    /** Centre-line Y at the near edge. */
    public final double nearY;
    /** Centre-line Y at the far edge. */
    public final double farY;
    public final double nearCenterX;
    public final double farCenterX;

    /** Additional Y applied to the left edge at the near end (positive = raised). */
    public final double nearLeftYOffset;
    /** Additional Y applied to the right edge at the near end. */
    public final double nearRightYOffset;
    /** Additional Y applied to the left edge at the far end. */
    public final double farLeftYOffset;
    /** Additional Y applied to the right edge at the far end. */
    public final double farRightYOffset;

    /** Constructs a flat plane (no banking). */
    public Plane(double nearZ, double farZ, double nearY, double farY,
                 double nearCenterX, double farCenterX) {
        this(nearZ, farZ, nearY, farY, nearCenterX, farCenterX, 0, 0, 0, 0);
    }

    /** Constructs a plane with independent left/right Y offsets for banking. */
    public Plane(double nearZ, double farZ, double nearY, double farY,
                 double nearCenterX, double farCenterX,
                 double nearLeftYOffset, double nearRightYOffset,
                 double farLeftYOffset, double farRightYOffset) {
        this.nearZ = nearZ;
        this.farZ = farZ;
        this.nearY = nearY;
        this.farY = farY;
        this.nearCenterX = nearCenterX;
        this.farCenterX = farCenterX;
        this.nearLeftYOffset = nearLeftYOffset;
        this.nearRightYOffset = nearRightYOffset;
        this.farLeftYOffset = farLeftYOffset;
        this.farRightYOffset = farRightYOffset;
    }

    /** Returns true if z falls within this plane's Z range. */
    public boolean contains(double z) {
        return z <= nearZ && z >= farZ;
    }

    /** Returns the interpolated centre-line Y at the given Z. */
    public double getCenterYAt(double z) {
        return lerp(nearY, farY, progress(z));
    }

    /** Returns the interpolated centre X at the given Z. */
    public double getCenterXAt(double z) {
        return lerp(nearCenterX, farCenterX, progress(z));
    }

    /** Returns the left-edge Y (centre Y + left offset) at the given Z. */
    public double getLeftYAt(double z) {
        double t = progress(z);
        return lerp(nearY, farY, t) + lerp(nearLeftYOffset, farLeftYOffset, t);
    }

    /** Returns the right-edge Y (centre Y + right offset) at the given Z. */
    public double getRightYAt(double z) {
        double t = progress(z);
        return lerp(nearY, farY, t) + lerp(nearRightYOffset, farRightYOffset, t);
    }

    public double getLeftXAt(double z, double halfWidth) {
        return getCenterXAt(z) - halfWidth;
    }

    public double getRightXAt(double z, double halfWidth) {
        return getCenterXAt(z) + halfWidth;
    }

    /** Returns the slope derivative dY/dZ for the centre line. */
    public double getSlopeDerivative() {
        double deltaZ = farZ - nearZ;
        if (Math.abs(deltaZ) < 1e-9) {
            return 0.0;
        }
        return (farY - nearY) / deltaZ;
    }

    private double progress(double z) {
        double length = nearZ - farZ;
        if (length <= 1e-9) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (nearZ - z) / length));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
