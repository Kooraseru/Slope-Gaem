/**
 * src/GameUI.java
 *
 * Swing JPanel that serves as the heads-up display (HUD) for the Slope game.
 * Displays an instructions screen on startup with a Play button. Once Play is
 * clicked, the instructions are dismissed and gameplay begins. During play,
 * shows the player's current score. When the player dies, displays a death screen
 * with the final score and a Respawn button. Reads game state from GameEnvironment
 * each tick via update(), detecting state transitions to show or hide the death
 * screen accordingly.
 *
 * Sources used:
 * https://docs.oracle.com/javase/8/docs/api/java/awt/Font.html for font configuration.
 * https://docs.oracle.com/javase/8/docs/api/java/awt/BorderLayout.html for layout managers.
 * https://docs.oracle.com/javase/8/docs/api/javax/swing/BoxLayout.html for layout managers.
 * https://stackoverflow.com/questions/59763059/change-the-font-style-size-in-a-java-swing-appliation for font styling.
 * ChatGPT 5.4 for initial structure and design of files. 
 *
 * @since 2026-03-27
 */
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class GameUI extends JPanel {
    private static final String UI_FONT_FAMILY = "SansSerif";

    private static final Color SCORE_TEXT_COLOR      = GameColors.UI_SCORE_TEXT;
    private static final Color OVERLAY_PANEL_COLOR   = GameColors.UI_OVERLAY_PANEL;
    private static final Color OVERLAY_BORDER_COLOR  = GameColors.UI_OVERLAY_BORDER;
    private static final Color PRIMARY_TEXT_COLOR    = GameColors.UI_PRIMARY_TEXT;
    private static final Color SECONDARY_TEXT_COLOR  = GameColors.UI_SECONDARY_TEXT;
    private static final Color INSTRUCTION_TEXT_COLOR = GameColors.UI_PRIMARY_TEXT;
    private static final Color WARNING_TEXT_COLOR    = GameColors.UI_WARNING_TEXT;

    private final JLabel scoreLabel;
    private final JPanel deathPanel;
    private final JLabel finalScoreLabel;
    private final JPanel instructionsPanel;
    private int lastScoreDisplayed = -1;
    private Runnable onSurprise = () -> { };

    private GameEnvironment env;
    private GameEnvironment.GameState lastGameState = GameEnvironment.GameState.RUNNING;

    /**
     * Creates a new GameUI instance with the specified respawn and play callbacks. The HUD is
     * initialized with a score label at the top, an instructions screen shown immediately on
     * startup, and a hidden death screen in the center. The play callback is invoked when the
     * player clicks Play on the instructions screen; the respawn callback is invoked when the
     * player clicks Respawn on the death screen.
     *
     * Sources used:
     * https://docs.oracle.com/javase/8/docs/api/java/awt/BorderLayout.html for layout managers.
     * https://docs.oracle.com/javase/8/docs/api/javax/swing/BoxLayout.html for layout managers.
     * https://docs.oracle.com/javase/8/docs/api/java/awt/Font.html for font configuration.
     * https://stackoverflow.com/questions/59763059/change-the-font-style-size-in-a-java-swing-appliation for font styling.
     * Claude Sonnet 4.6
     *
     * @param onRespawn the callback invoked when the player clicks the Respawn button;
     *     must not be null
     * @param onPlay the callback invoked when the player clicks the Play button on the
     *     instructions screen; must not be null
     */
    public GameUI(Runnable onRespawn, Runnable onPlay) {
        setOpaque(false);
        setLayout(new BorderLayout());
        setFocusable(false);

        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setForeground(SCORE_TEXT_COLOR);
        scoreLabel.setFont(new Font(UI_FONT_FAMILY, Font.BOLD, 22));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 16));
        topBar.setOpaque(false);
        topBar.add(scoreLabel);
        add(topBar, BorderLayout.NORTH);

        deathPanel = new JPanel();
        deathPanel.setOpaque(true);
        deathPanel.setBackground(OVERLAY_PANEL_COLOR);
        deathPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(OVERLAY_BORDER_COLOR, 1),
            new EmptyBorder(28, 42, 28, 42)
        ));
        deathPanel.setLayout(new BoxLayout(deathPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("You Died");
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(PRIMARY_TEXT_COLOR);
        titleLabel.setFont(new Font(UI_FONT_FAMILY, Font.BOLD, 42));

        finalScoreLabel = new JLabel("Final Score: 0");
        finalScoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        finalScoreLabel.setForeground(SECONDARY_TEXT_COLOR);
        finalScoreLabel.setFont(new Font(UI_FONT_FAMILY, Font.PLAIN, 24));

        JButton respawnButton = new JButton("Respawn");
        respawnButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        respawnButton.setFocusable(false);
        respawnButton.addActionListener(e -> onRespawn.run());

        deathPanel.add(titleLabel);
        deathPanel.add(Box.createVerticalStrut(14));
        deathPanel.add(finalScoreLabel);
        deathPanel.add(Box.createVerticalStrut(20));
        deathPanel.add(respawnButton);
        deathPanel.setVisible(false);

        instructionsPanel = new JPanel();
        instructionsPanel.setOpaque(true);
        instructionsPanel.setBackground(OVERLAY_PANEL_COLOR);
        instructionsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(OVERLAY_BORDER_COLOR, 1),
            new EmptyBorder(28, 42, 28, 42)
        ));
        instructionsPanel.setLayout(new BoxLayout(instructionsPanel, BoxLayout.Y_AXIS));

        JLabel gameTitleLabel = new JLabel("Slope");
        gameTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        gameTitleLabel.setForeground(PRIMARY_TEXT_COLOR);
        gameTitleLabel.setFont(new Font(UI_FONT_FAMILY, Font.BOLD, 48));

        JLabel instructionsLine1 = new JLabel("Use the Left and Right arrow keys to steer.");
        instructionsLine1.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructionsLine1.setForeground(INSTRUCTION_TEXT_COLOR);
        instructionsLine1.setFont(new Font(UI_FONT_FAMILY, Font.PLAIN, 16));

        JLabel instructionsLine2 = new JLabel("Or use the A and D keys... whatever floats your boat.");
        instructionsLine2.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructionsLine2.setForeground(INSTRUCTION_TEXT_COLOR);
        instructionsLine2.setFont(new Font(UI_FONT_FAMILY, Font.PLAIN, 16));

        JLabel instructionsLine3 = new JLabel("Survive as long as possible.");
        instructionsLine3.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructionsLine3.setForeground(WARNING_TEXT_COLOR);
        instructionsLine3.setFont(new Font(UI_FONT_FAMILY, Font.PLAIN, 16));

        JButton playButton = new JButton("Play");
        playButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        playButton.setFocusable(false);
        playButton.addActionListener(e -> {
            instructionsPanel.setVisible(false);
            onPlay.run();
        });

        JButton surpriseButton = new JButton("suprise");
        surpriseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        surpriseButton.setFocusable(false);
        surpriseButton.addActionListener(e -> onSurprise.run());

        instructionsPanel.add(gameTitleLabel);
        instructionsPanel.add(Box.createVerticalStrut(18));
        instructionsPanel.add(instructionsLine1);
        instructionsPanel.add(Box.createVerticalStrut(6));
        instructionsPanel.add(instructionsLine2);
        instructionsPanel.add(Box.createVerticalStrut(6));
        instructionsPanel.add(instructionsLine3);
        instructionsPanel.add(Box.createVerticalStrut(24));
        instructionsPanel.add(surpriseButton);
        instructionsPanel.add(Box.createVerticalStrut(12));
        instructionsPanel.add(playButton);

        JPanel centerWrap = new JPanel(new GridBagLayout());
        centerWrap.setOpaque(false);
        centerWrap.add(instructionsPanel);
        centerWrap.add(deathPanel);
        add(centerWrap, BorderLayout.CENTER);
    }

    /**
     * Registers the game environment so this HUD can read game state during
     * update() calls.
     *
     * @param env the game environment to observe; must not be null
     */
    public void setGameEnvironment(GameEnvironment env) {
        this.env = env;
    }

    void setOnSurprise(Runnable onSurprise) {
        this.onSurprise = onSurprise == null ? () -> { } : onSurprise;
    }

    /**
     * Synchronizes the HUD with the current game state. Updates the score label
     * and shows or hides the death screen based on state transitions. Called
     * once per game tick by Viewer3D.
     */
    public void update() {
        if (env == null) {
            return;
        }

        int shownScore = (int) Math.floor(
                env.getGameState() == GameEnvironment.GameState.RUNNING
                        ? env.getScore() : env.getFinalScore());
        setScore(shownScore);

        GameEnvironment.GameState currentState = env.getGameState();
        if (currentState != lastGameState) {
            System.out.println(String.format("[STATE_CHANGE] %s -> %s", lastGameState, currentState));
            if (currentState == GameEnvironment.GameState.DEAD) {
                System.out.println("[HUD] DISPLAYING DEATH SCREEN");
                showDeathScreen((int) Math.floor(env.getFinalScore()));
            } else {
                System.out.println("[HUD] HIDING DEATH SCREEN");
                hideDeathScreen();
            }
            lastGameState = currentState;
        }
    }

    /**
     * Updates the score displayed on the HUD. The score label is only refreshed when the
     * value has changed since the last call, avoiding unnecessary repaints.
     *
     * @param score the new score to display
     */
    public void setScore(int score) {
        if (score != lastScoreDisplayed) {
            lastScoreDisplayed = score;
            scoreLabel.setText("Score: " + score);
            repaint();
        }
    }

    /**
     * Displays the death screen with the given final score. Has no effect if the
     * death panel is already visible.
     *
     * @param score the final score to display on the death screen
     */
    public void showDeathScreen(int score) {
        finalScoreLabel.setText("Final Score: " + score);
        if (!deathPanel.isVisible()) {
            deathPanel.setVisible(true);
            System.out.println(String.format("[HUD] showDeathScreen() | score=%d | VISIBILITY CHANGED", score));
            repaint();
        } else {
            System.out.println(String.format("[HUD] showDeathScreen() | score=%d | already visible", score));
        }
    }

    /**
     * Hides the death screen. Has no effect if the death panel is already hidden.
     */
    public void hideDeathScreen() {
        if (deathPanel.isVisible()) {
            deathPanel.setVisible(false);
            System.out.println("[HUD] hideDeathScreen() | HIDING DEATH SCREEN");
            repaint();
        }
    }
}
