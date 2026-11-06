package me.copimine.endevent.domain;

import java.util.List;

/**
 * Wave 6 roster tables: elite guard counts and named mage miniboss profiles.
 *
 * <p>Both tables are evaluated from the roster frozen at admission, never from
 * the number of players still alive. That distinction matters because the user
 * requirement is that a party shrinking mid-encounter does not silently weaken
 * the current stage or re-apply a health multiplier to a body that was already
 * scaled.</p>
 */
public final class WaveSixRosterPolicy {
    /** Contractual ceiling: a single mage may never be shielded by more. */
    public static final int MAX_GUARDS_PER_MAGE = 5;

    /** Smallest supported party. */
    public static final int MIN_PARTICIPANTS = 2;

    /** Largest supported party. */
    public static final int MAX_PARTICIPANTS = 20;

    private WaveSixRosterPolicy() {
    }

    /**
     * Elite guards assigned to the current mage.
     *
     * <p>The roster basis includes the prisoner, because the prisoner is still
     * an admitted participant and the encounter difficulty is scaled on the
     * party that started, not on who is free to fight at this second.</p>
     */
    public static int guardsFor(int admittedPlayers) {
        int roster = clampRoster(admittedPlayers);
        if (roster <= 4) {
            return 1;
        }
        if (roster <= 8) {
            return 2;
        }
        if (roster <= 12) {
            return 3;
        }
        if (roster <= 16) {
            return 4;
        }
        return MAX_GUARDS_PER_MAGE;
    }

    /**
     * Mage miniboss health under the frozen roster.
     *
     * <p>Deliberately separate from {@link EliteCombatStatsPolicy} and from the
     * Wave 7 thousand-HP floor. Linear in roster so a five-mage run stays a
     * fight rather than a health-bar grind, and the values are configuration
     * candidates, not hidden per-actor multipliers.</p>
     */
    public static double mageHealth(int admittedPlayers) {
        int roster = clampRoster(admittedPlayers);
        // Delegate to the one curve that owns the envelope. A second local
        // formula used to live here and disagreed with it: it returned 320 HP
        // at the smallest party (the player named 350 as the floor) and 896 at
        // the largest (the player named 800 as the ceiling). The live spawn path
        // always called RitualCasterHealthPolicy, so the stale numbers were
        // invisible — and exactly the kind of trap that becomes a real bug the
        // moment somebody wires this method up.
        return RitualCasterHealthPolicy.maxHealthFor(0, roster);
    }

    /** Base health of one mage at the smallest supported party. */
    public static final double MAGE_BASE_HEALTH =
            RitualCasterHealthPolicy.MIN_HEALTH;

    /** Increment per additional admitted player above the minimum. */
    public static final double MAGE_HEALTH_PER_PLAYER = 0.0D;

    /**
     * The shared elite health contract, restated here as a local constant.
     *
     * <p>Deliberately duplicated rather than imported: this class is small, pure
     * and frequently compiled standalone by the Java test harness, and pulling in
     * {@code EliteCombatStatsPolicy} would drag the whole party-scaling chain into
     * every harness. The value is asserted equal to the shared policy by
     * {@code WaveSixRosterPolicyTest} and the Wave 6 behavior contract.</p>
     */
    public static final double ELITE_HEALTH_MULTIPLIER = 3.0D;

    /**
     * Guard bodies are shared elite roles scaled exactly once.
     *
     * <p>An "enhanced elite guard" must not mean a hand-picked extra multiplier
     * on top of the already tripled elite value, so this is the ordinary role
     * maximum times the shared elite contract, applied once by the caller.</p>
     */
    public static double guardHealth(double ordinaryRoleMaxHealth) {
        if (!(ordinaryRoleMaxHealth > 0.0D) || !Double.isFinite(ordinaryRoleMaxHealth)) {
            throw new IllegalArgumentException(
                    "ordinary role health must be a positive finite value: " + ordinaryRoleMaxHealth);
        }
        return ordinaryRoleMaxHealth * ELITE_HEALTH_MULTIPLIER;
    }

    /** Identity -> combat profile -> signature, the explicit audit table. */
    public static List<Binding> bindings() {
        List<Binding> table = new java.util.ArrayList<>(MageSignaturePolicy.MAGE_COUNT);
        for (MageSignaturePolicy.MageId mage : MageSignaturePolicy.MageId.values()) {
            table.add(new Binding(
                    mage,
                    MageSignaturePolicy.roleFor(mage),
                    MageSignaturePolicy.signatureFor(mage)));
        }
        return List.copyOf(table);
    }

    private static int clampRoster(int admittedPlayers) {
        if (admittedPlayers < MIN_PARTICIPANTS || admittedPlayers > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "Wave 6 admitted roster must be in " + MIN_PARTICIPANTS + "-"
                            + MAX_PARTICIPANTS + ": " + admittedPlayers);
        }
        return admittedPlayers;
    }

    public record Binding(MageSignaturePolicy.MageId mage,
                          RitualCasterTacticsPolicy.Role combatRole,
                          MageSignaturePolicy.Signature signature) {
        public Binding {
            if (mage == null || combatRole == null || signature == null) {
                throw new IllegalArgumentException("complete mage binding is required");
            }
        }
    }
}