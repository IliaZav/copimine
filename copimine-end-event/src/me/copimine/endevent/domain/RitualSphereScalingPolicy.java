package me.copimine.endevent.domain;

/** Bounded roster pressure table with five fixed Caster spell roles. */
public final class RitualSphereScalingPolicy {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final int GUARDS_PER_CASTER = 3;
    public static final int MAX_PROJECTILES_PER_VOLLEY = 7;

    private RitualSphereScalingPolicy() {
    }

    public static Profile forPlayers(int players) {
        int count = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, players));
        int projectiles = count <= 2 ? 3
                : count == 3 ? 4
                : count <= 5 ? 5
                : count == 6 ? 6 : 7;
        if (count <= 4) {
            return new Profile(count, 5, 5, projectiles, 1, 13);
        }
        if (count <= 8) {
            return new Profile(count, 5, 10, projectiles, 1, 12);
        }
        if (count <= 12) {
            return new Profile(count, 5, 15, projectiles, 2, 11);
        }
        if (count <= 16) {
            return new Profile(count, 5, 15, projectiles, 2, 10);
        }
        return new Profile(count, 5, 15, projectiles, 3, 9);
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
                throw new IllegalArgumentException("ritual guard count must be one to three per caster");
            }
            if (projectilesPerVolley < 3 || projectilesPerVolley > MAX_PROJECTILES_PER_VOLLEY
                    || simultaneousZones < 1 || simultaneousZones > 3
                    || majorCooldownSeconds < 9 || majorCooldownSeconds > 13) {
                throw new IllegalArgumentException("invalid ritual scaling profile");
            }
        }

        public int guardsPerCaster() {
            return guardCount / casterCount;
        }
    }
}
