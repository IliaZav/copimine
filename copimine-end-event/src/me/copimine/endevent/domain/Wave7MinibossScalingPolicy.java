package me.copimine.endevent.domain;

/**
 * Party scaling for the Wave 7 minibosses.
 *
 * <p>SUPERSEDED (2026-11-03): miniboss health used a frozen-roster ladder that
 * grew by only 25 % per extra player from a 1000 HP floor. The user's verdict
 * was "ни мин то что стоит сейчас": a party of ten fought the same skeleton it
 * fought as a party of two, and at ten players a miniboss died to a few swings.
 * A frozen roster is still the right basis — a relog must never heal a living
 * actor — but the <em>ladder</em> was wrong, not the basis.</p>
 *
 * <p>The replacement scales in the open, like the main boss already does:</p>
 * <ul>
 *   <li>health grows by a fixed step per extra player, with every derived number
 *       rounded to a tenth so the client bar never shows a fractional wobble;</li>
 *   <li>health has a ceiling, because an endlessly growing sponge is not a
 *       fight either;</li>
 *   <li>damage grows more slowly than health (a square-root curve), because
 *       damage that doubles with the party turns every hit into a one-shot;</li>
 *   <li>every number is monotonic in the player count — no dip anywhere.</li>
 * </ul>
 */
public final class Wave7MinibossScalingPolicy {

    /**
     * Smallest supported party, and the size the authored base is written for.
     *
     * <p>The frozen roster never reports fewer than two, so growth starts at
     * the third player; the two-player encounter is exactly the authored body,
     * which keeps the contract floor of 1000 HP exact rather than rounded.</p>
     */
    public static final int MIN_PLAYERS = 2;
    /** Largest supported party; also the bound of the scaling basis. */
    public static final int MAX_PLAYERS = 20;
    /** Nobody in the encounter can be squishier than this, whatever the trial. */
    public static final double HEALTH_FLOOR = 1_000.0D;
    /** Ceiling for a miniboss body; the main boss is still the biggest number. */
    public static final double HEALTH_CAP = 6_000.0D;
    /**
     * Extra health per player above one.
     *
     * <p>Sized so the ceiling is reached by a full twenty-player party and not
     * before: the first draft used 420 and the curve flattened at fourteen,
     * which is the same "it stopped mattering" defect this policy exists to
     * remove. The test pins monotonic growth across the whole supported band.</p>
     */
    public static final double HEALTH_PER_PLAYER = 260.0D;
    /** Extra damage, as a fraction of the solo value, per step of the sqrt curve. */
    public static final double DAMAGE_GROWTH = 0.22D;
    /** Damage never exceeds this multiple of the solo value. */
    public static final double DAMAGE_CAP_MULTIPLIER = 1.90D;

    private Wave7MinibossScalingPolicy() {
    }

    /**
     * Body health for one trial at {@code players} participants.
     *
     * @param baseHealth authored solo health, already at or above
     *                   {@link #HEALTH_FLOOR}
     */
    public static double health(double baseHealth, int players) {
        int count = clampPlayers(players);
        double base = Math.max(HEALTH_FLOOR, Double.isFinite(baseHealth) ? baseHealth : HEALTH_FLOOR);
        if (count <= MIN_PLAYERS) return round1(Math.max(HEALTH_FLOOR, base));
        double grown = base + HEALTH_PER_PLAYER * (count - MIN_PLAYERS);
        return round1(Math.min(HEALTH_CAP, Math.max(HEALTH_FLOOR, grown)));
    }

    /**
     * Multiplier applied to the authored contact and spell damage.
     *
     * <p>Grows as {@code 1 + 0.22 * sqrt(extra)}, so four players see about
     * 1.38x and twenty see the 1.90x cap. Health outpaces damage on purpose: a
     * big party should make the fight longer, not lethal.</p>
     */
    public static double damageMultiplier(int players) {
        int extra = clampPlayers(players) - MIN_PLAYERS;
        double grown = 1.0D + DAMAGE_GROWTH * Math.sqrt(extra);
        return round2(Math.min(DAMAGE_CAP_MULTIPLIER, grown));
    }

    /**
     * Clamps a live health value into the policy band.
     *
     * <p>Called on every re-application so a scribbled or truncated save can
     * never present an impossible health or silently resurrect a dead actor.</p>
     */
    public static double clampHealth(double value, double maxHealth) {
        if (!Double.isFinite(maxHealth) || maxHealth <= 0.0D) return HEALTH_FLOOR;
        if (!Double.isFinite(value) || value <= 0.0D) return 0.0D;
        return Math.min(maxHealth, value);
    }

    /** Human-readable line for the operator diagnostic. */
    public static String describe(double baseHealth, int players) {
        return "hp=" + health(baseHealth, players)
                + " dmg=x" + damageMultiplier(players)
                + " players=" + clampPlayers(players);
    }

    private static int clampPlayers(int players) {
        if (players < MIN_PLAYERS) return MIN_PLAYERS;
        return Math.min(MAX_PLAYERS, players);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
