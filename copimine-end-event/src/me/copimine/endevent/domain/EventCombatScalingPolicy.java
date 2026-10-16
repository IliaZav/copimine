package me.copimine.endevent.domain;

/**
 * Bounded pressure profile. It describes simultaneous hostile pressure, not
 * a kill quota or a limit on assigned targets. Squad targeting remains a
 * separate decision, and major ability wind-ups are admitted by the combat
 * coordinator's single lease.
 */
public final class EventCombatScalingPolicy {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final int GLOBAL_HOSTILE_CAP = 56;

    private EventCombatScalingPolicy() {
    }

    public record Profile(int players, int commonMobs, int elites, int heavyMechanics,
                          int mechanicSlots, int maxConcurrentHostiles) {
        public Profile {
            players = clamp(players, MIN_PLAYERS, MAX_PLAYERS);
            commonMobs = Math.max(0, commonMobs);
            elites = Math.max(0, elites);
            heavyMechanics = Math.max(0, heavyMechanics);
            mechanicSlots = Math.max(0, mechanicSlots);
            maxConcurrentHostiles = Math.min(GLOBAL_HOSTILE_CAP, Math.max(0, maxConcurrentHostiles));
        }
    }

    public static Profile forPlayers(int players) {
        int safe = clamp(players, MIN_PLAYERS, MAX_PLAYERS);
        int common;
        if (safe <= 4) {
            common = safe + 2;
        } else if (safe <= 6) {
            common = 7;
        } else if (safe <= 8) {
            common = 8;
        } else if (safe <= 10) {
            common = safe;
        } else if (safe <= 15) {
            common = 11;
        } else {
            common = 12;
        }
        int elites = safe <= 5 ? 1 : safe <= 13 ? 2 : 3;
        int heavy = safe <= 5 ? 1 : safe <= 15 ? 2 : 3;
        int mechanics = safe <= 5 ? 1 : safe <= 10 ? 2 : 3;
        return new Profile(safe, common, elites, heavy, mechanics, common + elites);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
