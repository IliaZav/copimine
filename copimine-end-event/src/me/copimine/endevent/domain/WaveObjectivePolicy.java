package me.copimine.endevent.domain;

/**
 * Pure mapping and completion predicates for the five initial waves.
 *
 * <p>The mob-cap oracle is the current {@code copimine-end-event/config.yml}
 * wave composition, scaled by the runtime's maximum player scale of 2.0 and
 * bounded by {@code waves.hard-cap: 48}. The configured base totals are
 * 13, 16, 17, 20, and 30, so the policy caps are 26, 32, 34, 40, and 48.
 */
public final class WaveObjectivePolicy {
    public static final int CONFIGURED_WAVE_HARD_CAP = 48;
    private static final int MAX_PLAYER_SCALE = 2;
    private static final int[] CONFIGURED_BASE_MOB_TOTALS = {13, 16, 17, 20, 30};

    private WaveObjectivePolicy() { }

    public static Objective objective(int wave) {
        return switch (wave) {
            case 1 -> Objective.AWAKENING;
            case 2 -> Objective.HUNT;
            case 3 -> Objective.PORTALS;
            case 4 -> Objective.TOWER_DEFENSE;
            case 5 -> Objective.RIFT_STORM;
            default -> throw new IllegalArgumentException("unknown wave: " + wave);
        };
    }

    public static String title(int wave) { return objective(wave).title(); }
    public static int mobCap(int wave) { return objective(wave).mobCap(); }

    public static boolean isComplete(int wave, Progress progress) {
        if (progress == null) return false;
        return switch (objective(wave)) {
            case AWAKENING, HUNT -> progress.requiredMobs() > 0
                    && progress.mobsDefeated() >= progress.requiredMobs();
            case PORTALS -> progress.requiredPortals() > 0
                    && progress.portalsCaptured() >= progress.requiredPortals();
            case TOWER_DEFENSE -> progress.towerSuccess();
            case RIFT_STORM -> progress.riftStormCleared();
        };
    }

    public enum Objective {
        AWAKENING("Awakening", cap(0)), HUNT("Hunt", cap(1)), PORTALS("Portals", cap(2)),
        TOWER_DEFENSE("Tower Defense", cap(3)), RIFT_STORM("Rift Storm", cap(4));
        private final String title;
        private final int mobCap;
        Objective(String title, int mobCap) { this.title = title; this.mobCap = mobCap; }
        public String title() { return title; }
        public int mobCap() { return mobCap; }
    }

    private static int cap(int waveIndex) {
        return Math.min(CONFIGURED_WAVE_HARD_CAP, CONFIGURED_BASE_MOB_TOTALS[waveIndex] * MAX_PLAYER_SCALE);
    }

    public record Progress(int mobsDefeated, int requiredMobs, int portalsCaptured, int requiredPortals,
                           boolean towerSuccess, boolean riftStormCleared) {
        public Progress {
            if (mobsDefeated < 0 || requiredMobs < 0 || portalsCaptured < 0 || requiredPortals < 0) {
                throw new IllegalArgumentException("progress cannot be negative");
            }
        }
    }
}
