package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phase 4 (OVERLOAD) infernal End mob window (master contract, N23-P1-09).
 *
 * <p>V5.1 requires Phase 4 to be a real infernal battlefield, not a tinted
 * floor: a <b>small, mixed, bounded</b> squad of custom-textured vanilla
 * Magma Cubes, Blazes and Wither Skeletons that supplement the Guardian and
 * then leave. The owner read the shipped phase as floor-only, so this class owns
 * the pure rules the runtime must obey:</p>
 *
 * <ul>
 *   <li>the window is exactly OVERLOAD - no infernal elite is ever alive
 *       outside it, and none survives a death, wipe, phase change or restart;</li>
 *   <li>the whole roster is bounded and party-scaled (never above
 *       {@link #ROSTER_CAP});</li>
 *   <li>the three roles have distinct, purposeful behaviour budgets: the Magma
 *       Cube is a telegraphed ground jumper, the Blaze a short ranged salvo, the
 *       Wither Skeleton a close-range stalker/cleaver - never one clone of the
 *       others and never vanilla fireball spam;</li>
 *   <li>no spawn lands in a protected safe cell and no attack is ever taken
 *       against a safe-zone-protected player, and an attack needs real
 *       line-of-sight;</li>
 *   <li>a Magma Cube cannot recursively split into an unbounded flood: a
 *       daughter is admitted only while the shared roster budget allows, and
 *       Blazes/Wither Skeletons never divide.</li>
 * </ul>
 *
 * <p>Nothing here touches Bukkit. The runtime only executes these decisions, so
 * the bounded-roster and cleanup guarantees can be proven without a server.</p>
 */
public final class InfernoMobWindowPolicy {
    /** The three authored infernal roles. The order is stable for logging. */
    public enum Role {
        /** Ground pursuer: telegraphed leap and a brief heat splash on landing. */
        MAGMA_CUBE,
        /** Airborne ranged: a short, aimed fire salvo to dislodge static camps. */
        BLAZE,
        /** Close-range stalker: quick approach and an occasional heavy cleave. */
        WITHER_SKELETON
    }

    /** The hard upper bound on the whole infernal roster, at any party size. */
    public static final int ROSTER_CAP = 9;

    /** A visible 3D breach tell before the first body lands. Never a head-drop. */
    public static final int SPAWN_BREACH_TICKS = 30;
    /** A refused placement retries no sooner than this; it never spam-stacks. */
    public static final int SPAWN_RETRY_TICKS = 60;
    /** A body changes its target at most this many times before it resets. */
    public static final int MAX_TARGET_CHANGES = 3;

    /** Magma Cube leap telegraph; the underside brightens and the body squashes. */
    public static final int MAGMA_JUMP_TELEGRAPH_TICKS = 12;
    /** The heat splash radius and damage of one accepted landing. */
    public static final double MAGMA_LANDING_RADIUS = 2.4D;
    public static final double MAGMA_LANDING_DAMAGE = 3.0D;

    /** Blaze fires a bounded salvo, not a continuous stream. */
    public static final int BLAZE_SALVO_MIN = 2;
    public static final int BLAZE_SALVO_MAX = 3;
    /** Ticks between two salvo shots, so the player can move between them. */
    public static final int BLAZE_SALVO_GAP_TICKS = 14;

    /** The Wither Skeleton's heavy cleave cadence; the rest of the time it stalks. */
    public static final int WITHER_CLEAVE_PERIOD_TICKS = 40;

    private InfernoMobWindowPolicy() {
    }

    /** The infernal window is exactly the OVERLOAD health phase. */
    public static boolean windowActive(BossPhase phase) {
        return phase == BossPhase.OVERLOAD;
    }

    /** The bounded roster cap for the given living party size. */
    public static int capFor(int livingPlayers) {
        int players = Math.max(1, livingPlayers);
        if (players <= 2) {
            return 2;
        }
        if (players <= 5) {
            return 4;
        }
        if (players <= 10) {
            return 6;
        }
        if (players <= 11) {
            return 7;
        }
        return ROSTER_CAP;
    }

    /**
     * The authored mixed squad for the given party size.
     *
     * <p>Ground (Magma Cube) leads, ranged (Blaze) backs it, and a stalker
     * (Wither Skeleton) appears from five players on. The total is exactly the
     * cap for the size, so the composition and the bound can never disagree.</p>
     */
    public static List<Role> rosterFor(int livingPlayers) {
        int players = Math.max(1, livingPlayers);
        int magma;
        int blaze;
        int wither;
        if (players <= 2) {
            magma = 1;
            blaze = 1;
            wither = 0;
        } else if (players <= 5) {
            magma = 2;
            blaze = 1;
            wither = 1;
        } else if (players <= 10) {
            magma = 3;
            blaze = 2;
            wither = 1;
        } else if (players <= 11) {
            magma = 3;
            blaze = 2;
            wither = 2;
        } else {
            magma = 4;
            blaze = 3;
            wither = 2;
        }
        List<Role> roster = new ArrayList<>(magma + blaze + wither);
        add(roster, Role.MAGMA_CUBE, magma);
        add(roster, Role.BLAZE, blaze);
        add(roster, Role.WITHER_SKELETON, wither);
        int cap = capFor(players);
        if (roster.size() > cap) {
            roster = new ArrayList<>(roster.subList(0, cap));
        }
        return Collections.unmodifiableList(roster);
    }

    private static void add(List<Role> roster, Role role, int count) {
        for (int i = 0; i < count; i++) {
            roster.add(role);
        }
    }

    /** The intended number of this role in the party's squad (0 when absent). */
    public static int roleBudget(int livingPlayers, Role role) {
        if (role == null) {
            return 0;
        }
        int count = 0;
        for (Role member : rosterFor(livingPlayers)) {
            if (member == role) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether one more body of this role may enter now.
     *
     * <p>Fail-closed: a null role, a non-Overload phase, a full shared roster, a
     * role that is not part of this party's squad, a protected landing cell or an
     * unopened breach all refuse. A refused placement must back off for
     * {@link #SPAWN_RETRY_TICKS} rather than retrying every tick, and the caller
     * must not exceed {@link #roleBudget} for that role.</p>
     */
    public static boolean maySpawn(BossPhase phase, Role role, int livingPlayers, int aliveOwned,
                                   boolean landingProtected, boolean breachOpen) {
        if (role == null || !windowActive(phase)) {
            return false;
        }
        int cap = capFor(livingPlayers);
        if (aliveOwned < 0 || aliveOwned >= cap) {
            return false;
        }
        if (!breachOpen || landingProtected) {
            return false;
        }
        return rosterFor(livingPlayers).contains(role);
    }

    /**
     * Whether an attack may be resolved. An infernal elite must never attack a
     * safe-zone-protected player, never attack outside the window, and must have
     * real line-of-sight; otherwise the attack is simply not taken.
     */
    public static boolean attackAllowed(BossPhase phase, boolean targetProtected, boolean lineOfSight) {
        return windowActive(phase) && !targetProtected && lineOfSight;
    }

    /**
     * Whether a Magma Cube daughter may be released on a landing.
     *
     * <p>Vanilla splitting is intentionally suppressed: only a Magma Cube may
     * divide, only while the shared roster has room, and never outside the
     * window. Blazes and Wither Skeletons never divide.</p>
     */
    public static boolean daughterAllowed(BossPhase phase, Role role, int livingPlayers, int aliveOwned) {
        return role == Role.MAGMA_CUBE && windowActive(phase) && aliveOwned >= 0
                && aliveOwned < capFor(livingPlayers);
    }

    /**
     * Whether an owned infernal body must be retired now.
     *
     * <p>The contract is categorical: no lingering mob after the phase ends,
     * the boss/session dies, the party wipes or the encounter restarts. The
     * caller also retires a body whose owner identity no longer matches.</p>
     */
    public static boolean shouldRetire(BossPhase phase, boolean ownerValid, boolean partyActive,
                                       boolean restarting) {
        return !windowActive(phase) || !ownerValid || !partyActive || restarting;
    }

    /** The authored salvo size for a Blaze, clamped to the bounded band. */
    public static int blazeSalvoSize(int requested) {
        return Math.max(BLAZE_SALVO_MIN, Math.min(BLAZE_SALVO_MAX, requested));
    }

    /** Stable, bounded label for diagnostics; logs must not invent another. */
    public static List<String> describe(int livingPlayers) {
        List<String> out = new ArrayList<>();
        out.add("cap=" + capFor(livingPlayers));
        out.add("squad=" + rosterFor(livingPlayers));
        out.add("breach_ticks=" + SPAWN_BREACH_TICKS);
        return Collections.unmodifiableList(out);
    }
}
