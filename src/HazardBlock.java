/**
 * Represents a red hazard block placed on the slope.
 *
 * Hazard blocks sit on top of generated slope segments and kill the player on
 * contact. Blocks can remain fixed in place or animate up and down with a
 * tweened bobbing motion.
 */
public final class HazardBlock {

    /**
     * Defines the supported hazard movement behaviors.
     */
    public enum MovementType {
        STATIC,
        BOBBING
    }

    private final MovementType movementType;
    private final double centerX;
    private final double centerZ;
    private final double baseY;
    private final double width;
    private final double depth;
    private final double height;
    private final double bobAmplitude;
    private final double bobSpeed;
    private final double bobPhase;

    private double elapsedTime = 0.0;

    /**
     * Creates a hazard block with the supplied geometry and movement settings.
     *
     * @param movementType the movement mode for the block
     * @param centerX the world X coordinate of the block center
     * @param centerZ the world Z coordinate of the block center
     * @param baseY the Y coordinate of the block base resting on the slope
     * @param width the X-axis size of the block in world units
     * @param depth the Z-axis size of the block in world units
     * @param height the Y-axis size of the block in world units
     * @param bobAmplitude the peak vertical offset for bobbing motion
     * @param bobSpeed the bobbing angular speed in radians per second
     * @param bobPhase the starting bobbing phase in radians
     */
    public HazardBlock(MovementType movementType, double centerX, double centerZ, double baseY,
            double width, double depth, double height, double bobAmplitude, double bobSpeed, double bobPhase) {
        this.movementType = movementType;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.baseY = baseY;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.bobAmplitude = bobAmplitude;
        this.bobSpeed = bobSpeed;
        this.bobPhase = bobPhase;
    }

    /**
     * Advances the block animation by one frame.
     *
     * @param dt elapsed time in seconds since the previous frame
     */
    public void update(double dt) {
        elapsedTime += dt;
    }

    /**
     * Returns the vertical offset produced by the block's movement pattern.
     *
     * Uses a sine wave to create a smooth up/down tween.
     *
     * @return the current vertical offset from the resting base position
     */
    public double getVerticalOffset() {
        if (movementType == MovementType.STATIC) {
            return 0.0;
        }
        // Shift sine from [-1,1] to [0,1] so the block never dips below its baseY.
        return ((Math.sin(elapsedTime * bobSpeed + bobPhase) + 1.0) / 2.0) * bobAmplitude;
    }

    /**
     * Returns the current Y coordinate of the block base.
     *
     * @return the current base Y after movement is applied
     */
    public double getCurrentBaseY() {
        return baseY + getVerticalOffset();
    }

    /**
     * Returns the current Y coordinate of the block top face.
     *
     * @return the current top-face Y coordinate
     */
    public double getTopY() {
        return getCurrentBaseY() + height;
    }

    /**
     * Returns whether the sphere intersects this block.
     *
     * Uses sphere-vs-axis-aligned-box overlap in world space.
     *
     * Source:
     * https://developer.mozilla.org/en-US/docs/Games/Techniques/3D_collision_detection
     *
     * @param sphereX the sphere center X coordinate
     * @param sphereY the sphere center Y coordinate
     * @param sphereZ the sphere center Z coordinate
     * @param sphereRadius the sphere radius
     * @return true if the sphere overlaps the block volume
     */
    public boolean collidesWith(double sphereX, double sphereY, double sphereZ, double sphereRadius) {
        double minX = centerX - width / 2.0;
        double maxX = centerX + width / 2.0;
        double minY = getCurrentBaseY();
        double maxY = minY + height;
        double minZ = centerZ - depth / 2.0;
        double maxZ = centerZ + depth / 2.0;

        double closestX = clamp(sphereX, minX, maxX);
        double closestY = clamp(sphereY, minY, maxY);
        double closestZ = clamp(sphereZ, minZ, maxZ);

        double dx = sphereX - closestX;
        double dy = sphereY - closestY;
        double dz = sphereZ - closestZ;
        return dx * dx + dy * dy + dz * dz <= sphereRadius * sphereRadius;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public double getWidth() {
        return width;
    }

    public double getDepth() {
        return depth;
    }

    public double getHeight() {
        return height;
    }
}
