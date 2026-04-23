/**
 * src/Main.java
 *
 * Main class for the Slope game application. Initializes the game components, loads resources,
 * and sets up the main application window with a layered pane to display the 3D scene and HUD.
 *
 * Was originally going to have the little kill boxes that appear in the actual slope game,
 * but didn't have the time to implement them. Also if the ball goes off the track then moves back
 * onto the track, it "teleports" back to the track instead of still falling down.
 *
 * The ball image is from Omori, created by Omocat.
 * The explosion gif is from Deltarune, created by Toby Fox.
 *
 * Sources used:
 * https://stackoverflow.com/questions/17680817/difference-between-requestfocusinwindow-and-grabfocus-in-swing for focus handling.
 * https://stackoverflow.com/questions/29267761/how-do-i-import-a-jpg-image-in-java for image loading.
 * https://docs.oracle.com/javase/8/docs/api/javax/swing/SwingUtilities.html for EDT initialization.
 * https://docs.oracle.com/javase/8/docs/api/javax/swing/JLayeredPane.html for layered pane layout.
 * ChatGPT 5.4 for initial structure and design of files.
 * Claude Sonnet 4.6 for support for GIFs and classpath resources, and error handling improvements.
 *
 * @since 2026-03-25
 */
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import javax.imageio.*;
import javax.swing.*;

public class Main {
    private static final String DEFAULT_BALL_IMAGE_PATH = "resources/Image.png";

    /**
     * Loads an image from the specified path. Tries the classpath first, then the filesystem
     * with a fallback into a "resources/" subdirectory. Returns null if the image
     * cannot be found or read.
     */
    public static BufferedImage loadImage(String path) {
        try (InputStream resourceStream = Main.class.getClassLoader().getResourceAsStream(path)) {
            if (resourceStream != null) {
                return ImageIO.read(resourceStream);
            }
        } catch (IOException e) {
            System.err.println("Failed to load classpath image '" + path + "': " + e.getMessage());
        }

        try {
            Path imagePath = Path.of(path);
            if (!Files.exists(imagePath)) {
                imagePath = Path.of("resources", Path.of(path).getFileName().toString());
            }
            if (Files.exists(imagePath)) {
                return ImageIO.read(imagePath.toFile());
            }
        } catch (IOException e) {
            System.err.println("Failed to load file image '" + path + "': " + e.getMessage());
        }

        System.err.println("Image not found: " + path);
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Slope slope = new Slope();

            BufferedImage defaultBallImage = loadImage(DEFAULT_BALL_IMAGE_PATH);
            ExplosionAnimation explosionAnimation = ExplosionAnimation.load("resources/Explosion.gif");

            Sphere sphere = new Sphere(defaultBallImage);
            sphere.placeAtStart(slope);

            GameEnvironment env = new GameEnvironment(sphere, slope, explosionAnimation);

            Viewer3D viewer = new Viewer3D(env);
            JFrame frame = new JFrame("Slope gaem idk");
            GameUI gameUI = new GameUI(env::respawn, () -> {
                env.startGame();
                viewer.requestFocusInWindow();
            });
            gameUI.setGameEnvironment(env);
            gameUI.setOnSurprise(() -> {
                CameraPreviewDialog dialog = new CameraPreviewDialog(frame);
                dialog.setVisible(true);
                BufferedImage captured = dialog.getCapturedImage();
                if (captured != null) {
                    sphere.setImage(captured);
                } else {
                    String error = OpenCvBridge.getLastError();
                    sphere.setImage(defaultBallImage);
                    JOptionPane.showMessageDialog(
                        frame,
                        (error == null ? "No photo was captured." : error)
                            + "\nUsing resources/Image.png instead.",
                        "suprise",
                        JOptionPane.WARNING_MESSAGE
                    );
                }
                viewer.repaint();
            });
            viewer.setGameUI(gameUI);

            JLayeredPane layeredPane = new JLayeredPane() {
                @Override
                public void doLayout() {
                    for (java.awt.Component c : getComponents()) {
                        c.setBounds(0, 0, getWidth(), getHeight());
                    }
                }
            };
            layeredPane.add(viewer, JLayeredPane.DEFAULT_LAYER);
            layeredPane.add(gameUI, JLayeredPane.PALETTE_LAYER);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.setContentPane(layeredPane);
            frame.setVisible(true);
            SwingUtilities.invokeLater(viewer::requestFocusInWindow);
        });
    }
}
