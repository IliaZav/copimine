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
        int partyTarget = safePlayers <= 2 ? 2
                : safePlayers <= 4 ? 3
                : safePlayers <= 7 ? 4
                : safePlayers <= 10 ? 5
                : safePlayers <= 15 ? 6
                : 8;
        int stageCap = switch (stage) {
            case AWAKENING -> 0;
            case HUNT -> 2;
            case RIFT -> 3;
            case OVERLOAD -> 5;
            case RAGE -> 6;
            case LAST_SEAL -> MAX_PERMANENT;
        };
        return Math.min(partyTarget, stageCap);
    }

    public static int temporaryFor(int players, V2BossStage stage) {
        int safePlayers = Math.max(0, Math.min(20, players));
        if (safePlayers == 0 || stage == null
                || (stage != V2BossStage.RAGE && stage != V2BossStage.LAST_SEAL)) {
            return 0;
        }
        int slots = safePlayers <= 4 ? 2
                : safePlayers <= 8 ? 3
                : safePlayers <= 12 ? 4
                : safePlayers <= 16 ? 5
                : 6;
        return Math.min(MAX_TEMPORARY, slots);
    }
}
