package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Phase 4 (OVERLOAD) cold-patch decay.
 *
 * <p>{@link InfernoArenaLifecyclePolicy} states the fairness floor: the arena
 * keeps visibly cold ground. This policy adds the owner request that the fight
 * <em>drags</em>: the longer it runs (the lower the Guardian's health fraction),
 * the more often the cold patches go out and come back, and the smaller each
 * patch becomes - but the party is never left without a refuge.</p>
 *
 * <p>Two guarantees are first-class and therefore tested, not assumed:</p>
 * <ul>
 *   <li><b>Always present.</b> At every health fraction the patch count stays
 *       at or above {@link #MIN_PATCHES}; a near-dead Guardian never removes
 *       every refuge.</li>
 *   <li><b>Reachable.</b> The patches are chosen so that a walking player is
 *       never further than {@link #MAX_SAFE_SPACING_BLOCKS} blocks (Chebyshev)
 *       from one of them, so the fight cannot become an unfair lava sprint.</li>
 * </ul>
 */
public final class InfernoDecayPolicy {
    /** A cold patch lives at least five seconds before it may relocate. */
    public static final int MIN_SAFE_LIFETIME_TICKS = 100;
    /** Reachability: every walkable cell sits within this Chebyshev range of a patch. */
    public static final int MAX_SAFE_SPACING_BLOCKS = 7;
    /** Generous at full health, lean at the last sliver, but never empty. */
    public static final int MAX_PATCHES = 12;
    public static final int MIN_PATCHES = 3;
    /** Relocation cadence at the healthiest. */
    public static final int FULL_HEALTH_RELOCATE_TICKS = 160;
    /** Relocation cadence at the last sliver: faster cold/death churn. */
    public static final int LOW_HEALTH_RELOCATE_TICKS = MIN_SAFE_LIFETIME_TICKS;
    /** Patch radius (Chebyshev) at the healthiest and at the last sliver. */
    public static final int FULL_HEALTH_PATCH_RADIUS = 2;
    public static final int LOW_HEALTH_PATCH_RADIUS = 1;

    private InfernoDecayPolicy() {
    }

    /** Clamps any health input to a real 0..1 fraction. */
    public static double clampFraction(double hpFraction) {
        if (!Double.isFinite(hpFraction)) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, hpFraction));
    }

    /**
     * How many cold patches the arena keeps at this health fraction.
     *
     * <p>Fewer as the fight drags, matching "smaller patches", but floored so
     * there is always a refuge.</p>
     */
    public static int safePatchCount(double hpFraction, int players) {
        double fraction = clampFraction(hpFraction);
        int party = Math.max(1, Math.min(20, players));
        int partyDemand = InfernoArenaLifecyclePolicy.safeCellCountForPlayers(party);
        // Decay multiplier: 1.0 at full health down to ~0.4 at the last sliver.
        double decay = 0.4D + 0.6D * fraction;
        int scaled = (int) Math.round(partyDemand * decay);
        return Math.max(MIN_PATCHES, Math.min(MAX_PATCHES, scaled));
    }

    /**
     * Ticks between cold-patch relocations. The lower the health, the shorter
     * the interval (more frequent extinguish + new spawn), but never below the
     * five-second minimum lifetime.
     */
    public static int relocateIntervalTicks(double hpFraction) {
        double fraction = clampFraction(hpFraction);
        int span = FULL_HEALTH_RELOCATE_TICKS - LOW_HEALTH_RELOCATE_TICKS;
        int interval = LOW_HEALTH_RELOCATE_TICKS + (int) Math.round(span * fraction);
        return Math.max(MIN_SAFE_LIFETIME_TICKS, interval);
    }

    /** Chebyshev radius of one cold patch: shrinks as the fight drags. */
    public static int patchRadius(double hpFraction) {
        double fraction = clampFraction(hpFraction);
        return fraction >= 0.5D ? FULL_HEALTH_PATCH_RADIUS : LOW_HEALTH_PATCH_RADIUS;
    }

    /** The single unconditional promise: a refuge always exists. */
    public static boolean alwaysPresent(double hpFraction) {
        return safePatchCount(hpFraction, 1) >= MIN_PATCHES;
    }

    // --- reachable coverage -------------------------------------------------

    /** Chebyshev distance, the metric the party actually walks. */
    public static int chebyshev(int ax, int az, int bx, int bz) {
        return Math.max(Math.abs(ax - bx), Math.abs(az - bz));
    }

    /** True when at least one cold patch is within {@code maxGap} of the cell. */
    public static boolean covered(int x, int z, List<InfernoArenaLifecyclePolicy.Cell> safe, int maxGap) {
        if (safe == null || safe.isEmpty()) {
            return false;
        }
        int gap = Math.max(0, maxGap);
        for (InfernoArenaLifecyclePolicy.Cell patch : safe) {
            if (patch != null && chebyshev(x, z, patch.x(), patch.z()) <= gap) {
                return true;
            }
        }
        return false;
    }

    /** True when every standable floor cell is within {@code maxGap} of a patch. */
    public static boolean coverageSatisfied(List<InfernoArenaLifecyclePolicy.Cell> arena,
                                            List<InfernoArenaLifecyclePolicy.Cell> safe,
                                            int maxGap) {
        if (arena == null || arena.isEmpty()) {
            return true;
        }
        for (InfernoArenaLifecyclePolicy.Cell cell : arena) {
            if (cell == null || !cell.standable()) {
                continue;
            }
            if (!covered(cell.x(), cell.z(), safe, maxGap)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Greedy reachable coverage: pick the fewest standable, unprotected cells so
     * every walkable cell lies within {@code maxGap} of one of them.
     *
     * <p>This differs from {@link InfernoArenaLifecyclePolicy#safeCells} on
     * purpose. That method keeps patches a minimum distance <em>apart</em>; this
     * one keeps them a maximum distance <em>reachable</em>. The owner asked for
     * both, so both exist and neither is folded into the other.</p>
     *
     * @param arena          every real floor cell
     * @param protectedCells core / rune / lane cells that are never a refuge
     * @param maxPatches     hard ceiling on the returned patches
     * @param maxGap         reachability radius in Chebyshev blocks
     * @param seed           cast-scoped seed, so a re-cast moves the patches
     */
    public static List<InfernoArenaLifecyclePolicy.Cell> selectCovering(
            List<InfernoArenaLifecyclePolicy.Cell> arena,
            Set<InfernoArenaLifecyclePolicy.Cell> protectedCells,
            int maxPatches, int maxGap, long seed) {
        if (arena == null || arena.isEmpty() || maxPatches <= 0) {
            return List.of();
        }
        int cap = Math.max(1, maxPatches);
        int gap = Math.max(0, maxGap);
        Set<InfernoArenaLifecyclePolicy.Cell> blocked =
                protectedCells == null ? Set.of() : new HashSet<>(protectedCells);
        Map<InfernoArenaLifecyclePolicy.Cell, InfernoArenaLifecyclePolicy.Cell> unique = new LinkedHashMap<>();
        List<InfernoArenaLifecyclePolicy.Cell> walkable = new ArrayList<>();
        for (InfernoArenaLifecyclePolicy.Cell cell : arena) {
            if (cell == null) {
                continue;
            }
            if (cell.standable()) {
                walkable.add(cell);
                if (!blocked.contains(cell)) {
                    unique.putIfAbsent(cell, cell);
                }
            }
        }
        if (walkable.isEmpty()) {
            return List.of();
        }
        List<InfernoArenaLifecyclePolicy.Cell> candidates = new ArrayList<>(unique.values());
        candidates.sort((left, right) -> left.x() != right.x()
                ? Integer.compare(left.x(), right.x()) : Integer.compare(left.z(), right.z()));
        java.util.Collections.shuffle(candidates, new Random(seed));
        boolean[] reached = new boolean[walkable.size()];
        List<InfernoArenaLifecyclePolicy.Cell> chosen = new ArrayList<>(cap);
        // One starting patch: the highest-coverage candidate, so even a cap of 1
        // leaves the best possible centre.
        pickBest(candidates, walkable, reached, gap, chosen, seed);
        while (chosen.size() < cap && uncovered(reached)) {
            if (!pickBest(candidates, walkable, reached, gap, chosen, seed)) {
                break;
            }
        }
        return List.copyOf(chosen);
    }

    private static boolean pickBest(List<InfernoArenaLifecyclePolicy.Cell> candidates,
                                    List<InfernoArenaLifecyclePolicy.Cell> walkable,
                                    boolean[] reached, int gap,
                                    List<InfernoArenaLifecyclePolicy.Cell> chosen, long seed) {
        InfernoArenaLifecyclePolicy.Cell best = null;
        int bestScore = -1;
        int bestIndex = -1;
        for (int index = 0; index < candidates.size(); index++) {
            InfernoArenaLifecyclePolicy.Cell candidate = candidates.get(index);
            if (candidate == null || chosen.contains(candidate)) {
                continue;
            }
            int score = 0;
            for (int cell = 0; cell < walkable.size(); cell++) {
                if (reached[cell]) {
                    continue;
                }
                if (chebyshev(candidate.x(), candidate.z(),
                        walkable.get(cell).x(), walkable.get(cell).z()) <= gap) {
                    score++;
                }
            }
            // A later shuffle-ordered candidate only wins on a strictly better
            // score, so the seed really changes the result when scores tie.
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                bestIndex = index;
            }
        }
        if (best == null || bestScore <= 0) {
            return false;
        }
        chosen.add(best);
        for (int cell = 0; cell < walkable.size(); cell++) {
            if (!reached[cell]
                    && chebyshev(best.x(), best.z(),
                    walkable.get(cell).x(), walkable.get(cell).z()) <= gap) {
                reached[cell] = true;
            }
        }
        // Demote the winner so a second pass cannot reselect it.
        candidates.set(bestIndex, null);
        return true;
    }

    private static boolean uncovered(boolean[] reached) {
        for (boolean value : reached) {
            if (!value) {
                return true;
            }
        }
        return false;
    }

    /** Diagnostic label; logs must never invent a different name. */
    public static String describe(double hpFraction, int players) {
        return "PHASE4_INFERNO_DECAY hp_fraction=" + String.format(java.util.Locale.ROOT, "%.3f",
                clampFraction(hpFraction))
                + " players=" + players
                + " patches=" + safePatchCount(hpFraction, players)
                + " relocate_ticks=" + relocateIntervalTicks(hpFraction)
                + " patch_radius=" + patchRadius(hpFraction)
                + " max_gap=" + MAX_SAFE_SPACING_BLOCKS
                + " min_lifetime_ticks=" + MIN_SAFE_LIFETIME_TICKS;
    }
}
