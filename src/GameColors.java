
/**
 * Central palette for all game colors.
 *
 * Edit values here to change any color without touching rendering or UI code.
 */
import java.awt.Color;

public final class GameColors {

    private GameColors() {
    }

    public static final Color SKY_TOP = new Color(8, 10, 28);
    public static final Color SKY_BOTTOM = new Color(78, 116, 174);

    public static final Color SLOPE_DOWNHILL = new Color(132, 97, 62);
    public static final Color SLOPE_RAMP = new Color(92, 124, 80);
    public static final Color SLOPE_CORKSCREW = new Color(110, 80, 120);
    public static final Color SLOPE_GAP = new Color(0, 0, 0, 0);
    public static final Color SLOPE_EDGE = new Color(255, 230, 180, 80);

    public static final Color HAZARD_TOP = new Color(255, 92, 92);
    public static final Color HAZARD_LEFT = new Color(190, 24, 24);
    public static final Color HAZARD_RIGHT = new Color(145, 18, 18);
    public static final Color HAZARD_FRONT = new Color(220, 42, 42);
    public static final Color HAZARD_OUTLINE_TOP = new Color(255, 208, 208, 130);
    public static final Color HAZARD_OUTLINE_FRONT = new Color(110, 0, 0, 130);

    public static final Color KILL_PLANE_FILL = new Color(185, 36, 36, 42);
    public static final Color KILL_PLANE_OUTLINE = new Color(255, 110, 110, 100);

    public static final Color SPHERE_GLASS = new Color(210, 235, 255, 32);
    public static final Color SPHERE_SHINE_A = new Color(255, 255, 255, 150);
    public static final Color SPHERE_SHINE_B = new Color(255, 255, 255, 18);
    public static final Color SPHERE_SHADOW = new Color(18, 26, 44, 70);
    public static final Color EXPLOSION_OUTER = new Color(255, 122, 54, 220);
    public static final Color EXPLOSION_INNER = new Color(255, 230, 120, 180);

    public static final Color UI_SCORE_TEXT = new Color(255, 255, 255, 230);
    public static final Color UI_OVERLAY_PANEL = new Color(3, 6, 16, 210);
    public static final Color UI_OVERLAY_BORDER = new Color(255, 255, 255, 45);
    public static final Color UI_PRIMARY_TEXT = Color.WHITE;
    public static final Color UI_SECONDARY_TEXT = new Color(230, 235, 255);
    public static final Color UI_WARNING_TEXT = new Color(255, 0, 0);
}
