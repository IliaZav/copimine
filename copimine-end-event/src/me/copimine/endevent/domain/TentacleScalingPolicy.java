package me.copimine.endevent.domain;

/** Bounded tentacle pressure for the six V2 boss stages. */
public final class TentacleScalingPolicy {
    public static final int MAX_PERMANENT = 8;
    public static final int MAX_TEMPORARY = 6;

    private TentacleScalingPolicy() {
    }

    public static int permanentFor(int players, V2BossStage stage) {
        int safePlayers = Math.max(0, Math.min(20, players));
        if (safePlayers == 0 || stage == null) {
            return 0;
        }
        int base = switch (stage) {
            case AWAKENING -> 0;
            case HUNT -> 2;
            case RIFT -> 3;
            case OVERLOAD -> 5;
            case RAGE -> 6;
            case LAST_SEAL -> 8;
        };
        int partyPressure = safePlayers >= 16 && base > 0 ? 1 : safePlayers >= 10 && base > 0 ? 1 : 0;
        return Math.min(MAX_PERMANENT, base + partyPressure);
    }

    public static int temporaryFor(int players, V2BossStage stage) {
        int safePlayers = Math.max(0, Math.min(20, players));
        if (safePlayers == 0 || stage == null
                || (stage != V2BossStage.RAGE && stage != V2BossStage.LAST_SEAL)) {
            return 0;
        }
        return Math.min(MAX_TEMPORARY, Math.max(1, (safePlayers + 3) / 4));
    }
}
