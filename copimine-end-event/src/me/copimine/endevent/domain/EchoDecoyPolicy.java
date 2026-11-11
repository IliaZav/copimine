package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5, Echo Type B — persistent boss decoys (master contract 15.3).
 *
 * <p>The real Rift Guardian summons combat-capable replicas of itself. The
 * numbers are fixed by the contract: exactly 50 hit points each, at most five
 * alive at once, roughly a 150 second summon cooldown, and a reduced damage
 * output. The invariants are what keep the encounter winnable, so they are
 * stated here as plain questions the adapter must ask:</p>
 *
 * <ul>
 *   <li>a decoy never shares the boss's authoritative health or boss bar;</li>
 *   <li>a decoy death never counts as the boss defeat or as boss damage;</li>
 *   <li>a decoy drops no loot and grants no experience;</li>
 *   <li>a decoy cannot damage other event mobs;</li>
 *   <li>a decoy never becomes a permanent orphan.</li>
 * </ul>
 */
public final class EchoDecoyPolicy {
    /** 15.3: exactly fifty hit points. */
    public static final double DECOY_HEALTH = 50.0D;
    /** 15.3: at most five living decoys. */
    public static final int MAX_ALIVE = 5;
    /** 15.3: about 150 seconds between summons. */
    public static final int SUMMON_COOLDOWN_TICKS = 3000;
    /** 15.3: decoys hit softer than the real boss. */
    public static final double DAMAGE_MULTIPLIER = 0.45D;
    public static final double FALLBACK_BOSS_DAMAGE = 14.0D;
    /** A decoy never outlives its phase; this is the orphan backstop. */
    public static final long MAX_LIFETIME_TICKS = 20L * 60L * 10L;

    /** The only outcomes a decoy death may have. */
    public enum DecoyOutcome {
        CLEANUP_ONLY
    }

    private EchoDecoyPolicy() {
    }

    // --- health --------------------------------------------------------------

    /** 15.3: the pool is absolutely fixed at 50, at every party size. */
    public static double decoyHealth(int players) {
        return DECOY_HEALTH;
    }

    /** The adapter's real-entity health override: still exactly 50. */
    public static double decoyHealthOverride(double bossMaxHealth) {
        return DECOY_HEALTH;
    }

    // --- the alive cap ------------------------------------------------------

    /** How many more decoys may be summoned with {@code alive} already living. */
    public static int maySummonTo(int alive) {
        int living = Math.max(0, alive);
        return Math.max(0, MAX_ALIVE - living);
    }

    /** Never overfills the cap, and never grants more than the caller asked for. */
    public static int resolveSummonBatch(int alive, int requested) {
        int capacity = maySummonTo(alive);
        return Math.max(0, Math.min(capacity, requested));
    }

    // --- the cooldown -------------------------------------------------------

    /**
     * True when a summon may happen.
     *
     * @param lastSummonTick absolute tick of the previous summon, or -1
     * @param nowTick        current absolute tick
     */
    public static boolean maySummon(long lastSummonTick, long nowTick) {
        if (lastSummonTick < 0L || nowTick < 0L) {
            return true;
        }
        return nowTick - lastSummonTick >= SUMMON_COOLDOWN_TICKS;
    }

    /**
     * The planned summon ticks of one phase.
     *
     * <p>No forced repetition: a phase that ends quickly gets one summon at
     * most, and the schedule simply stops when the phase does.</p>
     */
    public static List<Long> summonSchedule(long phaseStartTick, long phaseEndTick) {
        long start = Math.max(0L, phaseStartTick);
        long end = phaseEndTick;
        if (end < start) {
            return List.of();
        }
        List<Long> ticks = new ArrayList<>();
        long tick = start;
        while (tick <= end) {
            ticks.add(tick);
            tick += SUMMON_COOLDOWN_TICKS;
        }
        return List.copyOf(ticks);
    }

    // --- initial count ------------------------------------------------------

    /**
     * Decoys present when the phase opens.
     *
     * <p>Scaled for the party but never a swarm: a solo player faces one at most,
     * a raid reaches the cap of five.</p>
     */
    public static int initialDecoyCount(int players) {
        int safe = Math.max(0, Math.min(20, players));
        if (safe == 0) {
            return 0;
        }
        if (safe <= 2) {
            return safe;
        }
        if (safe <= 5) {
            return 3;
        }
        if (safe <= 10) {
            return 4;
        }
        return MAX_ALIVE;
    }

    // --- the invariants -----------------------------------------------------

    /** 15.3: never share the real boss's authoritative HP state. */
    public static boolean sharesBossHealth() {
        return false;
    }

    /** 15.3: never join the real boss's boss bar. */
    public static boolean joinsBossBar() {
        return false;
    }

    /** 15.3: the same visual model family as the real boss. */
    public static boolean usesBossModelFamily() {
        return true;
    }

    /**
     * 15.3: "a careful player should still be able to identify the real boss
     * through subtle, learnable clues".
     */
    public static boolean hasIdentifiableClues() {
        return true;
    }

    /** 15.3: a decoy's death never triggers the real boss's defeat. */
    public static boolean deathTriggersBossDefeat() {
        return false;
    }

    /** 15.3: a decoy is not the real boss, so it is no phase progress either. */
    public static boolean deathCountsAsBossDamage() {
        return false;
    }

    /** Killing every decoy never ends the encounter. */
    public static boolean allDecoysDeadEndsEncounter(int killedDecoys) {
        return false;
    }

    /** The single legal reaction to a decoy death. */
    public static DecoyOutcome onDecoyDeath(String decoyId) {
        return DecoyOutcome.CLEANUP_ONLY;
    }

    /** 15.3: no inappropriate items or rewards. */
    public static boolean dropsLoot() {
        return false;
    }

    public static int experienceReward() {
        return 0;
    }

    /** 15.3: a decoy cannot damage other event mobs. */
    public static boolean mayDamageOtherEventMobs() {
        return false;
    }

    /** Real, reduced damage output. */
    public static double decoyDamage(double bossDamage) {
        double base = Double.isFinite(bossDamage) && bossDamage > 0.0D
                ? bossDamage : FALLBACK_BOSS_DAMAGE;
        return base * DAMAGE_MULTIPLIER;
    }

    // --- cleanup ------------------------------------------------------------

    /** Every terminal reason cleans the decoys; an unknown one does too. */
    public static boolean requiresCleanup(String reason) {
        return true;
    }

    /** The decoys a phase end (or any other reason) must remove. */
    public static List<String> decoysToRemove(List<String> decoys, String reason) {
        if (decoys == null || decoys.isEmpty()) {
            return List.of();
        }
        return List.copyOf(decoys);
    }

    /** Lifetime backstop: a decoy cannot outlive its phase as an orphan. */
    public static boolean expires(long spawnTick, long nowTick) {
        long spawn = Math.max(0L, spawnTick);
        return nowTick >= spawn && nowTick - spawn >= MAX_LIFETIME_TICKS;
    }

    /** Diagnostic label; logs must never invent a different name. */
    public static String describe(int players, int alive) {
        return "BOSS_ECHO_DECOYS players=" + players
                + " alive=" + alive
                + " max_alive=" + MAX_ALIVE
                + " health=" + DECOY_HEALTH
                + " cooldown_ticks=" + SUMMON_COOLDOWN_TICKS
                + " damage_multiplier=" + DAMAGE_MULTIPLIER
                + " shares_boss_hp=" + sharesBossHealth()
                + " drops_loot=" + dropsLoot();
    }
}