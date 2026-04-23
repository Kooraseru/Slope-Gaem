/**
 * src/GameEnvironment.java
 *
 * Manages the game world state, including the sphere, slope, explosion animation,
 * score tracking, and the game state machine (RUNNING, EXPLODING, DEAD). Acts as
 * the single source of truth for game state; Viewer3D and GameUI read from this
 * class each frame but do not own any game state themselves.
 * 
 * Owns and updates all game objects and state for the Slope game.
 *
 * Holds the Sphere, Slope, and ExplosionAnimation, drives the game state machine
 * (RUNNING to EXPLODING to DEAD), and accumulates the score. Rendering (Viewer3D)
 * and the HUD (GameUI) read from this class each frame but do not own any game
 * state themselves.
 *
 * Sources used:
 * https://github.com/pajouheshgar/3D-Swing/blob/master/src/GameEnv3D.java for the original game environment structure and update loop,
 *      which was heavily modified and expanded upon for this project.
 * Claude Sonnet 4.6 for Javadoc and refactoring support.
 *
 * @since 2026-04-03
 */

public class GameEnvironment {
    /** The lifecycle state of the current round. */
    public enum GameState {
        RUNNING,
        EXPLODING,
        DEAD
    }

    private static final long POST_EXPLOSION_DELAY_NANOS = 1_000_000_000L;

    private final Sphere sphere;
    private final Slope slope;
    private final ExplosionAnimation explosionAnimation;

    private boolean awaitingStart = true;
    private GameState gameState = GameState.RUNNING;
    private long explosionStartTime = -1L;
    private double score = 0.0;
    private double finalScore = 0.0;

    /**
     * Constructs a new game environment wrapping the given game objects.
     *
     * @param sphere the player sphere to simulate; must not be null
     * @param slope the slope defining track geometry; must not be null
     * @param explosionAnimation the pre-loaded explosion GIF frames; must not be null
     */
    public GameEnvironment(Sphere sphere, Slope slope, ExplosionAnimation explosionAnimation) {
        this.sphere = sphere;
        this.slope = slope;
        this.explosionAnimation = explosionAnimation;
    }

    /**
     * Advances the game simulation by one time step.
     *
     * Does nothing while the opening instructions are still awaiting confirmation.
     * When RUNNING after the player has started, updates the sphere physics and
     * increments the score.
     * When EXPLODING, waits for the explosion animation plus a delay before
     * transitioning to DEAD.
     *
     * @param dt elapsed time in seconds since the last tick; must be positive
     */
    public void tick(double dt) {
        if (awaitingStart) {
            return;
        }

        double oldScore = score;
        if (gameState == GameState.RUNNING) {
            long now = System.nanoTime();
            slope.updateHazards(dt);
            boolean died = sphere.update(dt, slope);
            score += dt;
            System.out.println(String.format(
                    "[GAME TICK] dt=%.3fs | sphere.z=%.2f | score=%.2f -> %.2f | thread=%s",
                    dt, sphere.z, oldScore, score, Thread.currentThread().getName()));
            if (died) {
                gameState = GameState.EXPLODING;
                explosionStartTime = now;
                finalScore = score;
                System.out.println("[DEATH] Sphere died at score: " + finalScore);
            }
        } else if (gameState == GameState.EXPLODING) {
            long now = System.nanoTime();
            long totalWaitNanos = explosionAnimation.getTotalDurationNanos() + POST_EXPLOSION_DELAY_NANOS;
            if (now - explosionStartTime >= totalWaitNanos) {
                gameState = GameState.DEAD;
                System.out.println("[STATE] EXPLODING -> DEAD");
            }
        }
    }

    /**
     * Resets all game objects and state to their initial values and resumes play.
     */
    public void respawn() {
        slope.reset();
        sphere.placeAtStart(slope);
        awaitingStart = false;
        score = 0.0;
        finalScore = 0.0;
        explosionStartTime = -1L;
        gameState = GameState.RUNNING;
    }

    /**
     * Starts the first round after the player dismisses the instructions screen.
     *
     * Has no effect once the game has already started.
     */
    public void startGame() {
        awaitingStart = false;
    }

    /**
     * Returns the current game state.
     *
     * @return the current GameState
     */
    public GameState getGameState() {
        return gameState;
    }

    /**
     * Returns the accumulated score for the current run.
     *
     * @return elapsed seconds while RUNNING this round
     */
    public double getScore() {
        return score;
    }

    /**
     * Returns the score frozen at the moment of death.
     *
     * @return the final score from the most recent death, or 0.0 if the
     *         player has not yet died
     */
    public double getFinalScore() {
        return finalScore;
    }

    /**
     * Returns the nanosecond timestamp when the explosion started.
     *
     * @return the value of System.nanoTime() at explosion start, or -1
     *         if no explosion has occurred this round
     */
    public long getExplosionStartTime() {
        return explosionStartTime;
    }

    /**
     * Returns the player sphere.
     *
     * @return the Sphere instance
     */
    public Sphere getSphere() {
        return sphere;
    }

    /**
     * Returns the slope defining the track geometry.
     *
     * @return the Slope instance
     */
    public Slope getSlope() {
        return slope;
    }

    /**
     * Returns the explosion animation used when the sphere dies.
     *
     * @return the ExplosionAnimation instance
     */
    public ExplosionAnimation getExplosionAnimation() {
        return explosionAnimation;
    }
}
