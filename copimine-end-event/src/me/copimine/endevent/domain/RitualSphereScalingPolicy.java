package me.copimine.endevent.domain;

/** Bounded roster pressure table with five fixed Caster spell roles. */
public final class RitualSphereScalingPolicy {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final int GUARDS_PER_CASTER = 5;
    public static final int MAX_PROJECTILES_PER_VOLLEY = 7;

    private RitualSphereScalingPolicy() {
    }

    /**
     * Roster profile for the ritual sphere.
     *
     * <p>The guard table is the user contract W6-009: one guard for a 2-4 party
     * and one more per four players, capped at the five-per-mage ceiling. It is
     * evaluated from the roster frozen at admission, never from survivors.</p>
     */
    public static Profile forPlayers(int players) {
        int count = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, players));
        int projectiles = count <= 2 ? 3
                : count == 3 ? 4
                : count <= 5 ? 5
                : count == 6 ? 6 : 7;
        int perMage = WaveSixRosterPolicy.guardsFor(count);
        int guards = 5 * perMage;
        if (count <= 4) {
            return new Profile(count, 5, guards, projectiles, 1, 14);
        }
        if (count <= 8) {
            return new Profile(count, 5, guards, projectiles, 1, 14);
        }
        if (count <= 12) {
            return new Profile(count, 5, guards, projectiles, 2, 14);
        }
        if (count <= 16) {
            return new Profile(count, 5, guards, projectiles, 2, 14);
        }
        return new Profile(count, 5, guards, projectiles, 3, 14);
    }

    /** Bounded ordinary pressure that complements, but never advances, caster progression. */
    public static WaveMechanicsPolicy.WaveCounts pressureMobsForPlayers(int players) {
        int count = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, players));
        int activePressure = PressureBudgetController.profileForPlayers(count).activePressure();
        int total = Math.max(4, Math.min(8, activePressure / 2));
        int elites = 1;
        int common = total - elites;
        int endermen = (common + 1) / 2;
        int spiders = common - endermen;
        int eliteEndermen = elites > 0 ? 1 : 0;
        int eliteSkeletons = elites > 1 ? 1 : 0;
        return new WaveMechanicsPolicy.WaveCounts(endermen, spiders,
                0, eliteEndermen, eliteSkeletons);
    }

    public record Profile(int participants, int casterCount, int guardCount,
                          int projectilesPerVolley, int simultaneousZones,
                          int majorCooldownSeconds) {
        public Profile {
            if (participants < MIN_PLAYERS || participants > MAX_PLAYERS) {
                throw new IllegalArgumentException("ritual participants outside 2-20: " + participants);
            }
            if (casterCount != 5) {
                throw new IllegalArgumentException("ritual encounter requires exactly five casters");
            }
            if (guardCount % casterCount != 0
                    || guardCount / casterCount < 1
                    || guardCount / casterCount > GUARDS_PER_CASTER) {
                throw new IllegalArgumentException("ritual guard count must be one to five per caster");
            }
            if (projectilesPerVolley < 3 || projectilesPerVolley > MAX_PROJECTILES_PER_VOLLEY
                    || simultaneousZones < 1 || simultaneousZones > 3
                    || majorCooldownSeconds < 12 || majorCooldownSeconds > 15) {
                throw new IllegalArgumentException("invalid ritual scaling profile");
            }
        }

        public int guardsPerCaster() {
            return guardCount / casterCount;
        }
    }
}
