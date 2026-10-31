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

    public static boolean confinedToArena() {
        return true;
    }

    public static boolean mayTargetOutsideEncounter() {
        return false;
    }
}