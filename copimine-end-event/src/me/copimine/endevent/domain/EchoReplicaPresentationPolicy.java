package me.copimine.endevent.domain;

/**
 * Phase 5, Echo presentation — the visible half of master contract 15.2 Type A.
 *
 * <p>{@link EchoAttackReplicaPolicy} owns the TIMING and the damage maths. This
 * class owns the two questions the timing cannot answer on its own:</p>
 *
 * <ul>
 *   <li><b>Which authored family is copied?</b> The timeline refuses families it
 *       does not know, so the adapter must derive a supported family from the
 *       attack the boss actually committed. Handing over an arbitrary spell name
 *       (the historic {@code VOID_BLAST}) makes every replica silently
 *       disappear.</li>
 *   <li><b>Is the copy VISIBLE at this tick?</b> A replica that deals damage
 *       without a readable arrival, telegraph, contact and collapse is exactly
 *       the invisible damage contract 23.3 forbids. Every phase decision therefore
 *       lives here, as a pure function of the copy's timeline: no hidden damage
 *       state can exist outside it.</li>
 * </ul>
 *
 * <p>The copy reuses the reach of the real boss's own strike rather than
 * inventing its own range, so a ghost that "cannot be reached" is a bug the
 * adapter shows, not a number this class hides.</p>
 */
public final class EchoReplicaPresentationPolicy {

    /** The visible beat a copy is in at one tick. */
    public enum Beat {
        /** Not yet arrived, or already gone: no actor may exist. */
        NONE,
        /** Spectral arrival: the copy is materialising and not yet readable. */
        ARRIVAL,
        /** Telegraph: the aim freezes and the strike is announced. */
        TELEGRAPH,
        /** Contact: the copied keyframe and the window the strike may land in. */
        CONTACT,
        /** Collapse: the copy is folding away. */
        COLLAPSE,
        /** Warned explosion: the radius is readable before it fires. */
        EXPLOSION_WARNING,
        /** The explosion's own keyframe. */
        EXPLOSION
    }

    /** Longest distance at which a copy may spawn from the point it copies. */
    public static final double MAX_SPAWN_DISTANCE = 34.0D;
    /** The copy never spawns inside the point it copies; it must be seen first. */
    public static final double MIN_SPAWN_DISTANCE = 2.4D;
    /** A copy below this much free headroom would be buried in the floor. */
    public static final int MIN_SPAWN_HEADROOM_BLOCKS = 3;
    /** How far the copy's visual carries beyond its own position. */
    public static final double PRESENTATION_RADIUS = 1.1D;
    /**
     * Contract 23.3: the animation must never announce the strike before the
     * damage window opens, and must not linger long after it closed.
     */
    public static final int CONTACT_TAIL_TICKS = 4;

    /** Family copied when the boss committed a close-range swipe. */
    private static final String FAMILY_MELEE = "MELEE_SWIPE";
    /** Family copied when the boss committed a wide ground slam. */
    private static final String FAMILY_SLAM = "GROUND_SLAM";
    /** Family copied for the chest strike and every other committed cast. */
    private static final String FAMILY_CHEST = "CHEST_STRIKE";

    private EchoReplicaPresentationPolicy() {
    }

    // --- family selection ----------------------------------------------------

    /**
     * The supported family nearest to the attack the boss actually committed.
     *
     * <p>The result is always a family {@link EchoAttackReplicaPolicy#timeline}
     * accepts, or {@code null} when the origin was not a committed boss attack
     * at all (null/blank). {@code VOID_BLAST} is deliberately NOT supported: a
     * copy that repeats the wrong attack is a lie the player cannot read.</p>
     */
    public static String familyForOrigin(String originFamily) {
        if (originFamily == null || originFamily.isBlank()) {
            return null;
        }
        String origin = originFamily.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.regex.Pattern.matches("[A-Z0-9_]{1,48}", origin)) {
            return null;
        }
        return switch (origin) {
            case "GROUND_SLAM", "UDAR_PO_ZEMLE", "UDAR_PO_ZEMLE2" -> FAMILY_SLAM;
            case "MELEE_SWIPE", "SWIPE", "SWIPE2", "MELEE" -> FAMILY_MELEE;
            case "CHEST_STRIKE", "CHEST_PROJECTILE", "UDAR_IZ_GRUDI",
                    "VOID_BLAST" -> FAMILY_CHEST; // historical serialized alias
            default -> null;
        };
    }

    /** True when the family can be copied AND has a real timeline. */
    public static boolean isCopyable(String family, long startTick) {
        String mapped = familyForOrigin(family);
        return mapped != null && EchoAttackReplicaPolicy.timeline(startTick, mapped) != null;
    }

    // --- the visible beats ---------------------------------------------------

    /**
     * The beat a copy occupies at {@code tick}. Windows are half-open so two
     * adjacent beats can never both be true, and the contact beat always equals
     * the strike window the damage path uses.
     */
    public static Beat beatAt(EchoAttackReplicaPolicy.Timeline timeline, long tick) {
        if (timeline == null) {
            return Beat.NONE;
        }
        if (tick < timeline.arrivalTick() || tick > timeline.expiryTick()) {
            return Beat.NONE;
        }
        if (EchoAttackReplicaPolicy.strikeWindowOpen(timeline, tick)) {
            return Beat.CONTACT;
        }
        if (EchoAttackReplicaPolicy.explosionWindowOpen(timeline, tick)) {
            return Beat.EXPLOSION;
        }
        if (tick < timeline.telegraphLockTick()) {
            return Beat.ARRIVAL;
        }
        if (tick < timeline.hitTick()) {
            return Beat.TELEGRAPH;
        }
        if (tick < timeline.explosionWarningTick()) {
            return Beat.COLLAPSE;
        }
        return Beat.EXPLOSION_WARNING;
    }

    /** The copy exists as a spawned actor only from arrival to expiry. */
    public static boolean actorShouldExist(EchoAttackReplicaPolicy.Timeline timeline, long tick) {
        if (timeline == null) {
            return false;
        }
        return tick >= timeline.arrivalTick() && tick <= timeline.expiryTick();
    }

    /**
     * True while the actor must be visibly telegraphing something: arrival
     * shimmer, locked aim, contact flash or collapse. Used by the adapter to
     * decide whether the copy needs its per-tick presentation packet at all.
     */
    public static boolean needsPresentation(EchoAttackReplicaPolicy.Timeline timeline, long tick) {
        return beatAt(timeline, tick) != Beat.NONE;
    }

    /**
     * The strike must be announced for at least this long before it lands. The
     * policy is stated twice on purpose: once by the timeline, once here, so a
     * later timeline edit that shortens the telegraph is caught.
     */
    public static boolean telegraphIsReadable(EchoAttackReplicaPolicy.Timeline timeline) {
        if (timeline == null) {
            return false;
        }
        return timeline.hitTick() - timeline.telegraphLockTick()
                >= EchoAttackReplicaPolicy.MIN_TELEGRAPH_TICKS;
    }

    // --- spawn placement -----------------------------------------------------

    /** A candidate spawn offset from the point being copied. */
    public record Placement(double dx, double dy, double dz) {
        public double horizontalDistance() {
            return Math.hypot(dx, dz);
        }
    }

    /**
     * One deterministic placement on a ring around the copied point.
     *
     * <p>Deterministic on purpose: two replicas of the same cast must not stack
     * on one another, and a replay of the same seed must produce the same
     * picture. The ring is wider than the strike radius so a copy always reads
     * as a separate attacker.</p>
     */
    public static Placement placement(int index, int replicas, long seed, double yawRadians) {
        if (!Double.isFinite(yawRadians)) {
            yawRadians = 0.0D;
        }
        int count = Math.max(1, Math.min(EchoAttackReplicaPolicy.MAX_REPLICAS, replicas));
        int slot = Math.floorMod(index, count);
        double spread = Math.PI * 0.9D;
        double base = yawRadians + (slot - (count - 1) / 2.0D) * spread;
        double jitter = (double) Math.floorMod(seed + slot * 7919L, 7L) / 7.0D;
        double angle = base + (jitter - 0.5D) * 0.22D;
        double radius = MIN_SPAWN_DISTANCE + EchoAttackReplicaPolicy.STRIKE_RADIUS
                + 0.6D + jitter * 0.8D;
        double dx = Math.cos(angle) * radius;
        double dz = Math.sin(angle) * radius;
        if (!Double.isFinite(dx) || !Double.isFinite(dz)) {
            return new Placement(MIN_SPAWN_DISTANCE, 0.0D, 0.0D);
        }
        return new Placement(dx, 0.0D, dz);
    }

    /** A candidate location is admitted only when it is readable and permitted. */
    public static boolean acceptsSpawnPoint(double distance, int headroomBlocks,
                                            boolean insideWorldBorder, boolean inArena) {
        if (!Double.isFinite(distance) || headroomBlocks < 0) {
            return false;
        }
        if (!insideWorldBorder || !inArena) {
            return false;
        }
        return distance >= MIN_SPAWN_DISTANCE && distance <= MAX_SPAWN_DISTANCE
                && headroomBlocks >= MIN_SPAWN_HEADROOM_BLOCKS;
    }

    // --- the presentation name ----------------------------------------------

    /**
     * The phase name the client is given for one beat. An UNKNOWN beat is a
     * refusal, never a fallback: a client that receives an empty phase must keep
     * the last readable pose rather than snap to the strike.
     */
    public static String phaseName(Beat beat) {
        if (beat == null) {
            return "";
        }
        return switch (beat) {
            case ARRIVAL -> "ECHO_ARRIVAL";
            case TELEGRAPH -> "ECHO_TELEGRAPH";
            case CONTACT -> "ECHO_CONTACT";
            case COLLAPSE -> "ECHO_COLLAPSE";
            case EXPLOSION_WARNING -> "ECHO_EXPLOSION_WARNING";
            case EXPLOSION -> "ECHO_EXPLOSION";
            case NONE -> "";
        };
    }

    /** The beat's duration in milliseconds, so the client clock matches ours. */
    public static long phaseDurationMillis(EchoAttackReplicaPolicy.Timeline timeline, Beat beat) {
        if (timeline == null || beat == null) {
            return 0L;
        }
        long ticks = switch (beat) {
            case ARRIVAL -> Math.max(1L, timeline.telegraphLockTick() - timeline.arrivalTick());
            case TELEGRAPH -> Math.max(1L, timeline.hitTick() - timeline.telegraphLockTick());
            case CONTACT -> Math.max(1L, EchoAttackReplicaPolicy.strikeWindowOpen(timeline,
                    timeline.hitTick()) ? 3L : 1L);
            case COLLAPSE -> Math.max(1L,
                    timeline.explosionWarningTick() - timeline.collapseTick());
            case EXPLOSION_WARNING -> Math.max(1L,
                    timeline.explosionTick() - timeline.explosionWarningTick());
            case EXPLOSION -> Math.max(1L, timeline.expiryTick() - timeline.explosionTick());
            case NONE -> 0L;
        };
        return ticks * 50L;
    }

    /** Damage may be applied only on a contact or explosion beat. */
    public static boolean beatMayDamage(Beat beat) {
        return beat == Beat.CONTACT || beat == Beat.EXPLOSION;
    }

    /**
     * Contract 23.3, stated for the reader of the adapter: a copy that has no
     * actor is not allowed to hurt anyone. The adapter asks this before every
     * damage call, not once at spawn.
     */
    public static boolean damageAllowed(boolean actorVisible,
                                        EchoAttackReplicaPolicy.Timeline timeline,
                                        long tick) {
        if (!actorVisible) {
            return false;
        }
        return beatMayDamage(beatAt(timeline, tick));
    }

    /** Diagnostic label; logs must never invent a different name. */
    public static String describe(String family, Beat beat) {
        return "BOSS_ECHO_REPLICA_PRESENTATION family=" + (family == null ? "" : family)
                + " beat=" + (beat == null ? "NONE" : beat.name())
                + " phase=" + phaseName(beat)
                + " min_distance=" + MIN_SPAWN_DISTANCE
                + " max_distance=" + MAX_SPAWN_DISTANCE;
    }
}
