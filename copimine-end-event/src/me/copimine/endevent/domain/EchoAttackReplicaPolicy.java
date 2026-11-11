package me.copimine.endevent.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 5, Echo Type A — delayed attack replicas (master contract 15.2).
 *
 * <p>A spectral copy appears, locks ONE eligible player, repeats the attack
 * family of the originating boss action after a visible delay, deals real damage
 * only on the matching hit keyframe, warns, and detonates with a real radius.</p>
 *
 * <p>Two silent failures are decided here rather than in the adapter:</p>
 * <ul>
 *   <li><b>No invisible homing.</b> The aim locks at a telegraph tick; a player
 *       who is outside the locked radius afterwards is simply missed.</li>
 *   <li><b>No double damage.</b> Every damage source of one copy has its own
 *       ledger key, so the strike and the warned explosion each land exactly
 *       once per player, and never twice.</li>
 * </ul>
 */
public final class EchoAttackReplicaPolicy {
    /** A copy is small in number so a raid never faces a swarm. */
    public static final int MAX_REPLICAS = 3;
    /** The copy is short-lived; it is a delayed action, not a miniboss. */
    public static final int MAX_LIFETIME_TICKS = 160;
    /** No strike may land without this much visible preparation. */
    public static final int MIN_TELEGRAPH_TICKS = 16;
    /** The spectral arrival effect before the copy is readable. */
    public static final int ARRIVAL_TICKS = 6;
    /** How long the copy stays after its strike before it collapses away. */
    public static final int COLLAPSE_TICKS = 14;
    /** Explosion warning window: the player can leave the radius. */
    public static final int EXPLOSION_WARNING_TICKS = 20;
    /** Real damage radii, not cosmetic ones. */
    public static final double STRIKE_RADIUS = 3.0D;
    public static final double EXPLOSION_RADIUS = 2.6D;
    public static final double STRIKE_DAMAGE = 9.0D;
    public static final double EXPLOSION_DAMAGE = 13.0D;
    /** The copy hits softer than the real boss, so the phase stays survivable. */
    public static final double DAMAGE_MULTIPLIER = 0.6D;

    /** Only reported families may be copied; anything else is refused. */
    private static final Set<String> FAMILIES = Set.of(
            "MELEE_SWIPE", "CHEST_STRIKE", "GROUND_SLAM");

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private EchoAttackReplicaPolicy() {
    }

    // --- target locking ------------------------------------------------------

    /** The copy locks exactly one player that the server already admits. */
    public static boolean lockTarget(String playerKey, Set<String> eligible) {
        if (playerKey == null || playerKey.isBlank() || eligible == null) {
            return false;
        }
        return eligible.contains(playerKey);
    }

    /** 15.2 item 9: a dead or disconnected target cancels the copy. */
    public static boolean cancelsOnTargetLoss() {
        return true;
    }

    /** Copies opened by one boss action: at least one, at most the cap. */
    public static int replicasForAttack(int players) {
        int safe = Math.max(0, Math.min(20, players));
        if (safe == 0) {
            return 0;
        }
        return Math.max(1, Math.min(MAX_REPLICAS, safe - 1));
    }

    public static Set<String> supportedFamilies() {
        return FAMILIES;
    }

    /** The copy must reproduce the originating family, never invent one. */
    public static boolean repeatsFamily(String originating, String copied) {
        if (originating == null || copied == null) {
            return false;
        }
        String source = originating.trim().toUpperCase(Locale.ROOT);
        String echo = copied.trim().toUpperCase(Locale.ROOT);
        if (!FAMILIES.contains(source) || !FAMILIES.contains(echo)) {
            return false;
        }
        return source.equals(echo);
    }

    // --- timeline ------------------------------------------------------------

    /**
     * Absolute ticks of one copy.
     *
     * @param replicaKey        unique per copy, used for the damage ledger
     * @param explosionKey      unique per copy's explosion
     * @param arrivalTick       spectral arrival
     * @param telegraphLockTick where the aim freezes
     * @param hitTick           the copied attack's hit keyframe
     * @param collapseTick      the copy starts to disappear
     * @param expiryTick        the copy is gone
     * @param explosionWarningTick start of the visible explosion warning
     * @param explosionTick     the explosion's own hit keyframe
     */
    public record Timeline(String family, String replicaKey, String explosionKey,
                           long arrivalTick, long telegraphLockTick, long hitTick,
                           long collapseTick, long expiryTick,
                           long explosionWarningTick, long explosionTick) {
        public Timeline {
            family = family == null ? "" : family;
            arrivalTick = Math.max(0L, arrivalTick);
            telegraphLockTick = Math.max(arrivalTick, telegraphLockTick);
            hitTick = Math.max(telegraphLockTick + MIN_TELEGRAPH_TICKS, hitTick);
            collapseTick = Math.max(hitTick + 1L, collapseTick);
            explosionWarningTick = Math.max(hitTick, explosionWarningTick);
            explosionTick = Math.max(explosionWarningTick + MIN_TELEGRAPH_TICKS, explosionTick);
            expiryTick = Math.max(explosionTick, expiryTick);
        }
    }

    public static Timeline timeline(long startTick, String family) {
        if (family == null) {
            return null;
        }
        String key = family.trim().toUpperCase(Locale.ROOT);
        if (!FAMILIES.contains(key)) {
            return null;
        }
        long start = Math.max(0L, startTick);
        // Heavier families prepare longer, exactly like the real boss does.
        int telegraph = MIN_TELEGRAPH_TICKS + switch (key) {
            case "GROUND_SLAM" -> 12;
            case "CHEST_STRIKE" -> 6;
            default -> 0;
        };
        long sequence = SEQUENCE.incrementAndGet();
        String replicaKey = "echo-" + start + "-" + key + "-" + sequence;
        long arrival = start;
        long lock = arrival + telegraph;
        long hit = lock + 2L;
        long collapse = hit + COLLAPSE_TICKS;
        long warning = collapse;
        long explosion = warning + EXPLOSION_WARNING_TICKS;
        long expiry = explosion + 4L;
        // Never exceed the short lifetime: shrink the tail, not the telegraph.
        long maxExpiry = arrival + MAX_LIFETIME_TICKS;
        if (expiry > maxExpiry) {
            expiry = maxExpiry;
        }
        return new Timeline(key, replicaKey, replicaKey + "-boom",
                arrival, lock, hit, collapse, expiry, warning, explosion);
    }

    // --- damage windows ------------------------------------------------------

    /** The copied strike lands on its keyframe and for a couple of ticks. */
    public static boolean strikeWindowOpen(Timeline timeline, long tick) {
        if (timeline == null || tick < timeline.hitTick()) {
            return false;
        }
        return tick <= timeline.hitTick() + 2L;
    }

    /** The explosion opens on its own keyframe only, so it cannot re-arm. */
    public static boolean explosionWindowOpen(Timeline timeline, long tick) {
        if (timeline == null || tick < timeline.explosionTick()) {
            return false;
        }
        return tick <= timeline.explosionTick() + 2L;
    }

    /** Aim freezes at the telegraph tick: no invisible homing afterwards. */
    public static boolean aimIsLocked(Timeline timeline, long tick) {
        return timeline != null && tick >= timeline.telegraphLockTick()
                && tick <= timeline.expiryTick();
    }

    /** A strike connects only inside the locked radius. */
    public static boolean strikeConnects(Timeline timeline, double dx, double dz) {
        return withinRadius(dx, dz, STRIKE_RADIUS);
    }

    public static boolean explosionConnects(double distance) {
        if (!Double.isFinite(distance)) {
            return false;
        }
        return distance <= EXPLOSION_RADIUS;
    }

    private static boolean withinRadius(double dx, double dz, double radius) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz)) {
            return false;
        }
        double offset = Math.hypot(dx, dz);
        return offset <= radius;
    }

    // --- the once-only ledger ------------------------------------------------

    public static String scopedKey(String sourceKey, String playerKey) {
        return (sourceKey == null ? "" : sourceKey) + ":" + (playerKey == null ? "" : playerKey);
    }

    public static boolean mayDamage(String playerKey, Set<String> ledger, String sourceKey) {
        if (playerKey == null || playerKey.isBlank() || ledger == null
                || sourceKey == null || sourceKey.isBlank()) {
            return false;
        }
        return !ledger.contains(scopedKey(sourceKey, playerKey));
    }

    // --- cleanup -------------------------------------------------------------

    /** Every terminal reason must clean the copy; an unknown one is still cleaned. */
    public static boolean requiresCleanup(String reason) {
        return true;
    }

    /** 15.2 item 6: a copy never produces loot. */
    public static boolean dropsLoot() {
        return false;
    }

    /** 15.2 item 6: a copy never participates in phase progression. */
    public static boolean countsTowardPhaseProgress() {
        return false;
    }

    /** Real damage of the copied strike, scaled below the real boss's. */
    public static double strikeDamage(double bossDamage) {
        double base = Double.isFinite(bossDamage) && bossDamage > 0.0D ? bossDamage : STRIKE_DAMAGE;
        return base * DAMAGE_MULTIPLIER;
    }

    public static double explosionDamage() {
        return EXPLOSION_DAMAGE;
    }

    /** Diagnostic label; logs must never invent a different name. */
    public static String describe(Timeline timeline) {
        if (timeline == null) {
            return "BOSS_ECHO_REPLICA refused=unknown-family";
        }
        return "BOSS_ECHO_REPLICA family=" + timeline.family()
                + " key=" + timeline.replicaKey()
                + " arrival=" + timeline.arrivalTick()
                + " lock=" + timeline.telegraphLockTick()
                + " hit=" + timeline.hitTick()
                + " boom=" + timeline.explosionTick()
                + " expire=" + timeline.expiryTick()
                + " strike_radius=" + STRIKE_RADIUS
                + " boom_radius=" + EXPLOSION_RADIUS;
    }

    /** Test hook: the sequence only exists to keep keys unique. */
    static void resetSequenceForTests() {
        SEQUENCE.set(0L);
    }

    static List<String> families() {
        return List.copyOf(FAMILIES);
    }
}