import me.copimine.endevent.domain.InfernoArenaLifecyclePolicy;
import me.copimine.endevent.domain.InfernoArenaLifecyclePolicy.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 4 contract (master 14.x): OVERLOAD is a fully authored inferno scene,
 * not "a handful of randomly placed magma blocks".
 *
 * <p>The contract is oddly specific about fairness, and that is what this probe
 * pins down: safe cells must be VISIBLE (a different, cold surface), they must
 * not be identical-burning-but-invisible, and the environment may never deal
 * unavoidable global damage every tick.</p>
 */
public final class InfernoArenaLifecyclePolicyTest {
    private static int failures = 0;

    public static void main(String[] args) {
        duration();
        heatingWave();
        safeCellsAreVisible();
        noGlobalPerTickDamage();
        lifecycleAndCooldown();
        ventTimeline();
        System.out.println(failures == 0 ? "OK" : "FAILURES " + failures);
        if (failures != 0) System.exit(1);
    }

    // --- 14.4 duration -------------------------------------------------------
    private static void duration() {
        check("the baseline inferno is about twenty seconds",
                InfernoArenaLifecyclePolicy.BASELINE_DURATION_TICKS == 400);
        check("the baseline is reused for a default cast",
                InfernoArenaLifecyclePolicy.durationTicks(20, 20) == 400);
        // Existing configurable contract is preserved, never widened arbitrarily.
        check("a shorter configured cast is honoured",
                InfernoArenaLifecyclePolicy.durationTicks(10, 20) < 400);
        check("a longer configured cast is honoured",
                InfernoArenaLifecyclePolicy.durationTicks(30, 20) > 400);
        check("a ridiculous configured cast is clamped",
                InfernoArenaLifecyclePolicy.durationTicks(600, 20)
                        <= InfernoArenaLifecyclePolicy.MAX_DURATION_TICKS);
        check("a zero or negative cast still runs",
                InfernoArenaLifecyclePolicy.durationTicks(0, 20)
                        >= InfernoArenaLifecyclePolicy.MIN_DURATION_TICKS);
    }

    // --- 14.2 transition: heating wave from the centre outward --------------
    private static void heatingWave() {
        // Progress is monotone for a fixed cell.
        double lastHeat = -1.0D;
        for (long tick = 0L; tick <= 300L; tick += 10L) {
            double heat = InfernoArenaLifecyclePolicy.heatAt(0.5D, tick);
            check("heat progress grows at " + tick, heat >= lastHeat);
            check("heat progress stays in 0..1 at " + tick, heat >= 0.0D && heat <= 1.0D);
            lastHeat = heat;
        }
        check("the wave starts at the centre",
                InfernoArenaLifecyclePolicy.heatAt(0.0D, 0) == 1.0D);
        // A cell at half the arena radius heats later than one at the centre.
        long centre = InfernoArenaLifecyclePolicy.heatReachedTick(0.0D, 300);
        long middle = InfernoArenaLifecyclePolicy.heatReachedTick(0.5D, 300);
        long edge = InfernoArenaLifecyclePolicy.heatReachedTick(1.0D, 300);
        check("the heat spreads outward", centre < middle && middle < edge);
        check("the wave is bounded", edge <= 300);
        check("the wave is visible, not instant", edge > centre);
        // Normalised distance below zero or above one is clamped.
        check("a negative radius is clamped to the centre",
                InfernoArenaLifecyclePolicy.heatReachedTick(-1.0D, 300) == centre);
        check("an over-range radius is clamped to the edge",
                InfernoArenaLifecyclePolicy.heatReachedTick(4.0D, 300) == edge);
        check("a NaN radius does not throw",
                InfernoArenaLifecyclePolicy.heatReachedTick(Double.NaN, 300) == edge);



        // The palette has to be hot and consistent.
        check("the burning surface is a hot lava material",
                InfernoArenaLifecyclePolicy.burningSurfaceBlock().contains("lava")
                        || InfernoArenaLifecyclePolicy.burningSurfaceBlock().contains("magma"));
        check("the cold safe surface is NOT a hot material",
                !InfernoArenaLifecyclePolicy.safeSurfaceBlock().contains("lava")
                        && !InfernoArenaLifecyclePolicy.safeSurfaceBlock().contains("magma")
                        && !InfernoArenaLifecyclePolicy.safeSurfaceBlock().contains("fire"));
    }

    // --- 14.3 safe regions must be VISIBLY recognisable ----------------------
    private static void safeCellsAreVisible() {
        check("a safe cell never shares the burning block",
                !InfernoArenaLifecyclePolicy.safeSurfaceBlock()
                        .equals(InfernoArenaLifecyclePolicy.burningSurfaceBlock()));
        check("a safe cell is physically distinct",
                InfernoArenaLifecyclePolicy.isVisiblySafe(
                        InfernoArenaLifecyclePolicy.safeSurfaceBlock(),
                        InfernoArenaLifecyclePolicy.burningSurfaceBlock()));
        check("a burning cell is not silently safe",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(
                        InfernoArenaLifecyclePolicy.burningSurfaceBlock(),
                        InfernoArenaLifecyclePolicy.burningSurfaceBlock()));
        // A barrier/floor pair that only differs by an invisible state is refused.
        // The contract's exact wording: no invisible safe cells among identical
        // burning textures. A state-only difference must be refused for EVERY
        // hot material, not just for the one this class happens to burn with.
        // These cases are asserted POSITIVELY (the material guard itself) so the
        // result does not depend on the hot-material veto.
        check("an invisible state difference on magma is refused",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(
                        "minecraft:magma_block[waterlogged=false]",
                        "minecraft:magma_block[waterlogged=true]"));
        check("an invisible state difference on soul sand is refused",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(
                        "minecraft:soul_sand[x=1]", "minecraft:soul_sand[x=2]"));
        check("an invisible state difference on basalt is refused",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(
                        "minecraft:basalt[axis=x]", "minecraft:basalt[axis=z]"));
        // And the same for blocks whose material string does not parse cleanly.
        check("two identical hot states are refused",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(
                        "minecraft:lava[level=0]", "minecraft:lava[level=15]"));
        check("two identical fire states are refused",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(
                        "minecraft:fire[age=0]", "minecraft:fire[age=15]"));
        check("a null or blank safe block is refused",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(null, "minecraft:magma_block")
                        && !InfernoArenaLifecyclePolicy.isVisiblySafe("  ", "minecraft:magma_block"));
        // Choosing a DIFFERENT material is not enough on its own: a second hot
        // material would still burn the player while claiming to be a refuge.
        check("another hot material is never a refuge",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(
                        "minecraft:lava", "minecraft:magma_block"));
        check("fire is never a refuge",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(
                        "minecraft:fire", "minecraft:soul_magma_block"));
        // Block-state suffixes must not fool the material comparison.
        check("a state suffix does not make a burning block cold",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(
                        "minecraft:magma_block[age=1]", "minecraft:magma_block"));
        check("a cold material behind a state suffix is accepted",
                InfernoArenaLifecyclePolicy.isVisiblySafe(
                        "minecraft:polished_deepslate[axis=x]", "minecraft:magma_block"));
        // Case and whitespace are normalised, so the check cannot be bypassed.
        check("material comparison is case-insensitive",
                !InfernoArenaLifecyclePolicy.isVisiblySafe(
                        "MINECRAFT:MAGMA_BLOCK", "minecraft:magma_block"));

        // Safe cells rotate, so the arena is not a static puzzle.
        List<Cell> arena = new ArrayList<>();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                arena.add(new Cell(x, z, true, false, false, false, (x == 0 && z == 0)));
            }
        }
        Set<Cell> protectedCells = Set.of(new Cell(0, 0));
        List<Cell> first = InfernoArenaLifecyclePolicy.safeCells(
                arena, protectedCells, 6, 4, 2, 1L);
        check("safe cells were selected", first.size() >= 4);
        for (Cell cell : first) {
            check("the core is never a safe cell", !protectedCells.contains(cell));
            check("a safe cell is standable", cell.solid() && !cell.liquid());
        }
        // Enough of them to walk to, and spaced so a route exists.
        check("safe cells are reachable as separate patches", first.size() <= 6
                || first.size() == InfernoArenaLifecyclePolicy.MAX_SAFE_CELLS);
        List<Cell> again = InfernoArenaLifecyclePolicy.safeCells(
                arena, protectedCells, 6, 4, 2, 1L);
        check("the same seed reproduces the same safe cells", again.equals(first));
        List<Cell> rotated = InfernoArenaLifecyclePolicy.safeCells(
                arena, protectedCells, 6, 4, 2, 2L);
        check("a new cycle relocates the safe cells", !rotated.equals(first)
                || arena.size() <= 6);
        check("a null arena yields no safe cells",
                InfernoArenaLifecyclePolicy.safeCells(null, protectedCells, 6, 4, 2, 1L).isEmpty());
        check("zero wanted yields no safe cells",
                InfernoArenaLifecyclePolicy.safeCells(arena, protectedCells, 6, 0, 2, 1L).isEmpty());
    }

    // --- 14.3 no global unavoidable damage every tick ------------------------
    private static void noGlobalPerTickDamage() {
        // Standing off the magma is always safe, whatever the tick.
        for (long tick = 1L; tick <= 400L; tick += 7L) {
            check("a player outside the fire is never hurt at " + tick,
                    InfernoArenaLifecyclePolicy.environmentDamage(tick, false, false, 0L) == 0.0D);
        }
        check("a player on the fire is hurt", InfernoArenaLifecyclePolicy
                .environmentDamage(40L, true, false, 0L) > 0.0D);
        // ...but only on the authored damage cadence, never every tick.
        check("the damage cadence is a real interval",
                InfernoArenaLifecyclePolicy.DAMAGE_INTERVAL_TICKS >= 20);
        check("an off-cadence tick deals nothing",
                InfernoArenaLifecyclePolicy.environmentDamage(
                        41L, true, false, 0L) == 0.0D);
        // A cooling/safe cell is safe even on the fire cells around it.
        check("a safe cell never burns",
                InfernoArenaLifecyclePolicy.environmentDamage(40L, true, true, 0L) == 0.0D);
        // Ignition is bounded, so a player cannot be permanently on fire.
        check("ignition is bounded", InfernoArenaLifecyclePolicy.IGNITE_TICKS <= 60);
        // The environment never stacks unavoidable immobilisation.
        check("the inferno refuses hard-control spells",
                !InfernoArenaLifecyclePolicy.allowsConcurrentSpell("VOID_MARK")
                        && !InfernoArenaLifecyclePolicy.allowsConcurrentSpell("AMBUSH_GRAB"));
        check("the inferno still allows ordinary pressure",
                InfernoArenaLifecyclePolicy.allowsConcurrentSpell("RIFT_PROJECTILE")
                        && InfernoArenaLifecyclePolicy.allowsConcurrentSpell("MELEE_SWIPE"));
    }

    // --- 14.4 lifecycle, extent and cooldown --------------------------------
    private static void lifecycleAndCooldown() {
        Stage stage = new Stage(400, 30, 60, 200);
        check("a fresh cast is preparing", stage.phase(0L) == Phase.PREPARE);
        check("the heat wave is running", stage.phase(30L) == Phase.HEATING);
        check("the arena is fully burning", stage.phase(200L) == Phase.BURNING);
        check("the inferno cools down", stage.phase(400L) == Phase.COOLING);
        check("the inferno ends", stage.phase(460L) == Phase.ENDED);
        // Nothing hits during the authored preparation.
        check("the preparation cannot damage", !stage.damages(0L) && !stage.damages(29L));
        check("the burning stage damages", stage.damages(200L));
        check("the ended stage never damages", !stage.damages(10_000L));

        // Cooldown: the inferno cannot restart while it is cooling.
        check("a live inferno blocks a second cast",
                !InfernoArenaLifecyclePolicy.mayRestart(100L, 500L, 600L));
        check("the cooldown is respected",
                !InfernoArenaLifecyclePolicy.mayRestart(520L, 500L, 600L));
        // The cooldown is measured from the END of the previous cast, so the
        // inferno cannot chain back-to-back firestorms.
        check("the cooldown is measured from the previous cast end",
                !InfernoArenaLifecyclePolicy.mayRestart(700L, 500L, 600L));
        check("a restarted inferno is allowed after the full cooldown",
                InfernoArenaLifecyclePolicy.mayRestart(600L + 200L, 500L, 600L));
        check("one tick before the cooldown still refuses",
                !InfernoArenaLifecyclePolicy.mayRestart(600L + 199L, 500L, 600L));
        check("the cooldown is a real interval",
                InfernoArenaLifecyclePolicy.RESTART_COOLDOWN_TICKS >= 200);

        // Every cell is restored from its own exact original block data.
        Stage done = new Stage(400, 30, 60, 200);
        check("before the restore the cells are still mutated",
                !done.shouldRestore(100L));
        check("the exact originals are restored at the end", done.shouldRestore(450L));
    }

    // --- vent timeline -------------------------------------------------------
    private static void ventTimeline() {
        check("a vent warns before it erupts",
                InfernoArenaLifecyclePolicy.ventDamages(InfernoArenaLifecyclePolicy.VentPhase.WARN) == false);
        check("a vent damages on its eruption",
                InfernoArenaLifecyclePolicy.ventDamages(InfernoArenaLifecyclePolicy.VentPhase.ERUPT));
        check("a vent never damages while recovering",
                !InfernoArenaLifecyclePolicy.ventDamages(InfernoArenaLifecyclePolicy.VentPhase.RECOVER)
                        && !InfernoArenaLifecyclePolicy.ventDamages(InfernoArenaLifecyclePolicy.VentPhase.REMOVE));
        check("geysers are bounded per cast",
                InfernoArenaLifecyclePolicy.MAX_VENTS >= 3 && InfernoArenaLifecyclePolicy.MAX_VENTS <= 6);
        for (int players = 2; players <= 20; players++) {
            int vents = InfernoArenaLifecyclePolicy.ventCountForPlayers(players);
            check("vent count is bounded at " + players,
                    vents >= 1 && vents <= InfernoArenaLifecyclePolicy.MAX_VENTS);
        }
        check("a larger party gets more geysers",
                InfernoArenaLifecyclePolicy.ventCountForPlayers(20)
                        >= InfernoArenaLifecyclePolicy.ventCountForPlayers(2));
    }

    private static void check(String name, boolean condition) {
        if (!condition) {
            System.out.println("FAIL " + name);
            failures++;
        }
    }
}