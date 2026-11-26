package me.copimine.endevent.domain;

import java.util.UUID;

/**
 * §10.4.3 &amp; §10.4.5: rare Personal Echo summon during the Guardian's RAGE phase.
 *
 * <p>A single, intelligent copy of one living participant that appears with the
 * owner's skin, equipment, and combat profile — no boss bar, no loot, no Wave 7
 * duel progression. The invitation interval, lifetime cap and damage scalars are
 * drawn from the provisional table in §10.4.3; these are <em>proposed, not
 * approved balance</em>. The holder is obliged to respect rarity, an independent
 * AI, and an absence of rewards and bar.</p>
 *
 * <p>Incoming damage mode defaults to {@link IncomingDamageMode#OWNER_ONLY} per
 * the owner's instruction to "keep current rules until approved" (§10.4.3).</p>
 */
public final class RageEchoSummonPolicy {
    /** At most one rare Personal Echo alive at any time. */
    public static final int MAX_SIMULTANEOUS = 1;

    /** Rare repeat: 90–120 active seconds; the mid point for the default. */
    public static final long MIN_COOLDOWN_TICKS = 90L * 20L;
    public static final long MAX_COOLDOWN_TICKS = 120L * 20L;
    public static final long COOLDOWN_TICKS = 105L * 20L;

    /** Lifetime: 45–60 s until auto-dismiss. */
    public static final long MIN_LIFETIME_TICKS = 45L * 20L;
    public static final long MAX_LIFETIME_TICKS = 60L * 20L;
    /** Default lifetime in ticks (52.5 s, midpoint). */
    public static final long LIFETIME_TICKS = MIN_LIFETIME_TICKS + (MAX_LIFETIME_TICKS - MIN_LIFETIME_TICKS) / 2L;

    /** Health multiplier ×1.75 owner max (within §10.4.3 1.5–2.0 band). */
    public static final double HEALTH_MULTIPLIER = 1.75D;
    /** Damage multiplier ×0.9 (within §10.4.3 0.8–1.0 band). */
    public static final double DAMAGE_MULTIPLIER = 0.9D;

    /** Fallback when the owner has no recorded combat damage. */
    public static final double FALLBACK_ATTACK_DAMAGE = 8.0D;

    /** The same visuals family as the owner — never the boss identity. */
    public static final boolean USES_BOSS_MODEL = false;
    /** No boss bar joined. */
    public static final boolean JOINS_BOSS_BAR = false;
    /** No loot drops. */
    public static final boolean DROPS_LOOT = false;

    /**
     * Who may damage the rare Echo. §10.4.3 mode A/B/C — B is not approved
     * and the owner has not yet decided. Keep the default at OWNER_ONLY
     * (mode A) until the answer arrives.
     */
    public enum IncomingDamageMode {
        /** Only the owner may damage their Echo. */
        OWNER_ONLY,
        /** The owner damages it; allies may help kill. */
        ALLIED_HELP,
        /** Free-for-all switching. */
        FREE_FOR_ALL
    }

    private RageEchoSummonPolicy() {
    }

    // --- admission -----------------------------------------------------------

    /**
     * True when a rare Echo may be summoned.
     *
     * @param lastSummonTick previous summon tick, or {@code -1} for none
     * @param nowTick        current event tick
     * @param alive          Echoes currently alive
     * @param phaseActive    the RAGE phase is still running
     * @param ownerOnline    the owner is online, alive and in the event world
     */
    public static boolean maySummon(long lastSummonTick, long nowTick,
                                    int alive, boolean phaseActive, boolean ownerOnline) {
        if (!phaseActive || !ownerOnline || alive < 0) return false;
        return alive < MAX_SIMULTANEOUS
                && (lastSummonTick < 0L || nowTick - lastSummonTick >= COOLDOWN_TICKS);
    }

    /** How many more may be summoned now (always 0 or 1). */
    public static int maySummonTo(int alive) {
        int living = Math.max(0, alive);
        return Math.max(0, MAX_SIMULTANEOUS - living);
    }

    // --- health / damage -----------------------------------------------------

    /** The rare Echo's starting health = owner max × HEALTH_MULTIPLIER, clamped. */
    public static double summonHealth(double ownerMaxHealth) {
        double base = Double.isFinite(ownerMaxHealth) && ownerMaxHealth > 0.0D
                ? ownerMaxHealth : 20.0D;
        return Math.max(1.0D, base * HEALTH_MULTIPLIER);
    }

    /** The rare Echo's attack damage = profile attack × DAMAGE_MULTIPLIER. */
    public static double summonDamage(double profileDamage) {
        double base = Double.isFinite(profileDamage) && profileDamage > 0.0D
                ? profileDamage : FALLBACK_ATTACK_DAMAGE;
        return base * DAMAGE_MULTIPLIER;
    }

    // --- lifetime ------------------------------------------------------------

    /** True when the Echo has exceeded its lifetime and should auto-dismiss. */
    public static boolean expired(long spawnTick, long nowTick) {
        long spawn = Math.max(0L, spawnTick);
        return nowTick >= spawn && nowTick - spawn >= LIFETIME_TICKS;
    }

    /** The maximum lifetime ticks a session is allowed. */
    public static long lifetimeTicks() {
        return LIFETIME_TICKS;
    }

    // --- cleanup -------------------------------------------------------------

    /** Every terminal reason requires cleanup of the rare Echo. */
    public static boolean requiresCleanup(String reason) {
        return reason != null;
    }

    // --- incoming damage -----------------------------------------------------

    /**
     * Whether the given attacker may damage the Echo under the current mode.
     * Defaults to OWNER_ONLY until the owner approves another choice.
     */
    public static boolean mayBeDamagedBy(UUID attackerId, UUID ownerId,
                                         IncomingDamageMode mode) {
        if (attackerId == null || ownerId == null) return false;
        return switch (mode == null ? IncomingDamageMode.OWNER_ONLY : mode) {
            case OWNER_ONLY -> attackerId.equals(ownerId);
            case ALLIED_HELP -> true; // Anyone may damage in this mode.
            case FREE_FOR_ALL -> true;
        };
    }

    // --- helpers -------------------------------------------------------------

    /** Stable diagnostic label. */
    public static String describe(int alive, long lastSummonTick, long nowTick) {
        boolean ready = maySummon(lastSummonTick, nowTick, alive, true, true);
        return "RARE_ECHO alive=" + alive + " max=" + MAX_SIMULTANEOUS
                + " cooldown_ticks=" + COOLDOWN_TICKS
                + " lifetime_ticks=" + LIFETIME_TICKS
                + " health_multiplier=" + HEALTH_MULTIPLIER
                + " damage_multiplier=" + DAMAGE_MULTIPLIER
                + " ready=" + ready;
    }
}
