/**
 * src/Sphere.java
 *
 * Represents the player's ball in the Slope game. Manages position, velocity, spin, and
 * input state for left/right movement. Contains the physics update logic for grounded and
 * airborne movement, including slope acceleration and gravity. Also tracks the ball's
 * alive/dead state and provides a key listener for movement input.
 *
 * The sphere's position is updated each frame based on player input and the slope's surface
 * geometry. When grounded, the sphere accelerates along the slope and can move laterally.
 * When airborne, gravity is applied and the sphere can still move left/right. If the sphere
 * falls below the slope's kill plane, it enters the dead state and stops moving until respawned.
 *
 * Sources used:
 * https://stackoverflow.com/questions/17538182/getting-keyboard-input for key listener implementation.
 * https://docs.oracle.com/javase/8/docs/api/java/lang/Math.html for physics calculations.
 * ChatGPT 5.4 for initial structure and design of files.
 * Claude Sonnet 4.6 for segment generation logic and improvements to the Slope class design,
 *      including the addition of a SegmentType enum.
 *
 * @since 2026-03-29
 */
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.*;

public class Sphere {

    private static final double START_FORWARD_SPEED = 8.0;
    private static final double SIDE_MAX_SPEED = 6.0;
    private static final double SIDE_ACCELERATION = 60.0;
    private static final double SIDE_DECELERATION = 80.0;
    private static final double GRAVITY = -24.0;
    private static final double MIN_FORWARD_SPEED = 3.5;
    private static final double MAX_FORWARD_SPEED = 18.0;
    private static final double START_X = 0.0;
    public final double radius = 0.5;
    private BufferedImage image;
    public double x;
    public double y;
    public double z;
    public double spinX = 0.0;
    public double spinY = 0.0;
    public double spinZ = 0.0;

    /** Velocity */
    private final Vector3 velocity = new Vector3(0.0, 0.0, -START_FORWARD_SPEED);

    /** Acceleration */
    private final Vector3 acceleration = new Vector3();

    private double forwardSpeed = START_FORWARD_SPEED;
    private volatile boolean movingLeft = false;
    private volatile boolean movingRight = false;
    private boolean dead = false;
    private boolean grounded = false;

    /**
     * Constructs a new sphere with the given ball texture.
     *
     * @param image the image drawn on the sphere surface; may be null if the
     * resource failed to load, in which case only the glass overlay is rendered
     */
    public Sphere(BufferedImage image) {
        this.image = image;
        x = START_X;
        y = radius;
        z = 0.0;
    }

    BufferedImage getImage() {
        return image;
    }

    void setImage(BufferedImage image) {
        this.image = image;
    }

    /**
     * Resets position, velocity, and spin to match the slope's starting
     * conditions.
     *
     * @param slope the slope whose start coordinates and surface height are
     * used to position the sphere at the top of the track; must not be null
     */
    public void placeAtStart(Slope slope) {
        x = slope.getStartX();
        z = slope.getStartZ();
        y = slope.getSurfaceY(x, z) + radius;
        velocity.setX(0.0);
        velocity.setY(0.0);
        velocity.setZ(-START_FORWARD_SPEED);
        acceleration.setX(0.0);
        acceleration.setY(0.0);
        acceleration.setZ(0.0);
        forwardSpeed = START_FORWARD_SPEED;
        velocity.setZ(-forwardSpeed);
        spinX = 0.0;
        spinY = 0.0;
        spinZ = 0.0;
        movingLeft = false;
        movingRight = false;
        dead = false;
        grounded = true;
    }

    /**
     * Creates and returns a key listener that updates this sphere's movement
     * flags based on left/right input. The listener sets movingLeft to true
     * when the left arrow or A key is pressed, and movingRight to true when the
     * right arrow or D key is pressed.
     *
     * Sources used:
     * https://stackoverflow.com/questions/17538182/getting-keyboard-input
     *
     * @return a key listener wired to this sphere's movement flags
     */
    public KeyListener createKeyListener() {
        return new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A ->
                        movingLeft = true;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D ->
                        movingRight = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A ->
                        movingLeft = false;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D ->
                        movingRight = false;
                }
            }
        };
    }

    /**
     * Advances the sphere's physics simulation by one time step.
     *
     * When grounded, the sphere accelerates along the slope gradient and tracks
     * lateral lane offset. When airborne, gravity is applied. Spin angles are
     * accumulated from displacement for the visual roll effect. Returns true on
     * the tick the sphere dies.
     *
     * @param dt elapsed time in seconds since the last update; should be capped
     * by the caller to avoid large integration steps
     * @param slope the slope used for surface queries and kill-plane detection;
     * must not be null
     * @return true if the sphere fell below the kill plane this tick, false if
     * still alive
     */
    public boolean update(double dt, Slope slope) {
        if (dead) {
            return true;
        }

        slope.ensureGenerated(z);

        double prevX = x;
        double prevZ = z;
        double prevCenterX = slope.getSurfaceCenterX(prevZ);

        // Confirm still grounded using current position before any movement.
        if (grounded) {
            grounded = slope.isOnSlope(x, z) && y <= slope.getSurfaceY(x, z) + radius + 0.05;
        }

        double laneOffset = grounded && !Double.isNaN(prevCenterX) ? prevX - prevCenterX : 0.0;

        if (movingLeft && !movingRight) {
            acceleration.setX(-SIDE_ACCELERATION);
        } else if (movingRight && !movingLeft) {
            acceleration.setX(SIDE_ACCELERATION);
        } else {
            acceleration.setX(0.0);
        }

        if (acceleration.getX() != 0.0) {
            velocity.setX(velocity.getX() + acceleration.getX() * dt);
            velocity.setX(Math.max(-SIDE_MAX_SPEED, Math.min(SIDE_MAX_SPEED, velocity.getX())));
        } else if (velocity.getX() != 0.0) {
            double decelerationStep = SIDE_DECELERATION * dt;
            if (Math.abs(velocity.getX()) <= decelerationStep) {
                velocity.setX(0.0);
            } else {
                velocity.setX(velocity.getX() - Math.copySign(decelerationStep, velocity.getX()));
            }
        }

        if (grounded) {
            acceleration.setY(0.0);
            /**
             * Decompose gravitational acceleration along the slope surface
             * using the slope gradient. The component of gravity acting forward
             * is g * -dY/dZ divided by the magnitude of the slope tangent
             * vector.
             *
             * Sources used:
             * https://docs.oracle.com/javase/8/docs/api/java/lang/Math.html
             */
            double slopeDerivative = slope.getSlopeDerivative(z);
            double accelAlongTrack = (GRAVITY * -slopeDerivative) / (1.0 + slopeDerivative * slopeDerivative);
            acceleration.setZ(accelAlongTrack);
            forwardSpeed += accelAlongTrack * dt;
            forwardSpeed = Math.max(MIN_FORWARD_SPEED, Math.min(MAX_FORWARD_SPEED, forwardSpeed));
        } else {
            acceleration.setY(GRAVITY);
            acceleration.setZ(0.0);
        }
        velocity.setZ(-forwardSpeed);

        z += velocity.getZ() * dt;

        if (grounded) {
            double newCenterX = slope.getSurfaceCenterX(z);
            if (!Double.isNaN(newCenterX)) {
                laneOffset += velocity.getX() * dt;
                x = newCenterX + laneOffset;
            } else {
                x += velocity.getX() * dt;
            }
        } else {
            x += velocity.getX() * dt;
        }

        if (grounded) {
            // Sphere was grounded before movement; check new position still has surface.
            boolean stillOnSurface = slope.isOnSlope(x, z);
            if (stillOnSurface) {
                double surfaceY = slope.getSurfaceY(x, z) + radius;
                double surfaceVy = slope.getSlopeDerivative(z) * velocity.getZ();
                y = surfaceY;
                velocity.setY(surfaceVy);
            } else {
                // Rolled off edge — launch into the air with current slope velocity.
                velocity.setY(slope.getSlopeDerivative(prevZ) * velocity.getZ());
                velocity.setY(velocity.getY() + GRAVITY * dt);
                y += velocity.getY() * dt;
                grounded = false;
            }
        } else {
            double preStepY = y;
            velocity.setY(velocity.getY() + acceleration.getY() * dt);
            y += velocity.getY() * dt;

            // Land only if the sphere was above the surface before this step and has now crossed it.
            if (slope.isOnSlope(x, z)) {
                double surfaceY = slope.getSurfaceY(x, z) + radius;
                if (preStepY >= surfaceY && y <= surfaceY && velocity.getY() <= 0.0) {
                    y = surfaceY;
                    velocity.setY(slope.getSlopeDerivative(z) * velocity.getZ());
                    grounded = true;
                }
            }
        }

        double dx = x - prevX;
        double dz = z - prevZ;
        spinX += (-dz) / radius;
        spinY += dx / radius;
        spinZ += dx / (radius * 2.0);

        boolean died = y < slope.getKillY(z) || slope.collidesWithHazard(x, y, z, radius);
        if (died) {
            dead = true;
            velocity.setX(0.0);
            velocity.setY(0.0);
            velocity.setZ(0.0);
            acceleration.setX(0.0);
            acceleration.setY(0.0);
            acceleration.setZ(0.0);
            movingLeft = false;
            movingRight = false;
            return true;
        }
        return false;
    }

    /**
     * Returns whether the sphere has fallen off the track and is in the dead
     * state.
     *
     * @return true if the sphere has died, false if still active
     */
    public boolean isDead() {
        return dead;
    }

    /**
     * Returns the current lateral (X-axis) velocity in world units per second.
     *
     * @return positive for rightward motion, negative for leftward
     */
    public double getHorizontalVelocity() {
        return velocity.getX();
    }

    /**
     * Returns the current forward (Z-axis) velocity in world units per second.
     *
     * @return negative value since forward motion is in the -Z direction
     */
    public double getForwardVelocity() {
        return velocity.getZ();
    }

    /**
     * Returns the current vertical (Y-axis) velocity in world units per second.
     *
     * @return positive for upward motion, negative for downward
     */
    public double getVerticalVelocity() {
        return velocity.getY();
    }

    /**
     * Returns the current forward speed scalar in world units per second.
     *
     * This is the unsigned magnitude of the forward velocity, clamped between
     * MIN_FORWARD_SPEED and MAX_FORWARD_SPEED.
     *
     * @return the current forward speed; always positive
     */
    public double getForwardSpeed() {
        return forwardSpeed;
    }
}
