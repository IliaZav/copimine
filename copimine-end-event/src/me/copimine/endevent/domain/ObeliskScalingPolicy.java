package me.copimine.endevent.domain;

/** Exact, bounded Wave 4 obelisk and pressure scaling. */
public final class ObeliskScalingPolicy {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final int MAX_OBELISKS = 7;
    public static final int MAX_FIREBALL_ADMISSION_SLOTS = 48;

    private ObeliskScalingPolicy() {
    }

    public static Profile profileForPlayers(int livingPlayers) {
        if (livingPlayers < MIN_PLAYERS) {
            return Profile.NONE;
        }
        if (livingPlayers <= 4) {
            return configuredProfile(5, 6);
        }
        if (livingPlayers <= 7) {
            return configuredProfile(5, 8);
        }
        if (livingPlayers <= 10) {
            return configuredProfile(6, 10);
        }
        if (livingPlayers <= 15) {
            return configuredProfile(6, 12);
        }
        return configuredProfile(7, 16);
    }

    private static Profile configuredProfile(int towers, int mobs) {
        int integrity = 3;
        return new Profile(towers, integrity, towers * integrity, mobs,
                ObeliskFireDirectorPolicy.fireballAdmissionSlots(towers));
    }

    public static int obeliskCount(int livingPlayers) {
        return profileForPlayers(livingPlayers).obeliskCount();
    }

    public static int mobCount(int livingPlayers) {
        return profileForPlayers(livingPlayers).mobCount();
    }

    public static int fireballCap(int livingPlayers) {
        return profileForPlayers(livingPlayers).fireballCap();
    }

    public record Profile(int obeliskCount, int health, int requiredHits,
                          int mobCount, int fireballCap) {
        public static final Profile NONE = new Profile(0, 0, 0, 0, 0);

        public Profile {
            if (obeliskCount < 0 || obeliskCount > MAX_OBELISKS
                    || health < 0 || requiredHits < 0 || mobCount < 0 || fireballCap < 0) {
                throw new IllegalArgumentException("invalid obelisk profile");
            }
            if (obeliskCount > 0 && requiredHits != obeliskCount * health) {
                throw new IllegalArgumentException("required hits must match obelisk count and health");
            }
        }

        public int totalHealth() {
            return obeliskCount * health;
        }
    }
}
