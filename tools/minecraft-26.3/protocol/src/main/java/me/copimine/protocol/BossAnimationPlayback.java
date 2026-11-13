package me.copimine.protocol;

/** Shared authored-to-combat time mapping. Geometry and source clips stay intact. */
public final class BossAnimationPlayback {
    private BossAnimationPlayback() { }

    public static double rate(String animation) {
        if (animation == null) return 1.0D;
        return switch (animation) {
            // Shared by contact admission, animated hitboxes and client poses.
            // Do not speed up only the renderer: that produces phantom hits.
            case "MELEE_SWIPE", "GROUND_SLAM" -> 2.0D;
            case "CHEST_STRIKE" -> 1.75D;
            default -> 1.0D;
        };
    }

    /** Round contact forward so damage cannot precede its authored pose. */
    public static int combatTicks(String animation, int authoredTicks) {
        return Math.max(0, (int) Math.ceil(Math.max(0, authoredTicks) / rate(animation)));
    }

    public static double authoredTicks(String animation, double combatTicks) {
        if (!Double.isFinite(combatTicks)) return 0.0D;
        return Math.max(0.0D, combatTicks) * rate(animation);
    }

    /** For these stage-owned cues, duration_ms is the remaining clip duration. */
    public static long cocoonClipMillis(String animation) {
        if (animation == null) return 0L;
        return switch (animation) {
            case "COCOON_FALL" -> 2_000L;
            case "COCOON_LAND" -> 1_000L;
            default -> 0L;
        };
    }

    /** -1 means an invalid or unrelated cue; callers must keep their old state. */
    public static long cocoonElapsedMillis(String animation, long remainingMillis) {
        long full = cocoonClipMillis(animation);
        return full == 0L || remainingMillis < 0L || remainingMillis > full
                ? -1L : full - remainingMillis;
    }

    /** Old clients reject unknown V4 tokens; keep their stream alive during an upgrade. */
    public static String compatibleCue(String animation, String verifiedClientVersion) {
        if ("0.1.4+26.3".equals(verifiedClientVersion)) return animation;
        if ("COCOON_FALL".equals(animation)) return "CAST_CHARGE";
        if ("COCOON_LAND".equals(animation)) return "GROUND_SLAM";
        return animation;
    }
}
