package me.copimine.protocol;

/** Per-actor clock inside signed ENTITY_PHASE; never binds the official boss or its HP bar. */
public record GuardianActorFrame(Action action, long cast, int elapsedMillis, int durationMillis) {
    public enum Action {
        IDLE_BREATH, RUN, CAST_CHARGE, MELEE_SWIPE, CHEST_STRIKE, GROUND_SLAM,
        SPELL_RIFT_ARROWS, SPELL_VOID_BLAST, HURT, DYING
    }
    public GuardianActorFrame {
        if (action == null || cast < 0L || durationMillis < 1 || durationMillis > 10_000
                || elapsedMillis < 0 || elapsedMillis > durationMillis)
            throw new IllegalArgumentException("Invalid Guardian actor clock");
    }
    public String encode() { return "GACT1|"+action+"|"+cast+"|"+elapsedMillis+"|"+durationMillis; }
    public static GuardianActorFrame parse(String value) {
        if (value == null || value.length() > 160) throw new IllegalArgumentException("Invalid Guardian actor cue");
        String[] fields = value.split("\\|", -1);
        if (fields.length != 5 || !fields[0].equals("GACT1")) throw new IllegalArgumentException("Invalid actor cue");
        for (int i = 2; i < 5; i++) if (!fields[i].matches("0|[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Invalid actor clock");
        return new GuardianActorFrame(Action.valueOf(fields[1]), Long.parseLong(fields[2]),
                Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
    }
    public boolean matchesVisual(String visual) {
        return "END_RIFT_ECHO_REPLICA_V1".equals(visual) || "END_RIFT_ECHO_DECOY_V1".equals(visual);
    }
    public long elapsedAt(long deltaMillis) {
        return Math.min(durationMillis, elapsedMillis + Math.max(0L, Math.min(10_000L, deltaMillis)));
    }
}
