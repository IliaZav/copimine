package me.copimine.endevent.domain;

import java.util.List;

/**
 * Owner request: Phase 4 needs its own transition cinematic (V6 §947/§8).
 *
 * <p>The generic phase tell is short by design, but the RIFT→OVERLOAD change is
 * the arena transformation: the Guardian staggers and turns toward the Core,
 * shards levitate, the pre-authored cracks brighten, a flame vortex rises above
 * arena level, the sound builds and then one pressure release lands. This policy
 * owns only that presentation timeline. It never grants invulnerability, never
 * holds player control and never deals damage.</p>
 *
 * <p>The scene is 3.2 s, inside the approved 3–6 s window: long enough to read
 * the transformation, short enough that the fight does not idle.</p>
 */
public final class InfernoTransitionCinematicPolicy {

    /** 20 ticks per second. */
    public static final int TICKS_PER_SECOND = 20;
    public static final int GATHER_TICKS = 24;   // 0.0-1.2 s: stagger, cracks light
    public static final int LEVITATE_TICKS = 20; // 1.2-2.2 s: shards and vortex rise
    public static final int VORTEX_TICKS = 12;   // 2.2-2.8 s: overhead flame column
    public static final int RELEASE_TICKS = 8;   // 2.8-3.2 s: single pressure release
    public static final int TOTAL_TICKS = GATHER_TICKS + LEVITATE_TICKS
            + VORTEX_TICKS + RELEASE_TICKS;

    public static final double VORTEX_RADIUS_BLOCKS = 5.5D;
    public static final int SHARD_COUNT = 18;
    public static final int EMBER_COUNT = 24;

    public enum Stage {
        GATHER,
        LEVITATE,
        VORTEX,
        RELEASE,
        ENDED
    }

    private InfernoTransitionCinematicPolicy() {
    }

    public static int totalTicks() {
        return TOTAL_TICKS;
    }

    public static int releaseTick() {
        return GATHER_TICKS + LEVITATE_TICKS + VORTEX_TICKS;
    }

    public static long durationMillis() {
        return TOTAL_TICKS * 1000L / TICKS_PER_SECOND;
    }

    public static Stage stageFor(int tick) {
        if (tick < 0) {
            return Stage.GATHER;
        }
        if (tick >= TOTAL_TICKS) {
            return Stage.ENDED;
        }
        if (tick < GATHER_TICKS) {
            return Stage.GATHER;
        }
        if (tick < GATHER_TICKS + LEVITATE_TICKS) {
            return Stage.LEVITATE;
        }
        if (tick < releaseTick()) {
            return Stage.VORTEX;
        }
        return Stage.RELEASE;
    }

    /** The one tick that must fire the release exactly once. */
    public static boolean isReleaseTick(int tick) {
        return tick == releaseTick();
    }

    /** Height of the overhead vortex in blocks at a given tick, 0 outside. */
    public static double vortexHeight(int tick) {
        if (tick < GATHER_TICKS || tick >= TOTAL_TICKS) {
            return 0.0D;
        }
        double progress = (double) (tick - GATHER_TICKS)
                / Math.max(1, TOTAL_TICKS - GATHER_TICKS);
        return 2.0D + 6.0D * Math.min(1.0D, progress);
    }

    /** Shard count that is actually levitating at a tick. */
    public static int visibleShardCount(int tick) {
        if (tick < GATHER_TICKS) {
            return 0;
        }
        double progress = (double) (tick - GATHER_TICKS)
                / Math.max(1, TOTAL_TICKS - GATHER_TICKS);
        return (int) Math.round(SHARD_COUNT * Math.min(1.0D, progress));
    }

    /**
     * The staging beats, for diagnostics and the client storyboard. Bounded and
     * ordered, so a regression cannot silently drop a beat.
     */
    public static List<String> beats() {
        return List.of(
                "GATHER:stagger-and-turn-to-core",
                "LEVITATE:shards-and-cracks",
                "VORTEX:overhead-flame-column",
                "RELEASE:single-pressure-burst");
    }

    public static String describe() {
        return "PHASE4_TRANSITION_CINEMATIC total_ticks=" + TOTAL_TICKS
                + " duration_ms=" + durationMillis()
                + " release_tick=" + releaseTick()
                + " vortex_radius=" + VORTEX_RADIUS_BLOCKS
                + " shards=" + SHARD_COUNT
                + " beats=" + beats().size();
    }
}
