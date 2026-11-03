package me.copimine.endevent.domain;

/**
 * MAGE7-003 / MAGE7-004: bounded Vex support for the Rift Archmage.
 *
 * <p>Repeated summoning is part of the Archmage's identity, but accumulation is
 * not. The live cap scales with the admitted roster, Vexes arrive in small
 * groups on an explicit cadence, they expire, and they are children of the
 * stage: their deaths never complete it.
 */
public final class Wave7ArchmageVexPolicy {
    /** Absolute ceiling of live Vexes, whatever the roster size. */
    public static final int HARD_CAP = 6;
    /** Small bounded groups rather than dumping the whole cap at once. */
    public static final int SUMMON_GROUP_SIZE = 1;
    /** Explicit cadence between summon groups. */
    public static final long SUMMON_INTERVAL_TICKS = 200L;
    /** Finite lifetime so the stage cannot fill up over time. */
    public static final long VEX_LIFETIME_TICKS = 1_200L;
    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 20;
    /** Records the tick of the last summon so the cadence is real, not advisory. */
    public static final long NEVER_SUMMONED = Long.MIN_VALUE;

    private Wave7ArchmageVexPolicy() {
    }

    /** Live Vex cap for an admitted roster size, or {@code 0} outside Wave 7's range. */
    public static int cap(int admittedPlayers) {
        if (admittedPlayers < MIN_PARTICIPANTS || admittedPlayers > MAX_PARTICIPANTS) {
            return 0;
        }
        if (admittedPlayers <= 4) {
            return 2;
        }
        if (admittedPlayers <= 9) {
            return 4;
        }
        return HARD_CAP;
    }

    /**
     * How many Vexes to summon now: zero while the cadence is pending or the
     * live set is full, otherwise a bounded group that never overshoots the cap.
     *
     * @param cap              the live cap for this roster
     * @param liveVexes        Vexes currently alive for this stage
     * @param ticksSinceSummon ticks since the previous summon, or {@link #NEVER_SUMMONED}
     */
    public static int summonCount(int cap, int liveVexes, long ticksSinceSummon) {
        if (cap <= 0 || liveVexes < 0 || liveVexes >= cap) {
            return 0;
        }
        if (ticksSinceSummon != NEVER_SUMMONED && ticksSinceSummon < SUMMON_INTERVAL_TICKS) {
            return 0;
        }
        return Math.min(SUMMON_GROUP_SIZE, cap - liveVexes);
    }

    /** A Vex that reached its finite lifetime is removed rather than left running. */
    public static boolean expired(long ticksAlive) {
        return ticksAlive >= VEX_LIFETIME_TICKS;
    }

    public static boolean expireOnRootDeath() {
        return true;
    }

    public static boolean expireOnReset() {
        return true;
    }

    public static boolean childHasOwnIdentity() {
        return true;
    }

    /** W7-003: a child death is never a root death. */
    public static boolean vexDeathCompletesStage() {
        return false;
    }

    /**
     * The Archmage's Vex spell: it spends its own minions as ammunition.
     *
     * <p>Until now the Vexes were scenery — they floated around and bit whoever
     * stood close, and the party could ignore them entirely. The Archmage now
     * has a spell that <b>consumes</b> them: every live Vex is detonated on its
     * nearest target and the Archmage recovers health per Vex spent. That
     * creates the decision the wave was missing — kill the Vexes early and
     * deny the Archmage its burst and its healing, or ignore them and eat both.
     * Killing them is strictly better for the party, so the spell rewards
     * reaction rather than luck.</p>
     *
     * <p>Every bound is explicit: the spell needs at least
     * {@link #SWARM_MIN_VEXES} live Vexes, it never detonates more than the
     * live cap can produce, its damage and healing are per-Vex constants, and
     * its cooldown is long enough that a wiped swarm cannot be re-farmed
     * instantly.</p>
     */
    public static final int SWARM_MIN_VEXES = 2;

    /** Ticks between two swarm casts, counted from the previous resolution. */
    public static final long SWARM_COOLDOWN_TICKS = 320L;

    /** Ticks of visible telegraph before the swarm detonates. */
    public static final int SWARM_TELEGRAPH_TICKS = 26;

    /** Damage of one detonating Vex. */
    public static final double SWARM_DAMAGE_PER_VEX = 4.0D;

    /** Hard per-cast damage ceiling, so a large roster cannot be one-shot. */
    public static final double SWARM_MAX_DAMAGE = 16.0D;

    /** Health the Archmage recovers per Vex spent; refused when its HP is full. */
    public static final double SWARM_HEAL_PER_VEX = 6.0D;

    /** Hard per-cast healing ceiling. */
    public static final double SWARM_MAX_HEAL = 24.0D;

    /** Radius around the Archmage in which a Vex can find a target; beyond it the cast is refused. */
    public static final double SWARM_RADIUS_BLOCKS = 18.0D;

    /** Whether the Archmage may cast the swarm spell right now. */
    public static boolean swarmReady(int liveVexes, long ticksSinceSwarm, boolean archmageAlive,
                                     boolean archmageBusy) {
        if (!archmageAlive || archmageBusy) return false;
        if (liveVexes < SWARM_MIN_VEXES) return false;
        return ticksSinceSwarm < 0L || ticksSinceSwarm >= SWARM_COOLDOWN_TICKS;
    }

    /** Total damage of a detonation of {@code vexCount} Vexes, capped. */
    public static double swarmDamage(int vexCount) {
        if (vexCount <= 0) return 0.0D;
        return Math.min(SWARM_MAX_DAMAGE, SWARM_DAMAGE_PER_VEX * vexCount);
    }

    /**
     * Total healing of a detonation, capped and refused at full health.
     *
     * <p>A full-health Archmage gains nothing, so the spell is never a free
     * reset; the caller must still spend the Vexes, because that is the cost
     * the party paid attention for.</p>
     */
    public static double swarmHeal(int vexCount, double currentHealth, double maximumHealth) {
        if (vexCount <= 0 || currentHealth >= maximumHealth) return 0.0D;
        double heal = Math.min(SWARM_MAX_HEAL, SWARM_HEAL_PER_VEX * vexCount);
        return Math.min(heal, maximumHealth - currentHealth);
    }

    /** How many Vexes the spell may actually consume, bounded by the live cap. */
    public static int swarmVexSpent(int liveVexes, int cap) {
        if (liveVexes <= 0 || cap <= 0) return 0;
        return Math.min(liveVexes, cap);
    }

    public static boolean confinedToArena() {
        return true;
    }

    public static boolean mayTargetOutsideEncounter() {
        return false;
    }
}