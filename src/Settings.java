
/**
 * Central registry of all tunable game constants.
 *
 * Edit values here to adjust track geometry, segment chances, hazard behaviour,
 * and bobbing motion without touching any other file.
 */
public final class Settings {

    private Settings() {
    }

    public static final double SEGMENT_HALF_WIDTH = 1.5;
    public static final double BASE_SLOPE_DROP_PER_UNIT = 0.10;
    public static final double KILL_DEPTH = 14.0;
    public static final double GENERATION_DISTANCE = 240.0;

    public static final double MIN_DOWNHILL_LENGTH = 12.0;
    public static final double MAX_DOWNHILL_LENGTH = 22.0;
    public static final double MIN_RAMP_LENGTH = 8.0;
    public static final double MAX_RAMP_LENGTH = 11.0;
    public static final double MIN_GAP_LENGTH = 1.4;
    public static final double MAX_GAP_LENGTH = 2.6;
    public static final int MIN_GAP_SEGMENTS = 1;
    public static final int MAX_GAP_SEGMENTS = 3;
    public static final double MIN_RAMP_RISE = 1.8;
    public static final double MAX_RAMP_RISE = 3.0;

    public static final double MIN_CORKSCREW_LENGTH = 16.0;
    public static final double MAX_CORKSCREW_LENGTH = 28.0;
    public static final double CORKSCREW_BANK_ANGLE = 25.0;
    public static final double CORKSCREW_CYCLES = 2.0;

    public static final double MIN_CENTER_SHIFT = MAX_GAP_LENGTH;
    public static final double MAX_CENTER_SHIFT = MAX_GAP_LENGTH * 1.5;
    public static final double MIN_CENTER_X = -4.5;
    public static final double MAX_CENTER_X = 4.5;

    public static final double RAMP_CHANCE = 0.20;
    public static final double CORKSCREW_CHANCE = 0.00;

    public static final double STATIC_HAZARD_CHANCE = 0.34;
    public static final double BOBBING_HAZARD_CHANCE = 0.24;

    public static final double HAZARD_SIZE = SEGMENT_HALF_WIDTH * 2.0 / 6.0;
    public static final double HAZARD_CLEARANCE = 0.02;
    public static final double MIN_HAZARD_Z_PADDING = 3.0;

    public static final double MIN_BOB_AMPLITUDE = 1.0;
    public static final double MAX_BOB_AMPLITUDE = 5.0;
    public static final double MIN_BOB_SPEED = 2.2;
    public static final double MAX_BOB_SPEED = 3.6;
}
