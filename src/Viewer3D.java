/**
 * src/Viewer3D.java
 *
 * Primary rendering panel that displays the 3D perspective view of the game.
 * Drives the game loop timer, which advances GameEnvironment each tick, notifies
 * GameUI to update the HUD, and repaints the scene. All game state is owned by
 * GameEnvironment; this class only reads from it to produce the rendered output.
 *
 * Sources used:
 * https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html for update logic and time intervals.
 * https://github.com/pajouheshgar/3D-Swing for the original 3D rendering demo in Swing, which was heavily modified and expanded upon for this project.
 * https://en.wikipedia.org/wiki/Painter%27s_algorithm for the back-to-front rendering approach.
 * https://en.wikipedia.org/wiki/Rotation_matrix for the camera pitch rotation math.
 * ChatGPT 5.4 for initial structure and design of files.
 * Claude Sonnet 4.6 for rendering logic (modified on top of the 3D Swing demo),
 *      slope segment rendering, and game state management improvements. Pretty much
 *      all the logic that needed to be handled, especially with camera transforms.
 *
 * @since 2026-03-29
 */
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;

/**
 * Pure rendering panel that draws the 3D perspective view of the game each frame.
 *
 * All game state is owned by GameEnvironment. This class reads from it
 * each tick, drives the game loop timer (which calls GameEnvironment.tick
 * and GameUI.update), and repaints the scene. Rendering covers the sky
 * gradient, kill plane, slope geometry, and sphere or explosion.
 */
public class Viewer3D extends JPanel {

    private static final int TIMER_INTERVAL_MS = 50;
    private static final double VIEW_ANGLE_DEGREES = 30.0;
    private static final double CAMERA_FOLLOW_DISTANCE = 2.0;
    private static final double CAMERA_HEIGHT = Math.tan(Math.toRadians(VIEW_ANGLE_DEGREES)) * CAMERA_FOLLOW_DISTANCE;
    private static final double NEAR_CLIP = -0.2;
    private static final double PLANE_WIDTH_SCALE = 0.48;
    private static final double KILL_PLANE_HALF_WIDTH = 12.0;

    private final GameEnvironment env;

    private final double eyeXOffset = 0.0;
    private final double eyeYOffset = CAMERA_HEIGHT;
    private final double eyeZOffset = CAMERA_FOLLOW_DISTANCE;
    private final double focalLength = 600.0;
    private final double pitchRadians = Math.toRadians(VIEW_ANGLE_DEGREES);

    private final javax.swing.Timer timer;
    private long lastTime;

    private int renderedSlopeSegments = 0;
    private int renderedGaps = 0;

    private GameUI gameUI;

    /**
     * Constructs the viewer and starts the game loop timer. The timer advances
     * the game environment, notifies the HUD, and repaints the scene at a fixed
     * interval.
     *
     * Sources used:
     * https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html for update logic and time intervals.
     * Claude Sonnet 4.6 for update logic and time intervals.
     *
     * @param env the game environment providing all game state and physics; must
     *     not be null
     */
    public Viewer3D(GameEnvironment env) {
        this.env = env;

        setBackground(GameColors.SKY_TOP);
        setFocusable(true);
        addKeyListener(env.getSphere().createKeyListener());

        timer = new javax.swing.Timer(TIMER_INTERVAL_MS, (ActionEvent e) -> {
            long now = System.nanoTime();
            double dt = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            env.tick(dt);
            if (gameUI != null) {
                gameUI.update();
            }
            repaint();
        });
        lastTime = System.nanoTime();
        timer.start();

    }

    /**
     * Registers the GameUI overlay so it receives a GameUI.update() call every tick.
     *
     * @param gameUI the HUD overlay panel; must not be null
     */
    public void setGameUI(GameUI gameUI) {
        this.gameUI = gameUI;
        gameUI.update();
    }

    /**
     * Transforms a world-space point into camera space by translating relative
     * to the camera eye position and applying a pitch rotation about the X axis.
     *
     * The pitch rotation follows the standard 2D rotation matrix applied to the
     * Y and Z axes.
     *
     * Sources used:
     * https://en.wikipedia.org/wiki/Rotation_matrix for the camera pitch rotation math.
     * Claude Sonnet 4.6 for the original camera transform math, debugging, and refinement.
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     * @return a three-element array [cameraX, cameraY, cameraZ]
     */
    private double[] toCameraSpace(double x, double y, double z) {
        Sphere sphere = env.getSphere();
        double relX = x - (sphere.x + eyeXOffset);
        double relY = y - (sphere.y + eyeYOffset);
        double relZ = z - (sphere.z + eyeZOffset);

        double cameraY = relY * Math.cos(pitchRadians) - relZ * Math.sin(pitchRadians);
        double cameraZ = relY * Math.sin(pitchRadians) + relZ * Math.cos(pitchRadians);

        return new double[]{relX, cameraY, cameraZ};
    }

    /**
     * Projects a camera-space point onto the screen using perspective division.
     *
     * @param camX camera X coordinate
     * @param camY camera Y coordinate
     * @param camZ camera Z coordinate; must be at or behind NEAR_CLIP to be visible
     * @return screen pixel coordinates [sx, sy], or null if the point is behind
     *     the near-clip plane
     */
    private int[] projectCameraPoint(double camX, double camY, double camZ) {
        if (camZ > NEAR_CLIP) {
            return null;
        }

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        int sx = centerX + (int) (focalLength * camX / -camZ);
        int sy = centerY - (int) (focalLength * camY / -camZ);
        return new int[]{sx, sy};
    }

    /**
     * Convenience method combining toCameraSpace and projectCameraPoint.
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     * @return screen pixel coordinates, or null if behind the near-clip plane
     */
    private int[] project(double x, double y, double z) {
        double[] cam = toCameraSpace(x, y, z);
        return projectCameraPoint(cam[0], cam[1], cam[2]);
    }

    /**
     * Linearly interpolates a camera-space point to the near-clip plane along
     * the segment from start to end.
     *
     * @param start camera-space start point [cx, cy, cz]
     * @param end camera-space end point [cx, cy, cz]
     * @return the interpolated point clamped to NEAR_CLIP in Z
     */
    private double[] interpolateToNearClip(double[] start, double[] end) {
        double deltaZ = end[2] - start[2];
        if (Math.abs(deltaZ) < 1e-9) {
            return new double[]{end[0], end[1], NEAR_CLIP};
        }

        double t = (NEAR_CLIP - start[2]) / deltaZ;
        return new double[]{
            start[0] + (end[0] - start[0]) * t,
            start[1] + (end[1] - start[1]) * t,
            NEAR_CLIP
        };
    }

    /**
     * Projects the nearer of two world-space endpoints onto the screen,
     * clipping to the near plane when necessary. Returns null if both points
     * are behind the near-clip plane.
     *
     * @param ax world X of point A
     * @param ay world Y of point A
     * @param az world Z of point A
     * @param bx world X of point B (the anchor used when A is behind clip)
     * @param by world Y of point B
     * @param bz world Z of point B
     * @return screen pixel coordinates of the clipped point, or null if both
     *     points are behind the near-clip plane
     */
    private int[] projectClipped(double ax, double ay, double az, double bx, double by, double bz) {
        double[] camA = toCameraSpace(ax, ay, az);
        double[] camB = toCameraSpace(bx, by, bz);

        if (camA[2] > NEAR_CLIP && camB[2] > NEAR_CLIP) {
            return null;
        }

        double[] clipped = camA[2] <= NEAR_CLIP ? camA : interpolateToNearClip(camA, camB);
        return projectCameraPoint(clipped[0], clipped[1], clipped[2]);
    }

    /**
     * Paints the full scene: sky gradient, kill plane, slope geometry, and sphere.
     * The slope is rendered back-to-front so that nearer segments paint over farther ones.
     * The sphere is rendered last so it appears on top of the slope.
     *
     * @param g the graphics context provided by Swing
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        renderedSlopeSegments = 0;
        renderedGaps = 0;

        drawSky(g2);
        drawKillPlane(g2);
        drawSlope(g2);
        drawSphere(g2);
    }

    /**
     * Draws the sphere or explosion at its projected screen position. Skipped
     * entirely when the game is in the DEAD state.
     *
     * @param g2 the 2D graphics context
     */
    private void drawSphere(Graphics2D g2) {
        if (env.getGameState() == GameEnvironment.GameState.DEAD) {
            return;
        }

        Sphere sphere = env.getSphere();
        double[] cameraPos = toCameraSpace(sphere.x, sphere.y, sphere.z);
        double cameraZ = cameraPos[2];
        if (cameraZ > NEAR_CLIP) {
            return;
        }

        int[] screenPos = project(sphere.x, sphere.y, sphere.z);
        if (screenPos == null) {
            return;
        }

        int projectedRadius = Math.max(3, (int) (focalLength * sphere.radius / -cameraZ));
        int diameter = projectedRadius * 2;
        int left = screenPos[0] - projectedRadius;
        int top = screenPos[1] - projectedRadius;
        Ellipse2D sphereEllipse = new Ellipse2D.Double(left, top, diameter, diameter);

        if (env.getGameState() == GameEnvironment.GameState.EXPLODING) {
            drawExplosion(g2, screenPos[0], screenPos[1], diameter);
            return;
        }

        g2.setColor(GameColors.SPHERE_GLASS);
        g2.fill(sphereEllipse);

        java.awt.image.BufferedImage ballImage = sphere.getImage();
        if (ballImage != null) {
            Shape oldClip = g2.getClip();
            AffineTransform oldTransform = g2.getTransform();

            g2.setClip(sphereEllipse);
            g2.translate(screenPos[0], screenPos[1]);
            g2.rotate(sphere.spinZ);

            double rawScaleY = Math.cos(sphere.spinX);
            double scaleX = signedClamp(Math.cos(sphere.spinY * 0.35), 0.82);
            double scaleY = signedClamp(rawScaleY, 0.12);
            g2.scale(scaleX, scaleY);
            g2.shear(Math.sin(sphere.spinY) * 0.12, Math.sin(sphere.spinX) * 0.08);

            int planeWidth = (int) Math.round(diameter * PLANE_WIDTH_SCALE);
            int planeHeight = planeWidth * ballImage.getHeight() / Math.max(1, ballImage.getWidth());
            g2.drawImage(ballImage, -planeWidth / 2, -planeHeight / 2, planeWidth, planeHeight, null);

            if (rawScaleY < 0.0) {
                g2.setColor(GameColors.SPHERE_SHADOW);
                g2.fillRect(-planeWidth / 2, -planeHeight / 2, planeWidth, planeHeight);
            }

            g2.setTransform(oldTransform);
            g2.setClip(oldClip);
        }

        GradientPaint shineGradient = new GradientPaint(
                left, top,
                GameColors.SPHERE_SHINE_A,
                left + diameter, top + diameter,
                GameColors.SPHERE_SHINE_B
        );
        g2.setPaint(shineGradient);
        g2.fillOval(left, top, diameter, diameter);
    }

    /**
     * Draws the explosion animation frame (or a fallback orange fireball)
     * centered on the sphere's last screen position.
     *
     * @param g2 the 2D graphics context
     * @param centerX screen X center of the explosion
     * @param centerY screen Y center of the explosion
     * @param sphereDiameter projected pixel diameter of the sphere at death
     */
    private void drawExplosion(Graphics2D g2, int centerX, int centerY, int sphereDiameter) {
        int size = Math.max(80, (int) Math.round(sphereDiameter * 2.8));
        int left = centerX - size / 2;
        int top = centerY - size / 2;

        BufferedImage frame = env.getExplosionAnimation().getFrameAt(
                System.nanoTime() - env.getExplosionStartTime());
        if (frame != null) {
            g2.drawImage(frame, left, top, size, size, null);
            return;
        }

        g2.setColor(GameColors.EXPLOSION_OUTER);
        g2.fillOval(left, top, size, size);
        g2.setColor(GameColors.EXPLOSION_INNER);
        g2.fillOval(left + size / 5, top + size / 5, size * 3 / 5, size * 3 / 5);
    }

    /**
     * Clamps a value so its absolute magnitude is at least minMagnitude,
     * preserving the original sign.
     *
     * @param value the value to clamp
     * @param minMagnitude the minimum allowed absolute value; must be non-negative
     * @return the clamped value with the original sign
     */
    private double signedClamp(double value, double minMagnitude) {
        double sign = value < 0.0 ? -1.0 : 1.0;
        double magnitude = Math.max(minMagnitude, Math.abs(value));
        return sign * magnitude;
    }

    /**
     * Renders all visible slope segments as perspective-projected quadrilaterals.
     * Segments are drawn back-to-front so that nearer quads correctly paint over farther ones.
     *
     * Sources used:
     * https://en.wikipedia.org/wiki/Painter%27s_algorithm for the back-to-front rendering approach.
     * Claude Sonnet 4.6 for the initial logic
     *
     * @param g2 the 2D graphics context
     */
    private void drawSlope(Graphics2D g2) {
        Slope slope = env.getSlope();
        List<Segment> segments = slope.getSegments();
        List<HazardBlock> hazards = slope.getHazards();
        double halfWidth = slope.getSurfaceHalfWidth();

        for (int i = segments.size() - 1; i >= 0; i--) {
            Segment segment = segments.get(i);
            if (!segment.hasSurface()) {
                renderedGaps++;
                continue;
            }

            Color fill = switch (segment.getType()) {
                case DOWNHILL  -> GameColors.SLOPE_DOWNHILL;
                case RAMP      -> GameColors.SLOPE_RAMP;
                case CORKSCREW -> GameColors.SLOPE_CORKSCREW;
                case GAP       -> GameColors.SLOPE_GAP;
            };

            for (Plane plane : segment.getPlanes()) {
                int[] frontLeft  = projectClipped(plane.getLeftXAt(plane.nearZ, halfWidth),  plane.getLeftYAt(plane.nearZ),  plane.nearZ,
                                                  plane.getLeftXAt(plane.farZ,  halfWidth),  plane.getLeftYAt(plane.farZ),   plane.farZ);
                int[] frontRight = projectClipped(plane.getRightXAt(plane.nearZ, halfWidth), plane.getRightYAt(plane.nearZ), plane.nearZ,
                                                  plane.getRightXAt(plane.farZ,  halfWidth), plane.getRightYAt(plane.farZ),  plane.farZ);
                int[] backRight  = projectClipped(plane.getRightXAt(plane.farZ,  halfWidth), plane.getRightYAt(plane.farZ),  plane.farZ,
                                                  plane.getRightXAt(plane.nearZ, halfWidth), plane.getRightYAt(plane.nearZ), plane.nearZ);
                int[] backLeft   = projectClipped(plane.getLeftXAt(plane.farZ,   halfWidth), plane.getLeftYAt(plane.farZ),   plane.farZ,
                                                  plane.getLeftXAt(plane.nearZ,  halfWidth), plane.getLeftYAt(plane.nearZ),  plane.nearZ);

                if (frontLeft == null || frontRight == null || backRight == null || backLeft == null) {
                    continue;
                }

                Polygon quad = new Polygon(
                        new int[]{frontLeft[0], frontRight[0], backRight[0], backLeft[0]},
                        new int[]{frontLeft[1], frontRight[1], backRight[1], backLeft[1]},
                        4
                );
                g2.setColor(fill);
                g2.fillPolygon(quad);
                renderedSlopeSegments++;
            }

            g2.setColor(GameColors.SLOPE_EDGE);
            for (double z = segment.getFarZ(); z <= segment.getNearZ(); z += 6.0) {
                int[] edgeLeft  = project(segment.getLeftXAt(z, halfWidth),  segment.getYAt(z), z);
                int[] edgeRight = project(segment.getRightXAt(z, halfWidth), segment.getYAt(z), z);
                if (edgeLeft != null && edgeRight != null) {
                    g2.drawLine(edgeLeft[0], edgeLeft[1], edgeRight[0], edgeRight[1]);
                }
            }

            for (HazardBlock hazard : hazards) {
                double hz = hazard.getCenterZ();
                if (hz >= segment.getFarZ() && hz < segment.getNearZ()) {
                    drawHazard(g2, hazard);
                }
            }
        }
    }

    /**
     * Draws one hazard block as a shaded rectangular prism.
     *
     * @param g2 the 2D graphics context
     * @param hazard the hazard block to render
     */
    private void drawHazard(Graphics2D g2, HazardBlock hazard) {
        double minX = hazard.getCenterX() - hazard.getWidth() / 2.0;
        double maxX = hazard.getCenterX() + hazard.getWidth() / 2.0;
        double minZ = hazard.getCenterZ() - hazard.getDepth() / 2.0;
        double maxZ = hazard.getCenterZ() + hazard.getDepth() / 2.0;
        double bottomY = hazard.getCurrentBaseY();
        double topY = hazard.getTopY();

        int[] nearBottomLeft = project(minX, bottomY, maxZ);
        int[] nearBottomRight = project(maxX, bottomY, maxZ);
        int[] farBottomLeft = project(minX, bottomY, minZ);
        int[] farBottomRight = project(maxX, bottomY, minZ);
        int[] nearTopLeft = project(minX, topY, maxZ);
        int[] nearTopRight = project(maxX, topY, maxZ);
        int[] farTopLeft = project(minX, topY, minZ);
        int[] farTopRight = project(maxX, topY, minZ);

        if (nearBottomLeft == null || nearBottomRight == null || farBottomLeft == null || farBottomRight == null
                || nearTopLeft == null || nearTopRight == null || farTopLeft == null || farTopRight == null) {
            return;
        }

        Polygon topFace = new Polygon(
                new int[]{nearTopLeft[0], nearTopRight[0], farTopRight[0], farTopLeft[0]},
                new int[]{nearTopLeft[1], nearTopRight[1], farTopRight[1], farTopLeft[1]},
                4
        );
        Polygon leftFace = new Polygon(
                new int[]{nearTopLeft[0], farTopLeft[0], farBottomLeft[0], nearBottomLeft[0]},
                new int[]{nearTopLeft[1], farTopLeft[1], farBottomLeft[1], nearBottomLeft[1]},
                4
        );
        Polygon rightFace = new Polygon(
                new int[]{nearTopRight[0], farTopRight[0], farBottomRight[0], nearBottomRight[0]},
                new int[]{nearTopRight[1], farTopRight[1], farBottomRight[1], nearBottomRight[1]},
                4
        );
        Polygon frontFace = new Polygon(
                new int[]{nearTopLeft[0], nearTopRight[0], nearBottomRight[0], nearBottomLeft[0]},
                new int[]{nearTopLeft[1], nearTopRight[1], nearBottomRight[1], nearBottomLeft[1]},
                4
        );

        g2.setColor(GameColors.HAZARD_LEFT);
        g2.fillPolygon(leftFace);
        g2.setColor(GameColors.HAZARD_RIGHT);
        g2.fillPolygon(rightFace);
        g2.setColor(GameColors.HAZARD_FRONT);
        g2.fillPolygon(frontFace);
        g2.setColor(GameColors.HAZARD_TOP);
        g2.fillPolygon(topFace);

        g2.setColor(GameColors.HAZARD_OUTLINE_TOP);
        g2.drawPolygon(topFace);
        g2.setColor(GameColors.HAZARD_OUTLINE_FRONT);
        g2.drawPolygon(frontFace);
    }

    /**
     * Draws a translucent red plane at the kill threshold to give the player a
     * visual sense of the death boundary.
     * 
     * Sources used:
     * Claude Sonnet 4.6 for the initial logic and math to determine the plane corners in world space, which was 
     *      then transformed and projected like the slope segments.
     *
     * @param g2 the 2D graphics context
     */
    private void drawKillPlane(Graphics2D g2) {
        Sphere sphere = env.getSphere();
        Slope slope = env.getSlope();
        double visNearZ = sphere.z + 18.0;
        double visFarZ  = sphere.z - 120.0;
        double step = 3.0;

        // Walk back-to-front in small steps; skip z ranges suppressed by corkscrew.
        for (double z = visFarZ; z < visNearZ; z += step) {
            double zNear = Math.min(visNearZ, z + step);
            double killNear = slope.getKillY(zNear);
            double killFar  = slope.getKillY(z);
            if (!Double.isFinite(killNear) || !Double.isFinite(killFar)) {
                continue;
            }

            int[] nLeft  = project(-KILL_PLANE_HALF_WIDTH, killNear, zNear);
            int[] nRight = project( KILL_PLANE_HALF_WIDTH, killNear, zNear);
            int[] fRight = project( KILL_PLANE_HALF_WIDTH, killFar,  z);
            int[] fLeft  = project(-KILL_PLANE_HALF_WIDTH, killFar,  z);
            if (nLeft == null || nRight == null || fRight == null || fLeft == null) {
                continue;
            }

            Polygon slice = new Polygon(
                    new int[]{nLeft[0], nRight[0], fRight[0], fLeft[0]},
                    new int[]{nLeft[1], nRight[1], fRight[1], fLeft[1]},
                    4
            );
            g2.setColor(GameColors.KILL_PLANE_FILL);
            g2.fillPolygon(slice);
            g2.setColor(GameColors.KILL_PLANE_OUTLINE);
            g2.drawPolygon(slice);
        }
    }

    /**
     * Draws the sky background as a vertical gradient from dark navy at the top
     * to medium blue at the bottom.
     *
     * @param g2 the 2D graphics context
     */
    private void drawSky(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        GradientPaint sky = new GradientPaint(
                0, 0, GameColors.SKY_TOP,
                0, h, GameColors.SKY_BOTTOM
        );
        g2.setPaint(sky);
        g2.fillRect(0, 0, w, h);
    }
}
