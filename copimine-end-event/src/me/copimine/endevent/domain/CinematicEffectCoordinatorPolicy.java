package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure core of the shared cinematic effect coordinator named in V6 master
 * §OV4.10: "server lightweight shared timing and client cached
 * rigs/material/mesh/shader/audio, used by Guardian, minibosses, dungeon mobs and
 * gate opening while preserving existing effect contracts."
 *
 * <p>The expensive parts of a shared cinematic are not the visuals but the
 * ownership mistakes: two casts animating the same rig, a cheap miniboss
 * overwriting the Guardian's committed spell, a cast whose lease outlives its
 * phase, and a cleanup that releases the wrong set - or releases nothing -
 * because a tick walked the stage ladder twice. This class owns only the
 * deterministic bookkeeping:</p>
 *
 * <ul>
 *   <li><b>one live lease per owner</b>, so a second cast from the same owner
 *       key is refused instead of silently replacing the first;</li>
 *   <li>a <b>monotone stage ladder</b> walked one rung at a time, never
 *       backwards, never skipping, with an overrun past the cast deadline
 *       returning {@link Advance#EXPIRED} so a stuck cast must clean up rather
 *       than keep running;</li>
 *   <li>a <b>bounded, ordered resource ledger</b> so cleanup releases exactly
 *       the resources the cast bound, in a deterministic order, exactly
 *       once;</li>
 *   <li>a <b>reduced-motion fallback</b> that keeps the gameplay telegraph and
 *       drops the flourish, never the other way round.</li>
 * </ul>
 *
 * <p>No Paper type, no world access, no scheduler: the caller ticks this class
 * and applies the returned plan through its own client-bound writer. Timing is
 * expressed in server ticks supplied by the caller, so a fake clock in a test
 * and the live server share one contract.</p>
 */
public final class CinematicEffectCoordinatorPolicy {

    /** Hard cap on owners tracked at once, so a runaway summoner fails closed. */
    public static final int MAX_OWNERS = 32;
    /** Hard cap on bound resources per cast. */
    public static final int MAX_RESOURCES_PER_CAST = 16;
    /** Longest accepted cinematic, in ticks (60 seconds at nominal 20 TPS). */
    public static final int MAX_DURATION_TICKS = 1_200;

    /** The shared cast ladder: SELECT -> WARN -> LOCK -> EXECUTE -> RECOVERY -> CLEANUP -> DONE. */
    public enum Stage {
        SELECT,
        WARN,
        LOCK,
        EXECUTE,
        RECOVERY,
        CLEANUP,
        DONE
    }

    /** The single outcome a committed cast settles to, at most once. */
    public enum Result {
        PENDING,
        HIT,
        MISS
    }

    /** The client-side assets a cast may lease; cleanup releases them all. */
    public enum ResourceKind {
        MESH,
        RIG,
        MATERIAL,
        SHADER,
        AUDIO,
        PACKET,
        LIGHT
    }

    public enum Acquire {
        ACCEPTED,
        BUSY,
        INVALID
    }

    public enum Advance {
        ADVANCED,
        ALREADY_SETTLED,
        OUT_OF_ORDER,
        UNKNOWN_OWNER,
        EXPIRED
    }

    public enum Bind {
        BOUND,
        ALREADY_BOUND,
        UNKNOWN_OWNER,
        INVALID,
        OVER_BUDGET
    }

    public enum Resolve {
        SETTLED,
        ALREADY_SETTLED,
        NOT_EXECUTING,
        UNKNOWN_OWNER
    }

    public enum Cleanup {
        RELEASED,
        ALREADY_RELEASED,
        UNKNOWN_OWNER
    }

    /**
     * The result of a release: whether it owned the lease, and the exact ordered
     * resources the caller must tear down (empty unless {@code RELEASED}).
     */
    public record Release(Cleanup status, List<String> resources) {
        public Release {
            Objects.requireNonNull(status, "status");
            resources = List.copyOf(resources);
        }
    }

    /** One cast's lease, stage, deadline and resource ledger. */
    private static final class Cast {
        private final String actionId;
        private final long startTick;
        private final long deadlineTick;
        private final boolean reducedMotion;
        private Stage stage = Stage.SELECT;
        private Result result = Result.PENDING;
        private final LinkedHashSet<String> resources = new LinkedHashSet<>();
        private boolean released;

        private Cast(String actionId, long startTick, int durationTicks, boolean reducedMotion) {
            this.actionId = actionId;
            this.startTick = startTick;
            this.deadlineTick = startTick + durationTicks;
            this.reducedMotion = reducedMotion;
        }
    }

    private final LinkedHashMap<String, Cast> casts = new LinkedHashMap<>();
    private int releasedCount;

    public CinematicEffectCoordinatorPolicy() {
    }

    /**
     * Takes the single live lease for {@code ownerKey}.
     *
     * <p>A live cast for the same owner returns {@link Acquire#BUSY} and changes
     * nothing: a second cast may not silently replace a committed animation.
     * A released lease frees the slot for the owner's next cast. Inputs are
     * validated and bounded, never clamped silently.</p>
     */
    public Acquire acquire(String ownerKey, String actionId, long startTick, int durationTicks,
                           boolean reducedMotion) {
        String owner = normalize(ownerKey);
        String action = normalize(actionId);
        if (owner == null || action == null || startTick < 0L
                || durationTicks <= 0 || durationTicks > MAX_DURATION_TICKS) {
            return Acquire.INVALID;
        }
        Cast live = casts.get(owner);
        if (live != null && !live.released) {
            return Acquire.BUSY;
        }
        if (live == null && casts.size() >= MAX_OWNERS) {
            return Acquire.INVALID;
        }
        casts.put(owner, new Cast(action, startTick, durationTicks, reducedMotion));
        return Acquire.ACCEPTED;
    }

    public boolean hasLiveLease(String ownerKey) {
        Cast cast = casts.get(normalize(ownerKey));
        return cast != null && !cast.released;
    }

    public String actionId(String ownerKey) {
        Cast cast = casts.get(normalize(ownerKey));
        return cast == null ? null : cast.actionId;
    }

    public Stage stage(String ownerKey) {
        Cast cast = casts.get(normalize(ownerKey));
        return cast == null ? null : cast.stage;
    }

    public Result result(String ownerKey) {
        Cast cast = casts.get(normalize(ownerKey));
        return cast == null ? null : cast.result;
    }

    public boolean reducedMotion(String ownerKey) {
        Cast cast = casts.get(normalize(ownerKey));
        return cast != null && cast.reducedMotion;
    }

    /** Remaining ticks before the cast deadline; never negative. */
    public long remainingTicks(String ownerKey, long nowTick) {
        Cast cast = casts.get(normalize(ownerKey));
        if (cast == null) {
            return 0L;
        }
        return Math.max(0L, cast.deadlineTick - Math.max(cast.startTick, nowTick));
    }

    /** True once {@code nowTick} has passed the cast deadline. */
    public boolean expired(String ownerKey, long nowTick) {
        Cast cast = casts.get(normalize(ownerKey));
        return cast != null && nowTick > cast.deadlineTick;
    }

    /**
     * Walks one rung of the ladder.
     *
     * <p>Replaying the current stage returns {@link Advance#ALREADY_SETTLED} and
     * is not a second run. Any other non-adjacent move returns
     * {@link Advance#OUT_OF_ORDER}, so nothing can skip the warning or walk the
     * ladder backwards. Once the deadline has passed, every ordinary rung is
     * refused with {@link Advance#EXPIRED}; only {@code CLEANUP} and
     * {@code DONE} may still be reached, so an overrunning cast is forced to
     * release its resources instead of animating forever.</p>
     */
    public Advance advance(String ownerKey, Stage next, long nowTick) {
        Cast cast = casts.get(normalize(ownerKey));
        if (cast == null || cast.released) {
            return Advance.UNKNOWN_OWNER;
        }
        Objects.requireNonNull(next, "next stage");
        if (next == cast.stage) {
            return Advance.ALREADY_SETTLED;
        }
        if (next.ordinal() <= cast.stage.ordinal()) {
            return Advance.OUT_OF_ORDER;
        }
        if (next.ordinal() != cast.stage.ordinal() + 1) {
            return Advance.OUT_OF_ORDER;
        }
        if (nowTick > cast.deadlineTick && next.ordinal() < Stage.CLEANUP.ordinal()) {
            return Advance.EXPIRED;
        }
        cast.stage = next;
        return Advance.ADVANCED;
    }

    /** Settles the cast outcome once, only while it is actually executing. */
    public Resolve resolve(String ownerKey, Result outcome) {
        Cast cast = casts.get(normalize(ownerKey));
        if (cast == null || cast.released) {
            return Resolve.UNKNOWN_OWNER;
        }
        Objects.requireNonNull(outcome, "outcome");
        if (outcome == Result.PENDING) {
            return Resolve.SETTLED;
        }
        if (cast.result != Result.PENDING) {
            return Resolve.ALREADY_SETTLED;
        }
        if (cast.stage.ordinal() < Stage.EXECUTE.ordinal()) {
            return Resolve.NOT_EXECUTING;
        }
        cast.result = outcome;
        return Resolve.SETTLED;
    }

    /**
     * Binds one client resource to the cast. The same kind/id pair binds once;
     * a cast may not exceed {@link #MAX_RESOURCES_PER_CAST}, so a broken
     * definition cannot grow an unbounded cleanup list.
     */
    public Bind bind(String ownerKey, ResourceKind kind, String resourceId) {
        Cast cast = casts.get(normalize(ownerKey));
        if (cast == null || cast.released) {
            return Bind.UNKNOWN_OWNER;
        }
        Objects.requireNonNull(kind, "kind");
        String id = normalize(resourceId);
        if (id == null) {
            return Bind.INVALID;
        }
        String key = kind.name() + ":" + id;
        if (cast.resources.contains(key)) {
            return Bind.ALREADY_BOUND;
        }
        if (cast.resources.size() >= MAX_RESOURCES_PER_CAST) {
            return Bind.OVER_BUDGET;
        }
        cast.resources.add(key);
        return Bind.BOUND;
    }

    public int boundCount(String ownerKey) {
        Cast cast = casts.get(normalize(ownerKey));
        return cast == null ? 0 : cast.resources.size();
    }

    /**
     * The exact, ordered cleanup plan for one cast, without releasing it:
     * grouped by {@link ResourceKind} ordinal, then by id, so two identical
     * casts produce identical plans and a test can assert the whole list.
     */
    public List<String> cleanupPlan(String ownerKey) {
        Cast cast = casts.get(normalize(ownerKey));
        if (cast == null) {
            return List.of();
        }
        List<String> plan = new ArrayList<>(cast.resources);
        plan.sort(Comparator
                .comparingInt((String key) -> ResourceKind.valueOf(key.substring(0, key.indexOf(':'))).ordinal())
                .thenComparing(String::compareTo));
        return List.copyOf(plan);
    }

    /**
     * Releases the lease and returns every resource the caller must tear down.
     *
     * <p>Release is once-only: the first call returns the plan, moves the cast to
     * {@code DONE} and frees the owner slot; every later call returns
     * {@link Cleanup#ALREADY_RELEASED} with an empty plan, so a retried cleanup
     * cannot double-free a shared rig.</p>
     */
    public Release release(String ownerKey) {
        Cast cast = casts.get(normalize(ownerKey));
        if (cast == null) {
            return new Release(Cleanup.UNKNOWN_OWNER, List.of());
        }
        if (cast.released) {
            return new Release(Cleanup.ALREADY_RELEASED, List.of());
        }
        List<String> plan = cleanupPlan(ownerKey);
        cast.stage = Stage.DONE;
        cast.released = true;
        cast.resources.clear();
        releasedCount++;
        return new Release(Cleanup.RELEASED, plan);
    }

    /** Number of releases performed, for leak assertions in a repeating harness. */
    public int releasedCount() {
        return releasedCount;
    }

    /** Number of owner slots currently held by a live cast. */
    public int liveCount() {
        int live = 0;
        for (Cast cast : casts.values()) {
            if (!cast.released) {
                live++;
            }
        }
        return live;
    }

    /** Every live owner key, in acquisition order, for deterministic diagnostics. */
    public Set<String> liveOwners() {
        LinkedHashSet<String> owners = new LinkedHashSet<>();
        for (Map.Entry<String, Cast> entry : casts.entrySet()) {
            if (!entry.getValue().released) {
                owners.add(entry.getKey());
            }
        }
        return java.util.Collections.unmodifiableSet(owners);
    }

    /**
     * The stages a client actually authors for a cast.
     *
     * <p>Reduced motion drops the flourish and keeps the contract: the warning
     * that tells a player to move, the lock that fixes the target, and the
     * cleanup that proves the effect ended. It never drops WARN, LOCK or
     * CLEANUP, and it never invents a stage the full timeline does not have.</p>
     */
    public static List<Stage> authoredStages(boolean reducedMotion) {
        if (reducedMotion) {
            return List.of(Stage.WARN, Stage.LOCK, Stage.CLEANUP);
        }
        return List.of(Stage.WARN, Stage.LOCK, Stage.EXECUTE, Stage.RECOVERY, Stage.CLEANUP);
    }

    /** Flourish layers a client may add; zero under reduced motion. */
    public static int flourishLayers(boolean reducedMotion) {
        return reducedMotion ? 0 : 3;
    }

    /**
     * A shared cinematic never seizes the camera; the canonical End Rift design
     * forbids unnecessary camera seizure for waves 1-7 and the boss. Exposed as
     * a function so a caller cannot "helpfully" add one without failing a test.
     */
    public static boolean allowsCameraTakeover() {
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
