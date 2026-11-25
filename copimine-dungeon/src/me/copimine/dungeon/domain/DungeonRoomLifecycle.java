package me.copimine.dungeon.domain;

import java.util.ArrayDeque;
import java.util.Deque;
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
        WAITING,
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

        default long reserveGeneration(long previous) {
            return Math.incrementExact(previous);
        }

        default boolean hasCombatFailure() {
            return false;
        }

        default boolean commitClear() {
            return true;
        }

        default boolean recoverAttempt() {
            return openExit();
        }

        default boolean resetAfterClear() {
            return true;
        }
    }

    private final DungeonRoomDefinition room;
    private final DungeonEncounterPlanPolicy.Plan plan;
    private final Ports ports;

    private RoomState state = RoomState.PUBLISHED_IDLE;
    private long generation;
    private long fightStartTick = -1L;
    private int nextWave;
    private static final int MAX_SPAWN_ATTEMPTS = 3;
    private static final int SPAWN_RETRY_TICKS = 20;

    private final Deque<DungeonEncounterPlanPolicy.Assignment> pending = new ArrayDeque<>();
    private int spawnFailures;
    private long nextSpawnTick;
    private boolean aborted;

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
        long reserved = ports.reserveGeneration(generation);
        if (reserved <= generation) {
            return Signal.REJECTED;
        }
        generation = reserved;
        aborted = false;
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
        spawnFailures = 0;
        nextSpawnTick = tick;
        return advance(tick);
    }

    public Signal advance(long tick) {
        if (state == RoomState.FIGHTING) {
            int live = ports.liveEnemies();
            if (ports.hasCombatFailure()) {
                return abortToIdle();
            }
            enqueueDueWaves(tick);
            if (!spawnPending(tick, Math.max(0, plan.liveCap() - live))) {
                return abortToIdle();
            }
            if (nextWave < plan.waveCount() || !pending.isEmpty() || ports.liveEnemies() != 0) {
                return Signal.FIGHTING;
            }
            if (ports.hasCombatFailure()) {
                return abortToIdle();
            }
            move(RoomState.CLEAR_COMMITTED);
        }
        if (state == RoomState.CLEAR_COMMITTED) {
            if (!ports.commitClear()) {
                return Signal.WAITING;
            }
            move(RoomState.OPENING);
        }
        if (state == RoomState.OPENING) {
            if (!ports.openExit()) {
                return Signal.WAITING;
            }
            move(RoomState.REWARD_READY);
            return Signal.OPENED;
        }
        if (state == RoomState.REWARD_READY) {
            if (!ports.grantReward()) {
                return Signal.WAITING;
            }
            move(RoomState.RESETTING);
        }
        if (state == RoomState.RESETTING && !aborted) {
            if (!ports.resetAfterClear()) {
                return Signal.WAITING;
            }
            move(RoomState.PUBLISHED_IDLE);
            return Signal.REWARDED;
        }
        if (state == RoomState.RECOVERY_REQUIRED) {
            return recover();
        }
        return Signal.IDLE;
    }

    public Signal abort() {
        if (state != RoomState.ARMING && state != RoomState.SEALING
                && state != RoomState.FIGHTING) {
            return Signal.REJECTED;
        }
        return abortToIdle();
    }

    private Signal abortToIdle() {
        aborted = true;
        move(RoomState.ABORTING);
        pending.clear();
        fightStartTick = -1L;
        nextWave = 0;
        return recover();
    }

    private Signal recover() {
        if (!ports.recoverAttempt()) {
            move(RoomState.RECOVERY_REQUIRED);
            return Signal.WAITING;
        }
        move(RoomState.RESETTING);
        move(RoomState.PUBLISHED_IDLE);
        return Signal.ABORTED;
    }

    private void enqueueDueWaves(long tick) {
        while (nextWave < plan.waveCount()
                && tick - fightStartTick >= startTickOf(nextWave)) {
            pending.addAll(plan.wave(nextWave));
            nextWave++;
        }
    }

    private boolean spawnPending(long tick, int capacity) {
        if (tick < nextSpawnTick) {
            return true;
        }
        while (capacity > 0 && !pending.isEmpty()) {
            DungeonEncounterPlanPolicy.Assignment assignment = pending.getFirst();
            if (ports.spawnWave(assignment.waveIndex(), List.of(assignment)) != 1) {
                nextSpawnTick = tick + SPAWN_RETRY_TICKS;
                if (++spawnFailures >= MAX_SPAWN_ATTEMPTS) {
                    ports.report("room " + room.id() + " exhausted spawn retries at "
                            + assignment.anchorId() + " for generation " + generation);
                    return false;
                }
                break;
            }
            pending.removeFirst();
            spawnFailures = 0;
            capacity--;
        }
        return true;
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
