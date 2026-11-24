package me.copimine.dungeon.domain;

import java.util.List;
import java.util.Objects;

/**
 * Pure core of the authorised room lifecycle (spec OV4.10
 * {@code DungeonRoomController}; verification R01-R04).
 *
 * <p>One authoritative room generation drives
 * {@code PUBLISHED_IDLE -> ARMING -> SEALING -> FIGHTING -> CLEAR_COMMITTED ->
 * OPENING -> REWARD_READY -> RESETTING -> PUBLISHED_IDLE}. Activation is only
 * possible from {@code PUBLISHED_IDLE} and bumps the generation, so a burst of
 * simultaneous players or a duplicated trigger cannot stand up a second
 * controller. Waves are spawned on the plan's schedule, the exit is opened only
 * once the room is genuinely clear, and the reward is requested exactly once per
 * generation before the room resets.</p>
 *
 * <p>Everything side-effecting (gate masks, entity spawns, loot receipts) is an
 * injected {@link Ports}; this class owns no Bukkit types, so the whole state
 * machine is exercised with fake ports and explicit ticks.</p>
 */
public final class DungeonRoomLifecycle {

    /** What the last call did, for logs, tests and the Bukkit adapter. */
    public enum Signal {
        IDLE,
        FIGHTING,
        OPENED,
        REWARDED,
        ABORTED,
        REJECTED
    }

    /** The world-facing operations the controller delegates to the adapter. */
    public interface Ports {
        /** Closes/seals the room gates; {@code false} hides a failed seal. */
        boolean sealEntrance();

        /** Opens the exit gates; {@code false} means the room must abort. */
        boolean openExit();

        /** Spawns one wave's assignments; returns how many actors went live. */
        int spawnWave(int waveIndex, List<DungeonEncounterPlanPolicy.Assignment> assignments);

        /** Currently live, valid room-owned actors. */
        int liveEnemies();

        /** Requests the once-only reward for this generation. */
        boolean grantReward();

        /** Operator-readable diagnostic (never a gameplay shortcut). */
        void report(String message);
    }

    private final DungeonRoomDefinition room;
    private final DungeonEncounterPlanPolicy.Plan plan;
    private final Ports ports;

    private RoomState state = RoomState.PUBLISHED_IDLE;
    private long generation;
    private long fightStartTick = -1L;
    private int nextWave;
    private boolean rewarded;

    public DungeonRoomLifecycle(DungeonRoomDefinition room,
                                DungeonEncounterPlanPolicy.Plan plan,
                                Ports ports) {
        this.room = Objects.requireNonNull(room, "room");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.ports = Objects.requireNonNull(ports, "ports");
    }

    public DungeonRoomDefinition room() {
        return room;
    }

    public DungeonEncounterPlanPolicy.Plan plan() {
        return plan;
    }

    public RoomState state() {
        return state;
    }

    public long generation() {
        return generation;
    }

    public int nextWave() {
        return nextWave;
    }

    /** True once the run is armed and before it returns to idle. */
    public boolean isRunning() {
        return state.isActive();
    }

    /**
     * Attempts to start the room for an eligible roster. Only a genuinely idle,
     * viable room starts; every other case is a safe rejection.
     */
    public Signal tryActivate(int eligibleParticipants, long tick) {
        if (state != RoomState.PUBLISHED_IDLE) {
            return Signal.REJECTED;
        }
        if (eligibleParticipants < 1 || !plan.viable() || plan.waveCount() == 0) {
            return Signal.REJECTED;
        }
        generation++;
        move(RoomState.ARMING);
        move(RoomState.SEALING);
        if (!ports.sealEntrance()) {
            ports.report("room " + room.id() + " could not seal its entrance; aborting generation "
                    + generation);
            return abortToIdle();
        }
        move(RoomState.FIGHTING);
        fightStartTick = tick;
        nextWave = 0;
        rewarded = false;
        spawnDueWaves(tick);
        return Signal.FIGHTING;
    }

    /**
     * Advances the room on the adapter's tick. Spawns due waves, commits a real
     * clear, opens the exit and requests the reward once before resetting.
     */
    public Signal advance(long tick) {
        if (state == RoomState.FIGHTING) {
            spawnDueWaves(tick);
            if (nextWave >= plan.waveCount() && ports.liveEnemies() == 0) {
                move(RoomState.CLEAR_COMMITTED);
                move(RoomState.OPENING);
                if (!ports.openExit()) {
                    ports.report("room " + room.id() + " could not open its exit; aborting generation "
                            + generation);
                    return abortToIdle();
                }
                move(RoomState.REWARD_READY);
                return Signal.OPENED;
            }
            return Signal.FIGHTING;
        }
        if (state == RoomState.REWARD_READY) {
            if (!rewarded) {
                rewarded = true;
                if (!ports.grantReward()) {
                    ports.report("room " + room.id() + " reward deferred for generation " + generation);
                }
            }
            move(RoomState.RESETTING);
            move(RoomState.PUBLISHED_IDLE);
            return Signal.REWARDED;
        }
        return Signal.IDLE;
    }

    /** Aborts a running room, opening the exit so nobody is trapped. */
    public Signal abort() {
        if (!state.isActive()) {
            return Signal.REJECTED;
        }
        return abortToIdle();
    }

    private Signal abortToIdle() {
        move(RoomState.ABORTING);
        ports.openExit();
        move(RoomState.RESETTING);
        move(RoomState.PUBLISHED_IDLE);
        fightStartTick = -1L;
        nextWave = 0;
        return Signal.ABORTED;
    }

    private void spawnDueWaves(long tick) {
        if (fightStartTick < 0L) {
            return;
        }
        while (nextWave < plan.waveCount()
                && tick - fightStartTick >= startTickOf(nextWave)) {
            int index = nextWave;
            ports.spawnWave(index, plan.wave(index));
            nextWave++;
        }
    }

    /** Cumulative start tick of a wave, matching {@link RoomWaveSchedulePolicy}. */
    private long startTickOf(int waveIndex) {
        long due = 0L;
        for (int index = 0; index < waveIndex && index < room.waves().size(); index++) {
            due += room.waves().get(index).delayTicks();
        }
        return due;
    }

    private boolean move(RoomState to) {
        if (!RoomStateTransitionValidator.isLegal(state, to)) {
            return false;
        }
        state = to;
        return true;
    }
}
