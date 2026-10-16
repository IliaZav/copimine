package me.copimine.endevent.domain;

/** Exact, bounded Wave 4 obelisk and pressure scaling. */
public final class ObeliskScalingPolicy {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final int MAX_OBELISKS = 6;

    private ObeliskScalingPolicy() {
    }

    public static Profile profileForPlayers(int livingPlayers) {
        if (livingPlayers < MIN_PLAYERS) {
            return Profile.NONE;
        }
        if (livingPlayers <= 4) {
            return new Profile(4, 3, 12, 3, 1);
        }
        if (livingPlayers <= 7) {
            return new Profile(4, 3, 12, 4, 2);
        }
        if (livingPlayers <= 10) {
            return new Profile(5, 3, 15, 5, 2);
        }
        if (livingPlayers <= 15) {
            return new Profile(5, 3, 15, 6, 3);
        }
        return new Profile(6, 3, 18, 8, 3);
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
