package me.copimine.endevent.domain;

/** Pure mapping and completion predicates for the five initial waves. */
public final class WaveObjectivePolicy {
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
            case AWAKENING, HUNT -> progress.mobsDefeated() >= progress.requiredMobs();
            case PORTALS -> progress.portalsCaptured() >= progress.requiredPortals();
            case TOWER_DEFENSE -> progress.towerSuccess();
            case RIFT_STORM -> progress.riftStormCleared();
        };
    }

    public enum Objective {
        AWAKENING("Awakening", 16), HUNT("Hunt", 20), PORTALS("Portals", 24),
        TOWER_DEFENSE("Tower Defense", 28), RIFT_STORM("Rift Storm", 32);
        private final String title;
        private final int mobCap;
        Objective(String title, int mobCap) { this.title = title; this.mobCap = mobCap; }
        public String title() { return title; }
        public int mobCap() { return mobCap; }
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
