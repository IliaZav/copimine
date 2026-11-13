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
}
