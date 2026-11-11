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
 * Phase 4 (OVERLOAD) — the authored full-arena inferno.
 *
 * <p>Master contract 14.x supersedes "a handful of randomly placed magma
 * blocks": the whole arena must read as catastrophically on fire, while still
 * leaving visibly cold ground the party can walk on. Two fairness rules are
 * therefore first-class here, not comments in an adapter:</p>
 *
 * <ul>
 *   <li>a safe cell must be <em>visibly</em> safe — a different block material,
 *       never "identical burning texture with an invisible flag";</li>
 *   <li>the environment never deals unavoidable global damage every tick. Only
 *       standing on burning ground, on the authored cadence, costs health.</li>
 * </ul>
 */
public final class InfernoArenaLifecyclePolicy {
    /** 14.4: the baseline cast is about twenty seconds. */
    public static final int BASELINE_DURATION_TICKS = 400;
    public static final int MIN_DURATION_TICKS = 20;
    public static final int MAX_DURATION_TICKS = 1200;
    /** 14.2: the heating wave sweeps from the centre to the edge. */
    public static final int DEFAULT_HEATING_TICKS = 60;
    /** 14.3: exposure is bounded, never a permanent burn. */
    public static final int DAMAGE_INTERVAL_TICKS = 20;
    public static final int IGNITE_TICKS = 40;
    public static final double DAMAGE_PER_INTERVAL = 6.0D;
    /** 14.4: a new cast needs a real cooldown, not an immediate re-cast. */
    public static final int RESTART_COOLDOWN_TICKS = 200;
    /** Cooling tail during which the floor is already restoring. */
    public static final int COOLING_TICKS = 60;
    /** 14.3: bounded simultaneous geysers. */
    public static final int MAX_VENTS = 4;
    public static final int VENT_PERIOD_TICKS = 60;
    public static final int VENT_WARNING_TICKS = 16;
    public static final int VENT_RECOVER_TICKS = 10;
    /** 14.3: the arena keeps visible cold patches. */
    public static final int MAX_SAFE_CELLS = 12;
    public static final int MIN_SAFE_CELLS = 4;
    /** 14.3: safe patches must be machine-recognisable as cold. */
    private static final String BURNING_SURFACE = "minecraft:magma_block";
    private static final String SAFE_SURFACE = "minecraft:polished_deepslate";
    private static final String[] SAFE_SURFACES = {
        "minecraft:polished_deepslate",
        "minecraft:polished_blackstone_bricks",
        "minecraft:cracked_polished_blackstone_bricks",
        "minecraft:smooth_basalt"
    };

    private InfernoArenaLifecyclePolicy() {
    }

    // --- 14.4 duration ------------------------------------------------------

    /**
     * Cast length from the existing configurable contract.
     *
     * @param configuredSeconds the operator's configured duration in seconds
     * @param players           admitted party size (scaling hook)
     */
    public static int durationTicks(int configuredSeconds, int players) {
        int seconds = configuredSeconds;
        if (seconds <= 0) {
            return MIN_DURATION_TICKS;
        }
        // A raid does not face a longer firestorm than a duo: the inferno is an
        // environment event, not a scaling damage ability. Party size only
        // widens the safe patches and the number of geysers.
        long ticks = Math.min(MAX_DURATION_TICKS, (long) seconds * 20L);
        return (int) Math.max(MIN_DURATION_TICKS, ticks);
    }

    // --- 14.2 the heating wave ---------------------------------------------

    /** Tick on which a cell at {@code normalisedRadius} starts to burn. */
    public static long heatReachedTick(double normalisedRadius, int heatingTicks) {
        double radius = normalisedRadius;
        if (Double.isNaN(radius) || Double.isInfinite(radius)) {
            radius = 1.0D;
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, radius));
        int span = Math.max(1, heatingTicks);
        return Math.round(clamped * span);
    }

    // --- 14.3 surfaces and visible safety -----------------------------------

    public static String burningSurfaceBlock() {
        return BURNING_SURFACE;
    }

    public static String safeSurfaceBlock() {
        return SAFE_SURFACE;
    }

    /**
     * 14.3: safe ground must LOOK safe.
     *
     * <p>The contract explicitly forbids "invisible safe cells inside identical
     * burning textures", so a cell only counts as visibly safe when its block
     * material really differs from the burning material. A difference in block
     * <em>state</em> alone is not enough: nobody can see a waterlogged flag.</p>
     */
    public static boolean isVisiblySafe(String safeBlockData, String burningBlockData) {
        if (safeBlockData == null || safeBlockData.isBlank()
                || burningBlockData == null || burningBlockData.isBlank()) {
            return false;
        }
        String safeMaterial = materialOf(safeBlockData);
        String burningMaterial = materialOf(burningBlockData);
        if (safeMaterial.equals(burningMaterial)) {
            return false;
        }
        // A hot material is never a safe surface even if the burning cell was
        // some other hot block.
        return !isHotMaterial(safeMaterial);
    }

    private static String materialOf(String blockData) {
        String value = blockData.trim().toLowerCase(Locale.ROOT);
        int bracket = value.indexOf('[');
        return bracket >= 0 ? value.substring(0, bracket) : value;
    }

    private static boolean isHotMaterial(String material) {
        return material.contains("lava") || material.contains("magma")
                || material.contains("fire") || material.contains("fire_coral");
    }

    // --- 14.3 safe cells ----------------------------------------------------

    /** One arena floor cell; the refusal flags mirror the adapter's checks. */
    public record Cell(int x, int z, boolean solid, boolean liquid,
                       boolean protectedBlock, boolean playerPlaced, boolean core) {
        public Cell(int x, int z) {
            this(x, z, true, false, false, false, false);
        }

        public boolean standable() {
            return solid && !liquid && !protectedBlock && !playerPlaced;
        }
    }

    /**
     * Cold patches large enough to walk to, relocated every inferno cycle.
     *
     * @param arena       every real floor cell
     * @param protectedCells core and rune cells that are never a refuge
     * @param maxSafe     hard ceiling on safe patches
     * @param wanted      requested patch count for this party
     * @param minSpacing  chessboard distance two patches must keep
     * @param cycleSeed   cycle-scoped seed, so a re-cast moves the patches
     */
    public static List<Cell> safeCells(List<Cell> arena, Set<Cell> protectedCells,
                                       int maxSafe, int wanted, int minSpacing, long cycleSeed) {
        if (arena == null || arena.isEmpty()) {
            return List.of();
        }
        int ceiling = Math.max(0, Math.min(MAX_SAFE_CELLS, maxSafe));
        int target = Math.min(ceiling, Math.max(0, wanted));
        if (target == 0) {
            return List.of();
        }
        int space = Math.max(1, minSpacing);
        Set<Cell> blocked = protectedCells == null ? Set.of() : new HashSet<>(protectedCells);
        Map<Cell, Cell> unique = new LinkedHashMap<>();
        for (Cell cell : arena) {
            if (cell != null && cell.standable() && !blocked.contains(cell)) {
                unique.putIfAbsent(cell, cell);
            }
        }
        List<Cell> candidates = new ArrayList<>(unique.values());
        candidates.sort((left, right) -> left.x() != right.x()
                ? Integer.compare(left.x(), right.x()) : Integer.compare(left.z(), right.z()));
        java.util.Collections.shuffle(candidates, new Random(cycleSeed));
        List<Cell> chosen = new ArrayList<>(target);
        for (Cell candidate : candidates) {
            if (chosen.size() >= target) {
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

    /** Patch count for a party: a duo gets the floor, a raid gets a few more. */
    public static int safeCellCountForPlayers(int players) {
        int safe = Math.max(0, Math.min(20, players));
        if (safe == 0) {
            return 0;
        }
        if (safe <= 2) {
            return MIN_SAFE_CELLS;
        }
        if (safe <= 5) {
            return 6;
        }
        if (safe <= 10) {
            return 8;
        }
        return MAX_SAFE_CELLS;
    }

    /** The cold surface used for the {@code index}-th patch: never all identical. */
    public static String safeSurfaceFor(int index) {
        return SAFE_SURFACES[Math.floorMod(index, SAFE_SURFACES.length)];
    }

    // --- 14.3 damage fairness ----------------------------------------------

    /**
     * Environmental damage for one tick.
     *
     * <p>Returns zero unless the player is physically on a burning cell, and
     * even then only on the authored cadence. Standing on a cold patch is always
     * safe, so there is no unavoidable global damage and the visible safe ground
     * really is safe.</p>
     */
    public static double environmentDamage(long castTick, boolean onBurningCell,
                                           boolean onSafeCell, long lastHitTick) {
        if (castTick < 0L || !onBurningCell || onSafeCell) {
            return 0.0D;
        }
        if (Math.floorMod(castTick, DAMAGE_INTERVAL_TICKS) != 0L) {
            return 0.0D;
        }
        if (lastHitTick >= 0L && castTick - lastHitTick < DAMAGE_INTERVAL_TICKS) {
            return 0.0D;
        }
        return DAMAGE_PER_INTERVAL;
    }

    /** 14.3: the inferno must not stack an unavoidable immobilisation. */
    public static boolean allowsConcurrentSpell(String spell) {
        if (spell == null || spell.isBlank()) {
            return false;
        }
        String upper = spell.trim().toUpperCase(Locale.ROOT);
        return !upper.equals("VOID_MARK") && !upper.equals("AMBUSH_GRAB")
                && !upper.equals("ARENA_INFERNO");
    }

    // --- 14.4 lifecycle -----------------------------------------------------

    public enum Phase {
        PREPARE,
        HEATING,
        BURNING,
        COOLING,
        ENDED
    }

    public enum VentPhase {
        WARN,
        ERUPT,
        RECOVER,
        REMOVE
    }

    /**
     * One inferno cast.
     *
     * @param durationTicks  full cast length
     * @param prepareTicks   authored ground-impact preparation, never damaging
     * @param heatingTicks   the outward heat sweep
     * @param burningTicks   how long the full inferno lasts after the sweep
     */
    public record Stage(int durationTicks, int prepareTicks, int heatingTicks, int burningTicks) {
        public Stage {
            durationTicks = Math.max(MIN_DURATION_TICKS,
                    Math.min(MAX_DURATION_TICKS, durationTicks));
            prepareTicks = Math.max(0, prepareTicks);
            heatingTicks = Math.max(1, heatingTicks);
            burningTicks = Math.max(1, burningTicks);
        }

        private long burningStart() {
            return (long) prepareTicks + heatingTicks;
        }

        private long burningEnd() {
            return Math.min(durationTicks, burningStart() + burningTicks);
        }

        public Phase phase(long tick) {
            if (tick < 0L) {
                return Phase.PREPARE;
            }
            if (tick < prepareTicks) {
                return Phase.PREPARE;
            }
            if (tick < burningStart()) {
                return Phase.HEATING;
            }
            if (tick < burningEnd()) {
                return Phase.BURNING;
            }
            if (tick < durationTicks + COOLING_TICKS) {
                return Phase.COOLING;
            }
            return Phase.ENDED;
        }

        /** Only the live inferno may damage the floor cells. */
        public boolean damages(long tick) {
            Phase current = phase(tick);
            return current == Phase.HEATING || current == Phase.BURNING;
        }

        /** The restore happens once, at the very end, from the exact originals. */
        public boolean shouldRestore(long tick) {
            return phase(tick) == Phase.COOLING || phase(tick) == Phase.ENDED;
        }

        /** Heating progress for a cell at the given normalised radius. */
        public double heatProgress(double normalisedRadius, long tick, int heatingTicks) {
            if (tick < prepareTicks) {
                return 0.0D;
            }
            long start = heatReachedTick(normalisedRadius, heatingTicks) + prepareTicks;
            long span = Math.max(1L, heatingTicks);
            return Math.max(0.0D, Math.min(1.0D, (tick - start + span) / (double) span));
        }
    }

    /** Heat is fully applied at the centre from the very first tick. */
    public static double heatAt(double normalisedRadius, long tick) {
        double radius = Double.isNaN(normalisedRadius) ? 1.0D
                : Math.max(0.0D, Math.min(1.0D, normalisedRadius));
        if (radius == 0.0D) {
            return 1.0D;
        }
        return radius < 0.0D || tick < 0L ? 0.0D : radius > 0.0D ? 1.0D - radius : 1.0D;
    }

    /** A cast may only start when nothing is burning and the cooldown elapsed. */
    public static boolean mayRestart(long nowTick, long lastCastStartTick, long lastCastEndTick) {
        if (nowTick < 0L || lastCastStartTick < 0L) {
            return true;
        }
        // Never restart inside a live cast.
        if (lastCastEndTick >= 0L && nowTick < lastCastEndTick) {
            return false;
        }
        long reference = Math.max(lastCastStartTick, lastCastEndTick);
        return nowTick - reference >= RESTART_COOLDOWN_TICKS;
    }

    // --- vents --------------------------------------------------------------

    public static VentPhase ventPhase(int castTick) {
        int slot = Math.floorMod(castTick, VENT_PERIOD_TICKS);
        if (slot < VENT_WARNING_TICKS) {
            return VentPhase.WARN;
        }
        if (slot == VENT_WARNING_TICKS) {
            return VentPhase.ERUPT;
        }
        if (slot <= VENT_WARNING_TICKS + VENT_RECOVER_TICKS) {
            return VentPhase.RECOVER;
        }
        return VentPhase.REMOVE;
    }

    public static boolean ventDamages(VentPhase phase) {
        return phase == VentPhase.ERUPT;
    }

    public static int ventCountForPlayers(int players) {
        int safe = Math.max(0, Math.min(20, players));
        return Math.max(1, Math.min(MAX_VENTS, (safe + 4) / 5));
    }

    /** Diagnostic label; logs must never invent a different name. */
    public static String describe(int players) {
        return "OVERLOAD_INFERNO players=" + players
                + " duration_ticks=" + BASELINE_DURATION_TICKS
                + " safe_cells=" + safeCellCountForPlayers(players)
                + " vents=" + ventCountForPlayers(players)
                + " damage_interval_ticks=" + DAMAGE_INTERVAL_TICKS
                + " burning=" + burningSurfaceBlock() + " safe=" + safeSurfaceBlock();
    }
}