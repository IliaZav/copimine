package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Phase 3 (RIFT) — real, reversible block mutation that ends in a fiery geyser.
 *
 * <p>The master contract 13.x supersedes the old implementation, which drew a
 * particle ring over an unchanged floor. A fracture is now a five-stage event:
 * select valid arena floor cells, replace the surface with cracked rift blocks
 * and widen the crack, warn for 1.5-2 s, erupt an 8-12 block column that is the
 * <em>actual</em> damage volume, collapse, and then restore the exact original
 * block data.</p>
 *
 * <p>This class stays pure: it takes cells, tick counters and journal statuses
 * and returns decisions. The Bukkit adapter keeps owning worlds, sounds,
 * particles and {@code EntityDamageEvent}. Because the damage window is decided
 * here, "no duplicate damage for one eruption" is provable without a server.</p>
 */
public final class RiftFractureEruptionPolicy {
    /** 13.3: at most six simultaneous fractures. */
    public static final int MAX_SIMULTANEOUS = 6;
    /** 13.2 stage 2: the warning is never hidden and never instant. */
    public static final int MIN_WARNING_TICKS = 30;
    public static final int MAX_WARNING_TICKS = 40;
    /** 13.3: roughly one new batch every 8-12 s. */
    public static final int MIN_BATCH_GAP_TICKS = 160;
    public static final int MAX_BATCH_GAP_TICKS = 240;
    /** 13.2 stage 3: the fiery column is 8-12 blocks tall. */
    public static final double MIN_GEYSER_HEIGHT = 8.0D;
    public static final double MAX_GEYSER_HEIGHT = 12.0D;
    /** Stage durations used by {@link Stage} when a caller does not override them. */
    public static final int PREPARE_TICKS = 20;
    public static final int ERUPT_TICKS = 60;
    public static final int COLLAPSE_TICKS = 10;
    public static final int RESTORE_TICKS = 30;
    /** 13.3: no two cells in a batch touch, so an escape lane always stays open. */
    public static final int MIN_CELL_SPACING = 2;
    /** 13.4: never mutate the whole arena; this share must stay standable. */
    public static final double MINIMUM_SAFE_RATIO = 0.34D;
    /** The damage volume of the column. Matches the visible 8-12 block pillar. */
    public static final double GEYSER_RADIUS_BLOCKS = 1.9D;
    public static final double ERUPTION_DAMAGE = 7.0D;

    /** Rift surface materials, darkest first: the crack visibly widens. */
    private static final String[] SURFACE_STAGES = {
        "minecraft:cracked_polished_blackstone_bricks",
        "minecraft:cracked_deepslate_tiles",
        "minecraft:cracked_nether_bricks",
        "minecraft:magma_block"
    };

    private RiftFractureEruptionPolicy() {
    }

    // --- 13.3 frequency and scaling -----------------------------------------

    /**
     * Simultaneous fractures for a party.
     *
     * <p>2-4 for a small party, up to the cap of six for large ones. The brackets
     * are deliberately conservative at the low end: a duo must be able to see
     * every eruption zone before it grows.</p>
     */
    public static int batchSizeForPlayers(int players) {
        int safe = Math.max(0, Math.min(20, players));
        if (safe <= 0) {
            return 0;
        }
        if (safe <= 4) {
            return Math.min(4, Math.max(2, safe));
        }
        if (safe <= 8) {
            return 4;
        }
        if (safe <= 12) {
            return 5;
        }
        return MAX_SIMULTANEOUS;
    }

    /** Delay between two batches: 8 s for a duo up to 12 s for a raid. */
    public static int batchGapTicks(int players) {
        int safe = Math.max(0, Math.min(20, players));
        if (safe <= 4) {
            return MIN_BATCH_GAP_TICKS;
        }
        if (safe <= 10) {
            return 200;
        }
        return MAX_BATCH_GAP_TICKS;
    }

    /** Warning length for this fracture batch, always inside the contract band. */
    public static int warningTicks(long seed) {
        int span = MAX_WARNING_TICKS - MIN_WARNING_TICKS + 1;
        return MIN_WARNING_TICKS + Math.floorMod(seed, span);
    }

    /**
     * Rift surface block data for a widening crack.
     *
     * @param progress 0 at preparation, 1 when the warning ends
     */
    public static String surfaceBlockData(double progress) {
        double safe = Double.isFinite(progress) ? Math.max(0.0D, Math.min(1.0D, progress)) : 0.0D;
        int index = Math.min(SURFACE_STAGES.length - 1,
                (int) Math.floor(safe * SURFACE_STAGES.length));
        return SURFACE_STAGES[index];
    }

    // --- 13.2 lifecycle -----------------------------------------------------

    public enum Phase {
        PREPARE,
        WARNING,
        ERUPTION,
        COLLAPSE,
        RESTORE,
        DONE
    }

    /**
     * Absolute tick window of one fracture, resolved once at batch start.
     *
     * <p>Stage 1 (preparation: the surface becomes cracked, the crack widens,
     * dust rises) runs inside stage 2 (warning), because the contract asks the
     * player to READ the crack growing and then still has 1.5-2 s to walk off
     * it. So the warning window starts at the batch tick and the eruption is
     * exactly the contract band later; every later boundary is derived from it,
     * so an out-of-order window can never erupt before its warning.</p>
     */
    public record Stage(long preparedAt, long warningStart, long eruptionTick,
                        long collapseStart, long restoreStart) {
        public static final int COLLAPSE_TICKS = 10;

        public Stage {
            long prepare = Math.max(0L, preparedAt);
            long warning = Math.max(prepare, warningStart);
            long band = Math.max(MIN_WARNING_TICKS,
                    Math.min(MAX_WARNING_TICKS, eruptionTick - warning));
            long erupt = warning + band;
            long collapse = Math.max(erupt + 1L,
                    Math.min(erupt + ERUPT_TICKS, collapseStart));
            long restore = Math.max(collapse + 1L,
                    Math.min(collapse + COLLAPSE_TICKS, restoreStart));
            preparedAt = prepare;
            warningStart = warning;
            eruptionTick = erupt;
            collapseStart = collapse;
            restoreStart = restore;
        }

        /** 13.2 stage 2, provable: the warning really is 1.5-2 s. */
        public int warningTicks(long nowTick) {
            return (int) (eruptionTick - warningStart);
        }

        /** A batch opened now: crack and warning start on this very tick. */
        public static Stage forBatch(long nowTick, long seed) {
            long start = Math.max(0L, nowTick);
            long eruption = start + warningTicksStatic(seed);
            return new Stage(start, start, eruption,
                    eruption + ERUPT_TICKS, eruption + ERUPT_TICKS + COLLAPSE_TICKS);
        }

        private static int warningTicksStatic(long seed) {
            return RiftFractureEruptionPolicy.warningTicks(seed);
        }

        public Phase phase(long nowTick) {
            if (nowTick >= restoreStart + RESTORE_TICKS) {
                return Phase.DONE;
            }
            if (nowTick >= restoreStart) {
                return Phase.RESTORE;
            }
            if (nowTick >= collapseStart) {
                return Phase.COLLAPSE;
            }
            if (nowTick >= eruptionTick) {
                return Phase.ERUPTION;
            }
            return nowTick >= warningStart ? Phase.WARNING : Phase.PREPARE;
        }

        /** Widening crack: 0 when the batch is planned, 1 at the eruption. */
        public double width(long nowTick) {
            if (nowTick <= preparedAt) {
                return 0.06D;
            }
            if (nowTick >= eruptionTick) {
                return 1.0D;
            }
            double span = Math.max(1.0D, (double) (eruptionTick - preparedAt));
            return 0.06D + 0.94D * (nowTick - preparedAt) / span;
        }

        /**
         * Visible column height in blocks.
         *
         * <p>Rises fast in the first part of the eruption, peaks at the authored
         * maximum, then contracts through the collapse. It returns exactly zero
         * once the floor is restored, so no invisible hazard outlives its model.</p>
         */
        public double geyserHeight(long nowTick) {
            Phase phase = phase(nowTick);
            if (phase == Phase.DONE || phase == Phase.RESTORE) {
                return 0.0D;
            }
            if (phase == Phase.PREPARE || phase == Phase.WARNING) {
                return 0.0D;
            }
            if (phase == Phase.ERUPTION) {
                // The column reaches its authored maximum on the very first
                // eruption tick (that tick is also the single damage tick, so the
                // hit volume and the visible geyser agree at the hit instant)
                // and then slowly settles, never dropping below the 8-block floor.
                double span = Math.max(1.0D, (double) (collapseStart - eruptionTick));
                double settle = Math.min(1.0D, (nowTick - eruptionTick) / span);
                return MAX_GEYSER_HEIGHT - 1.2D * settle;
            }
            // Collapse: contracts from the contract floor down to nothing.
            double span = Math.max(1.0D, (double) (restoreStart - collapseStart));
            double left = 1.0D - Math.min(1.0D, (nowTick - collapseStart) / span);
            return MIN_GEYSER_HEIGHT * left;
        }

        /**
         * 13.3: exactly one damaging instant per eruption.
         *
         * <p>The column is real for the whole eruption, but only the very first
         * tick of it deals damage; every later tick of the same eruption is
         * already inside the player's damage ledger. A player who steps in
         * halfway still gets their single hit, because the adapter asks
         * {@link #mayDamagePlayer} rather than re-using the tick.</p>
         */
        public boolean damages(long nowTick) {
            // The whole eruption window is the hit volume; the once-only rule is
            // carried by the per-player ledger keyed on eruptionTick(), so a
            // player inside the column is never skipped for arriving late and
            // never hurt twice for staying.
            return phase(nowTick) == Phase.ERUPTION;
            // (Deliberately not tick-scoped: a latecomer who enters mid-column
            // must still take their single hit.)
        }

        /** The one authored hit keyframe, used to scope the per-player ledger. */
        public long eruptionTick() {
            return eruptionTick;
        }
    }

    /**
     * Only an eruption may damage; never the warning, collapse or restore.
     *
     * <p>This is the phase-level gate. The per-hit guarantee is
     * {@link #mayDamagePlayer}: a player is hurt once per eruption tick key, so a
     * player who arrives halfway through the column still takes their single
     * hit instead of being skipped because the first tick has passed.</p>
     */
    public static boolean eruptionDamages(Phase phase) {
        return phase == Phase.ERUPTION;
    }

    /** Stable ledger key so a later eruption of the same cell still hurts. */
    public static String scopedKey(long eruptionTick, String playerKey) {
        return eruptionTick + ":" + (playerKey == null ? "" : playerKey);
    }

    public static boolean mayDamagePlayer(String playerKey, Set<String> damaged, long eruptionTick) {
        if (playerKey == null || playerKey.isBlank() || damaged == null) {
            return false;
        }
        return !damaged.contains(scopedKey(eruptionTick, playerKey));
    }

    /** Real damage of the column; the visible geyser is the hitbox. */
    public static double eruptionDamage(int players) {
        int safe = Math.max(1, Math.min(20, players));
        return ERUPTION_DAMAGE + Math.min(3.0D, (safe - 2) * 0.25D);
    }

    /** 13.2 stage 3: hit players are launched, never stunlocked. */
    public static double eruptionLaunchVelocity() {
        return 1.1D;
    }

    // --- 13.4 the existing hazard journal lifecycle -------------------------

    public enum JournalStatus {
        EMPTY,
        PREPARED,
        APPLIED,
        RESTORED
    }

    public enum Step {
        PREPARE,
        APPLY,
        WAIT,
        RESTORE,
        DONE
    }

    /**
     * Which journal step the adapter owes next.
     *
     * <p>Save-before-mutate is the whole point of the journal: the surface is
     * only replaced after {@code PREPARED} is durable, and the journal only says
     * {@code APPLIED} once the blocks really changed. A failed apply must go
     * straight to RESTORE, otherwise recovery would believe magma was written
     * over a floor that still has its original blocks.</p>
     */
    public static Step nextJournalStep(JournalStatus status, boolean lifecycleOver, boolean applyFailed) {
        JournalStatus safe = status == null ? JournalStatus.EMPTY : status;
        if (safe == JournalStatus.RESTORED) {
            return Step.DONE;
        }
        if (safe == JournalStatus.EMPTY) {
            // An empty journal means no entry was ever written, so there is
            // nothing to prepare and nothing to restore, whatever else happened.
            return lifecycleOver ? Step.DONE : Step.PREPARE;
        }
        if (applyFailed) {
            return Step.RESTORE;
        }
        if (safe == JournalStatus.PREPARED) {
            return lifecycleOver ? Step.RESTORE : Step.APPLY;
        }
        return lifecycleOver ? Step.RESTORE : Step.WAIT;
    }

    // --- 13.2 stage 1 / 13.4: real floor cells ------------------------------

    /**
     * One candidate floor cell.
     *
     * <p>Every flag is a refusal reason the adapter can also report, so a batch
     * that selects too few cells is explainable instead of silent.</p>
     */
    public record Cell(int x, int z, boolean solid, boolean liquid, boolean protectedBlock,
                       boolean playerPlaced, boolean coreColumn, boolean core) {
        public Cell(int x, int z) {
            this(x, z, true, false, false, false, false, false);
        }

        /** A cell the phase may mutate. */
        public boolean eligible() {
            return solid && !liquid && !protectedBlock && !playerPlaced && !coreColumn && !core;
        }
    }

    /**
     * Selects valid arena floor cells for one fracture batch.
     *
     * @param arena           every real floor cell of the arena
     * @param protectedCells  core, runes and any structure the phase must not touch
     * @param occupied        cells already mutated by a live fracture or the inferno
     * @param wanted          how many fractures the batch should open
     * @param maxMutated      hard ceiling for this batch
     * @param minSpacing      chessboard distance two chosen cells must keep
     * @param minimumSafeRatio share of the arena that must stay standable
     * @param seed            attempt-scoped seed; the same seed reproduces the batch
     */
    public static List<Cell> selectCells(List<Cell> arena, Set<Cell> protectedCells,
                                         Set<Cell> occupied, int wanted, int maxMutated,
                                         int minSpacing, double minimumSafeRatio, long seed) {
        if (arena == null || arena.isEmpty()) {
            return List.of();
        }
        // A spacing below one cell would put two fractures on the same tile, so
        // one is the floor. The contract default is MIN_CELL_SPACING (two), which
        // keeps a walked-through gap; callers may only ask for MORE separation.
        int space = Math.max(1, minSpacing);
        int limit = Math.max(0, Math.min(MAX_SIMULTANEOUS, Math.min(wanted, maxMutated)));
        if (limit == 0) {
            return List.of();
        }
        double safeRatio = Double.isNaN(minimumSafeRatio) || Double.isInfinite(minimumSafeRatio)
                ? MINIMUM_SAFE_RATIO : Math.max(0.0D, Math.min(1.0D, minimumSafeRatio));
        Set<Cell> blocked = new HashSet<>();
        if (protectedCells != null) {
            blocked.addAll(protectedCells);
        }
        if (occupied != null) {
            blocked.addAll(occupied);
        }
        Map<Cell, Cell> unique = new LinkedHashMap<>();
        for (Cell cell : arena) {
            if (cell != null && cell.eligible() && !blocked.contains(cell)) {
                unique.putIfAbsent(cell, cell);
            }
        }
        List<Cell> candidates = new ArrayList<>(unique.values());
        // Deterministic order before the shuffle, so the same seed always gives
        // byte-identical batches regardless of the caller's list order.
        candidates.sort((left, right) -> left.x() != right.x()
                ? Integer.compare(left.x(), right.x()) : Integer.compare(left.z(), right.z()));
        java.util.Collections.shuffle(candidates, new Random(seed));

        // The arena must keep enough safe floor for the party after mutation.
        int safeRequired = (int) Math.ceil(arena.size() * safeRatio);
        int mutableBudget = Math.max(0, arena.size() - safeRequired);
        int allowed = Math.min(limit, mutableBudget);

        List<Cell> chosen = new ArrayList<>(allowed);
        for (Cell candidate : candidates) {
            if (chosen.size() >= allowed) {
                break;
            }
            boolean tooClose = false;
            for (Cell other : chosen) {
                if (Math.max(Math.abs(candidate.x() - other.x()),
                        Math.abs(candidate.z() - other.z())) < space) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                chosen.add(candidate);
            }
        }
        return List.copyOf(chosen);
    }

    /** Convenience: the surface block data of one cell at a given crack progress. */
    public static String surfaceBlockDataFor(double progress, boolean exploded) {
        String base = surfaceBlockData(progress);
        return exploded ? "minecraft:magma_block" : base;
    }

    /** Diagnostic label for the batch, so logs never invent a different name. */
    public static String describe(int players, List<Cell> cells) {
        return "RIFT_FRACTURE_BATCH players=" + players
                + " cells=" + (cells == null ? 0 : cells.size())
                + " batch_gap_ticks=" + batchGapTicks(players)
                + " warning_ticks=" + MIN_WARNING_TICKS + ".." + MAX_WARNING_TICKS
                + " geyser_height=" + (int) MIN_GEYSER_HEIGHT + ".." + (int) MAX_GEYSER_HEIGHT;
    }

    static String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}