package me.copimine.endevent.domain;

import java.util.List;

/**
 * Master contract 22.1: after the last cocoon defender dies the Guardian falls
 * exactly once, lands, and stays in melee fury for the rest of the encounter.
 *
 * <p>The transition is an authored sequence, not a teleport: the boss stops
 * hovering, its protective energy collapses, it descends visibly, and the
 * landing animation plays at the moment its feet reach the floor. Neither the
 * fall nor the landing may replay after a restart or a repeated check.</p>
 */
public final class CocoonFallPolicy {
    /** Authored stage order; index order is the contract order. */
    public enum Stage {
        /** Boss still suspended over the Core inside the cocoon. */
        COCOONED,
        /** Shield anchors break, hover released, energy collapses. */
        ANCHORS_BROKEN,
        /** Visible descent toward the floor. */
        FALLING,
        /** Landing animation plays with dust/shockwave/sound. */
        LANDING,
        /** Permanent melee fury. */
        MELEE_FURY
    }

    /** Descent is a real animation window, never an instant drop. */
    public static final int FALL_TICKS = 40;
    /** Landing impact window, dust and shockwave synchronise with it. */
    public static final int LANDING_TICKS = 20;

    private CocoonFallPolicy() {
    }

    /**
     * Stage after the last defender dies.
     *
     * @param alreadyFallenThisGeneration true when the fall receipt exists
     */
    public static Stage stageAfterLastDefender(boolean alreadyFallenThisGeneration,
                                               boolean hasLanded) {
        if (alreadyFallenThisGeneration) {
            return hasLanded ? Stage.MELEE_FURY : Stage.FALLING;
        }
        return Stage.ANCHORS_BROKEN;
    }

    /** The fall may start exactly once per encounter generation. */
    public static boolean mayStartFall(boolean fallStartedThisGeneration) {
        return !fallStartedThisGeneration;
    }

    /** A landed boss never starts a second fall. */
    public static boolean mayPlayLanding(boolean landingPlayedThisGeneration) {
        return !landingPlayedThisGeneration;
    }

    /** Furious state is permanent for the generation once entered. */
    public static boolean isMeleeFury(Stage stage) {
        return stage == Stage.MELEE_FURY;
    }

    /** Contract 22.2: the 3x multiplier only exists in this exact state. */
    public static double acceptedDamageMultiplier(Stage stage) {
        return stage == Stage.MELEE_FURY ? 3.0D : 1.0D;
    }

    public static boolean allowsMelee(Stage stage) {
        return stage == Stage.MELEE_FURY;
    }

    public static boolean allowsCocoonChannel(Stage stage) {
        return stage == Stage.COCOONED;
    }

    /** After the fall, the fight is close combat: no long-range spell loop. */
    public static boolean allowsRangedSpellcasting(Stage stage) {
        return stage == Stage.COCOONED;
    }

    /** Progress of the visible descent, 0 at the top and 1 at floor level. */
    public static double fallProgress(int elapsedTicks) {
        if (elapsedTicks <= 0) {
            return 0.0D;
        }
        return Math.min(1.0D, elapsedTicks / (double) FALL_TICKS);
    }

    /** Interpolated height above the floor during the descent. */
    public static double heightDuringFall(double hoverHeight, int elapsedTicks) {
        double safeHeight = Double.isFinite(hoverHeight) ? Math.max(0.0D, hoverHeight) : 0.0D;
        return safeHeight * (1.0D - fallProgress(elapsedTicks));
    }

    /** Landing starts on the tick the feet reach the floor, not before. */
    public static boolean landingDue(int elapsedTicks) {
        return elapsedTicks >= FALL_TICKS;
    }

    /** Complete transition for one tick; the adapter only renders it. */
    public static Stage advance(Stage current, int elapsedTicks) {
        Stage safe = current == null ? Stage.COCOONED : current;
        return switch (safe) {
            case COCOONED, ANCHORS_BROKEN -> Stage.FALLING;
            case FALLING -> landingDue(elapsedTicks) ? Stage.LANDING : Stage.FALLING;
            case LANDING -> elapsedTicks >= LANDING_TICKS ? Stage.MELEE_FURY : Stage.LANDING;
            case MELEE_FURY -> Stage.MELEE_FURY;
        };
    }

    /** Allowed abilities in melee fury: close combat only. */
    public static List<EndRiftAiPolicy.BossSpell> furySpellProfile() {
        return List.of(
                EndRiftAiPolicy.BossSpell.MELEE_SWIPE,
                EndRiftAiPolicy.BossSpell.CHEST_STRIKE,
                EndRiftAiPolicy.BossSpell.GROUND_SLAM,
                EndRiftAiPolicy.BossSpell.FINAL_STRIKE);
    }

    /** The fury profile must contain no long-range channel spell. */
    public static boolean furyProfileIsMeleeOnly() {
        for (EndRiftAiPolicy.BossSpell spell : furySpellProfile()) {
            if (spell == EndRiftAiPolicy.BossSpell.SMALL_KAGUNE
                    || spell == EndRiftAiPolicy.BossSpell.AMBUSH_GRAB
                    || spell == EndRiftAiPolicy.BossSpell.VOID_BLAST
                    || spell == EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE
                    || spell == EndRiftAiPolicy.BossSpell.RIFT_ARROWS
                    || spell == EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS
                    || spell == EndRiftAiPolicy.BossSpell.ARENA_INFERNO
                    || spell == EndRiftAiPolicy.BossSpell.BOSS_FIREBALL) {
                return false;
            }
        }
        return true;
    }
}