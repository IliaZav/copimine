package me.copimine.protocol;

/** Bounded presentation cue inside the existing signed entity-phase envelope. */
public record Wave7AnimationFrame(Action action, Stage stage, long cast, int elapsedMillis, int durationMillis) {
    public enum Action {
        WARDEN_NARROW_STRIKE, WARDEN_WIDE_STRIKE, WARDEN_SONIC_LUNGE, WARDEN_SLAM,
        MARKSMAN_QUICK_SHOT, MARKSMAN_HEAVY_SHOT, MARKSMAN_SPREAD_SHOT,
        MARKSMAN_SUPPORT, MARKSMAN_PAIRED_CROSSFIRE, MARKSMAN_DEFENDER,
        ARCHMAGE_FANGS, ARCHMAGE_FANGS_GROUP, ARCHMAGE_SPEAR, ARCHMAGE_STEP, ARCHMAGE_BEAM,
        ARCHMAGE_CIRCLE, ARCHMAGE_CROWN, ARCHMAGE_REPULSE, WARDEN_SCULK_TRACE
    }
    public enum Stage { EMERGE, WINDUP, RELEASE, CHANNEL, RECOVERY, HURT, DYING }
    public static final int MAX_CHANNEL_MILLIS = 30_000;
    public Wave7AnimationFrame {
        if (action == null || stage == null || cast < 0 || durationMillis < 1 || durationMillis > (stage == Stage.CHANNEL ? MAX_CHANNEL_MILLIS : 10_000)
                || elapsedMillis < 0 || elapsedMillis > durationMillis)
            throw new IllegalArgumentException("Invalid trial animation frame");
    }
    public String encode() {
        return "TRIAL1|" + action + "|" + stage + "|" + cast + "|" + elapsedMillis + "|" + durationMillis;
    }
    public static Wave7AnimationFrame parse(String value) {
        if (value == null || value.length() > 160) throw new IllegalArgumentException("Invalid trial cue");
        String[] fields = value.split("\\|", -1);
        if (fields.length != 6 || !fields[0].equals("TRIAL1")) throw new IllegalArgumentException("Invalid trial cue");
        for (int i=3; i<6; i++) if (!fields[i].matches("0|[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Invalid trial clock");
        return new Wave7AnimationFrame(Action.valueOf(fields[1]), Stage.valueOf(fields[2]),
                Long.parseLong(fields[3]), Integer.parseInt(fields[4]), Integer.parseInt(fields[5]));
    }
    public float progress() { return elapsedMillis / (float) durationMillis; }
    public Wave7AnimationFrame advance(long deltaMillis) {
        return new Wave7AnimationFrame(action, stage, cast,
                (int)Math.min(durationMillis, elapsedMillis + Math.max(0L, Math.min(durationMillis, deltaMillis))), durationMillis);
    }
    public boolean matchesVisual(String visual) {
        return visual != null && (action.name().startsWith("WARDEN_") && visual.equals("END_RIFT_WAVE7_WARDEN_V1")
                || action.name().startsWith("ARCHMAGE_") && visual.equals("END_RIFT_WAVE7_ARCHMAGE_V1")
                || action.name().startsWith("MARKSMAN_") && visual.equals("END_RIFT_WAVE7_MARKSMAN_V1"));
    }
}
