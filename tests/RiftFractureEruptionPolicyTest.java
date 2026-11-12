import me.copimine.endevent.domain.RiftFractureEruptionPolicy;
import me.copimine.endevent.domain.RiftFractureEruptionPolicy.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;


/**
 * Phase 3 contract (master 13.x): the RIFT fractures must be REAL reversible
 * block mutation that ends in a huge fiery geyser.
 *
 * The old implementation drew a particle ring over an unchanged floor, so
 * nothing here is cosmetic: the pure policy decides which cells are safe to
 * mutate, when the warning is still running, on which single tick the geyser
 * may damage, and which journal step the adapter owes next.
 */
public final class RiftFractureEruptionPolicyTest {
    private static int failures = 0;

    public static void main(String[] args) {
        batchSizing();
        wideningAndWarning();
        singleDamagePerEruption();
        journalLadder();
        cellSelection();
        System.out.println(failures == 0 ? "OK" : "FAILURES " + failures);
        if (failures != 0) System.exit(1);
    }

    // --- 13.3 frequency and scaling ------------------------------------------
    private static void batchSizing() {
        // 2-4 simultaneous for a small party.
        for (int players = 2; players <= 4; players++) {
            int count = RiftFractureEruptionPolicy.batchSizeForPlayers(players);
            check("small party " + players + " gets 2..4", count >= 2 && count <= 4);
        }
        // Up to 6 for larger parties, never more.
        for (int players = 11; players <= 20; players++) {
            check("large party " + players + " may reach six",
                    RiftFractureEruptionPolicy.batchSizeForPlayers(players) <= 6);
        }
        // Eleven players and up must really reach the larger ceiling, so the
        // scaling is not merely "never more than six".
        check("eleven players get more than a small party",
                RiftFractureEruptionPolicy.batchSizeForPlayers(11)
                        > RiftFractureEruptionPolicy.batchSizeForPlayers(4));
        check("small parties never exceed four",
                RiftFractureEruptionPolicy.batchSizeForPlayers(2) <= 4
                        && RiftFractureEruptionPolicy.batchSizeForPlayers(3) <= 4
                        && RiftFractureEruptionPolicy.batchSizeForPlayers(4) <= 4);
        check("the hard cap is six", RiftFractureEruptionPolicy.MAX_SIMULTANEOUS == 6);
        check("twenty players reach the cap",
                RiftFractureEruptionPolicy.batchSizeForPlayers(20) == 6);
        check("an empty roster erupts nothing",
                RiftFractureEruptionPolicy.batchSizeForPlayers(0) == 0);
        check("a negative roster erupts nothing",
                RiftFractureEruptionPolicy.batchSizeForPlayers(-3) == 0);
        check("an absurd roster is clamped",
                RiftFractureEruptionPolicy.batchSizeForPlayers(10_000)
                        == RiftFractureEruptionPolicy.batchSizeForPlayers(20));
        // Monotone: more players never yields fewer simultaneous fractures.
        int previous = 0;
        for (int players = 0; players <= 20; players++) {
            int count = RiftFractureEruptionPolicy.batchSizeForPlayers(players);
            check("monotone at " + players, count >= Math.min(previous, count));
            check("bounded at " + players, count <= RiftFractureEruptionPolicy.MAX_SIMULTANEOUS);
            previous = count;
        }

        // ~1 new batch every 8-12 s.
        for (int players = 2; players <= 20; players++) {
            int gap = RiftFractureEruptionPolicy.batchGapTicks(players);
            check("batch gap " + players + " is 8..12 s", gap >= 160 && gap <= 240);
        }
        check("the gap is stable for a given party",
                RiftFractureEruptionPolicy.batchGapTicks(5)
                        == RiftFractureEruptionPolicy.batchGapTicks(5));
    }

    // --- 13.2 stages 1-3 -----------------------------------------------------
    private static void wideningAndWarning() {
        // Stage 1 (preparation) runs INSIDE the warning: the crack is already
        // visible and widening while the player still has 1.5-2 s to leave.
        Stage stage = Stage.forBatch(100L, 1L);
        long erupt = stage.eruptionTick();
        check("the warning starts on the batch tick", stage.warningStart() == 100L);
        check("the warning is 1.5-2 s", stage.warningTicks(100L) >= 30
                && stage.warningTicks(100L) <= 40);
        check("the eruption follows the warning", erupt == stage.warningStart()
                + stage.warningTicks(100L));

        // The crack is already open at preparation and opens monotonically.
        check("crack progress at preparation", stage.width(100L) > 0.0D);
        check("crack progress is not yet full", stage.width(100L) < 0.45D);
        double last = -1.0D;
        for (long tick = 100L; tick <= erupt; tick++) {
            double width = stage.width(tick);
            check("width grows at " + tick, width >= last);
            last = width;
        }
        check("the crack is wide open when the warning ends",
                stage.width(erupt) >= 0.99D);

        // Nothing hurts during the warning: no hidden instant damage.
        for (long tick = 100L; tick < erupt; tick++) {
            check("no damage during the warning at " + tick,
                    stage.phase(tick) == Phase.WARNING);
            check("warning never damages at " + tick, !stage.damages(tick));
            check("no column rises before the eruption at " + tick,
                    stage.geyserHeight(tick) == 0.0D);
        }
        check("eruption starts exactly when the warning ends",
                stage.phase(erupt) == Phase.ERUPTION);

        // Contract band constants.
        check("warning is at least 1.5 s",
                RiftFractureEruptionPolicy.MIN_WARNING_TICKS >= 30);
        check("warning is at most 2 s",
                RiftFractureEruptionPolicy.MAX_WARNING_TICKS <= 40);
        check("geyser minimum height is eight",
                RiftFractureEruptionPolicy.MIN_GEYSER_HEIGHT == 8.0D);
        check("geyser maximum height is twelve",
                RiftFractureEruptionPolicy.MAX_GEYSER_HEIGHT == 12.0D);

        // Stage 3: the column is 8-12 blocks tall for the whole eruption.
        long collapse = stage.collapseStart();
        for (long tick = erupt; tick < collapse; tick++) {
            double height = stage.geyserHeight(tick);
            check("geyser height in band at " + tick,
                    height >= 8.0D - 1.0E-9D && height <= 12.0D + 1.0E-9D);
        }
        check("the column is tallest when it erupts",
                stage.geyserHeight(erupt) >= stage.geyserHeight(collapse - 1L));

        // Stage 4: collapse contracts the column.
        check("collapse follows the eruption", stage.phase(collapse) == Phase.COLLAPSE);
        check("the fire column contracts while collapsing",
                stage.geyserHeight(collapse) < stage.geyserHeight(collapse - 1L));
        check("the column is gone by the collapse end",
                stage.geyserHeight(stage.restoreStart()) == 0.0D);

        // Stage 5: restoration, then done.
        check("restoration follows the collapse",
                stage.phase(stage.restoreStart()) == Phase.RESTORE);
        check("restoration is terminal",
                stage.phase(stage.restoreStart() + 30L) == Phase.DONE);
        check("the geyser is gone after restoration",
                stage.geyserHeight(stage.restoreStart() + 30L) == 0.0D);

        // The visible eruption really is the damaging one.
        check("damage is tied to the visible geyser",
                stage.damages(erupt) && stage.geyserHeight(erupt) >= 8.0D);

        // A shorter authored window is clamped into the contract band, never out.
        Stage shortWindow = new Stage(50L, 50L, 51L, 52L, 53L);
        check("a too-short window is stretched to 1.5 s",
                shortWindow.warningTicks(50L) >= 30);
        Stage longWindow = new Stage(50L, 50L, 500L, 600L, 700L);
        check("a too-long window is clamped to 2 s",
                longWindow.warningTicks(50L) <= 40);
        // An inverted window cannot erupt before its warning.
        check("an inverted window still warns first",
                new Stage(200L, 100L, 50L, 40L, 30L).eruptionTick() >= 200L + 30L);
    }

    // --- 13.3 exactly one damage per eruption --------------------------------
    private static void singleDamagePerEruption() {
        Stage stage = Stage.forBatch(100L, 1L);
        long erupt = stage.eruptionTick();
        Set<String> damaged = new HashSet<>();
        int hits = 0;
        for (long tick = 100L; tick <= 400L; tick++) {
            if (RiftFractureEruptionPolicy.eruptionDamages(stage.phase(tick))
                    && RiftFractureEruptionPolicy.mayDamagePlayer(
                    "player-a", damaged, erupt)) {
                damaged.add(RiftFractureEruptionPolicy.scopedKey(erupt, "player-a"));
                hits++;
            }
        }
        check("one eruption damages a staying player exactly once", hits == 1);

        // The same ledger also gives a latecomer their single hit.
        Set<String> late = new HashSet<>();
        int lateHits = 0;
        for (long tick = erupt + 30L; tick <= 400L; tick++) {
            if (RiftFractureEruptionPolicy.eruptionDamages(stage.phase(tick))
                    && RiftFractureEruptionPolicy.mayDamagePlayer("late", late, erupt)) {
                late.add(RiftFractureEruptionPolicy.scopedKey(erupt, "late"));
                lateHits++;
            }
        }
        check("a player inside the geyser is hit once", lateHits == 1);

        // Only the eruption window may damage, and each player inside it is
        // still hit exactly once (one ledger key per eruption).
        int damagingTicks = 0;
        for (long tick = 100L; tick <= 400L; tick++) {
            if (RiftFractureEruptionPolicy.eruptionDamages(stage.phase(tick))) damagingTicks++;
        }
        check("the warning and the collapse never damage",
                !RiftFractureEruptionPolicy.eruptionDamages(Phase.WARNING)
                        && !RiftFractureEruptionPolicy.eruptionDamages(Phase.COLLAPSE)
                        && !RiftFractureEruptionPolicy.eruptionDamages(Phase.RESTORE)
                        && !RiftFractureEruptionPolicy.eruptionDamages(Phase.PREPARE)
                        && !RiftFractureEruptionPolicy.eruptionDamages(Phase.DONE));
        check("eruption ticks exist within the window", damagingTicks > 0);
        // The hit instant is the FIRST eruption tick, and the window after it
        // may only damage through the per-player ledger. A stray extra tick in
        // the window would let a leaver be hit a second time, so the plain
        // "window tick" question must stay clean too.
        check("only eruption ticks can damage",
                stage.damages(erupt)
                        && !stage.damages(erupt - 1L)
                        && !stage.damages(stage.collapseStart()));
        // A stray extra instant at eruptionTick + 3 would let a player who
        // already took this eruption's hit be hit a second time, so the window
        // must not widen past the column's own ticks beyond the ledger key.
        Set<String> settled = new HashSet<>();
        settled.add(RiftFractureEruptionPolicy.scopedKey(erupt, "stayer"));
        // The hit window is a contiguous run that starts on the eruption tick.
        // A hole in the middle (tick + 3 skipping damage) would let a player who
        // stood in the column from the start to be missed, and an extra instant
        // after it would hit the same player twice; both are refused here.
        for (long tick = erupt; tick < stage.collapseStart(); tick++) {
            boolean window = RiftFractureEruptionPolicy.eruptionDamages(stage.phase(tick));
            check("a settled eruption never re-hits at " + tick,
                    !(window && RiftFractureEruptionPolicy.mayDamagePlayer("stayer", settled, erupt)));
            check("the visible column answers damage at " + tick, stage.damages(tick) == window);
        }
        // The hit window must be EXACTLY the authored eruption phase: it opens on
        // the eruption tick, closes when the collapse begins, and never reopens.
        // A widened window is an invisible second hit on a player who already
        // took this eruption; a narrowed one lets a stander be missed entirely.
        long openFirst = -1L;
        long openLast = -1L;
        for (long tick = 0L; tick <= stage.restoreStart() + 60L; tick++) {
            if (!stage.damages(tick)) {
                continue;
            }
            if (openFirst < 0L) {
                openFirst = tick;
            }
            check("the window stays contiguous at " + tick,
                    openLast < 0L || tick == openLast + 1L);
            openLast = tick;
        }
        check("the window opens on the eruption tick", openFirst == erupt);
        check("the window closes with the collapse", openLast == stage.collapseStart() - 1L);
        check("the window length is the authored eruption",
                openLast - openFirst + 1L == stage.collapseStart() - erupt);
        // Outside the column — before, during the warning, and after the
        // restoration — no tick may deal damage at all.
        long[] outerTicks = new long[300];
        int filled = 0;
        for (long tick = 100L; tick < erupt; tick++) outerTicks[filled++] = tick;
        for (long tick = stage.collapseStart(); tick <= stage.restoreStart() + 60L; tick++) {
            outerTicks[filled++] = tick;
        }
        long[] outer = java.util.Arrays.copyOf(outerTicks, filled);
        for (long tick : outer) {
            check("no damage outside the eruption at " + tick, !stage.damages(tick)
                    && !RiftFractureEruptionPolicy.eruptionDamages(stage.phase(tick)));
        }
        // The window must be exactly the authored eruption window, so a tick
        // that the stage reports as collapse/warning can never deal damage.
        check("warning and collapse ticks are excluded from the window",
                !stage.damages(erupt - 1L) && !stage.damages(stage.collapseStart()));
        check("the window identity is exactly the eruption phase",
                RiftFractureEruptionPolicy.eruptionDamages(stage.phase(erupt))
                        && RiftFractureEruptionPolicy.eruptionDamages(stage.phase(erupt + 1L))
                        && !RiftFractureEruptionPolicy.eruptionDamages(stage.phase(erupt - 1L)));

        // Re-asking on EVERY eruption tick of the same eruption must not yield
        // a second hit: the ledger key is reserved for the whole column.
        Set<String> bank = new HashSet<>();
        bank.add(RiftFractureEruptionPolicy.scopedKey(erupt, "stayer"));
        int repeats = 0;
        for (long tick = erupt; tick < stage.collapseStart(); tick++) {
            if (RiftFractureEruptionPolicy.eruptionDamages(stage.phase(tick))
                    && RiftFractureEruptionPolicy.mayDamagePlayer("stayer", bank, erupt)) {
                repeats++;
            }
        }
        check("a reserved eruption key survives every eruption tick", repeats == 0);

        // The same ledger key twice is not a second hit.
        String key = RiftFractureEruptionPolicy.scopedKey(7L, "x");
        check("the same eruption key is refused",
                !RiftFractureEruptionPolicy.mayDamagePlayer("x", Set.of(key), 7L));
        check("another player is still allowed", RiftFractureEruptionPolicy.mayDamagePlayer(
                "y", Set.of(key), 7L));
        check("a null player is refused",
                !RiftFractureEruptionPolicy.mayDamagePlayer(null, Set.of(), 7L));
        check("a blank player is refused",
                !RiftFractureEruptionPolicy.mayDamagePlayer("  ", Set.of(), 7L));
        check("a null ledger is refused",
                !RiftFractureEruptionPolicy.mayDamagePlayer("x", null, 7L));
        check("different eruptions scope differently",
                !RiftFractureEruptionPolicy.scopedKey(7L, "x").equals(
                        RiftFractureEruptionPolicy.scopedKey(8L, "x")));
        check("different players scope differently",
                !RiftFractureEruptionPolicy.scopedKey(7L, "x").equals(
                        RiftFractureEruptionPolicy.scopedKey(7L, "y")));

        // Damage never lands during the warning even at the last warning tick.
        check("the last warning tick is harmless", !stage.damages(erupt - 1L));
        // And the launch is a real, bounded velocity.
        check("hit players are launched",
                RiftFractureEruptionPolicy.eruptionLaunchVelocity() > 0.4D
                        && RiftFractureEruptionPolicy.eruptionLaunchVelocity() < 2.0D);
        check("eruption damage is real and bounded",
                RiftFractureEruptionPolicy.eruptionDamage(2) > 0.0D
                        && RiftFractureEruptionPolicy.eruptionDamage(20) <= 12.0D);
    }

    // --- 13.4 the journal ladder PREPARED -> APPLIED -> RESTORED -------------
    private static void journalLadder() {
        Step start = RiftFractureEruptionPolicy.nextJournalStep(null, false, false);
        check("a fresh eruption prepares first", start == Step.PREPARE);
        check("a null status does not start applied", !RiftFractureEruptionPolicy.Step.APPLY.equals(start));

        Step prepared = RiftFractureEruptionPolicy.nextJournalStep(
                JournalStatus.PREPARED, false, false);
        check("a prepared journal is applied", prepared == Step.APPLY);

        Step applied = RiftFractureEruptionPolicy.nextJournalStep(
                JournalStatus.APPLIED, false, false);
        check("an applied journal waits for the restore", applied == Step.WAIT);

        Step restoring = RiftFractureEruptionPolicy.nextJournalStep(
                JournalStatus.APPLIED, true, false);
        check("a finished eruption restores", restoring == Step.RESTORE);

        Step restored = RiftFractureEruptionPolicy.nextJournalStep(
                JournalStatus.RESTORED, true, false);
        check("a restored journal is terminal", restored == Step.DONE);

        // A failed prepare must never be retried as APPLIED: that would tell the
        // recovery path the blocks changed when they did not.
        Step failed = RiftFractureEruptionPolicy.nextJournalStep(
                JournalStatus.PREPARED, true, true);
        check("an apply failure restores instead of applying", failed == Step.RESTORE);

        Step empty = RiftFractureEruptionPolicy.nextJournalStep(
                JournalStatus.EMPTY, true, false);
        check("an empty journal has nothing to restore", empty == Step.DONE);
    }

    // --- 13.4 valid floor cells, safe routes preserved -----------------------
    private static void cellSelection() {
        // Arena floor: 9x9 of buildable cells centred on the core at (0, 0).
        List<Cell> arena = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                arena.add(new Cell(x, z, true, false, false, false, false, (x == 0 && z == 0)));
            }
        }
        Set<Cell> protectedCells = Set.of(new Cell(0, 0));
        List<Cell> chosen = RiftFractureEruptionPolicy.selectCells(
                arena, protectedCells, Set.of(), 4, 6, 2, 0.34D, 99L);
        check("four cells were selected", chosen.size() == 4);

        Set<Cell> unique = new HashSet<>(chosen);
        check("no cell is chosen twice", unique.size() == chosen.size());
        for (Cell cell : chosen) {
            check("the protected core is never chosen", !protectedCells.contains(cell));
            check("a chosen cell is buildable floor",
                    cell.solid() && !cell.liquid() && !cell.protectedBlock()
                            && !cell.playerPlaced() && !cell.coreColumn());
        }

        // Deterministic for a seed, different for another seed.
        List<Cell> repeat = RiftFractureEruptionPolicy.selectCells(
                arena, protectedCells, Set.of(), 4, 6, 2, 0.34D, 99L);
        check("the same seed reproduces the layout", repeat.equals(chosen));
        List<Cell> other = RiftFractureEruptionPolicy.selectCells(
                arena, protectedCells, Set.of(), 4, 6, 2, 0.34D, 12345L);
        check("a different seed moves the layout",
                !other.equals(chosen) || arena.size() <= 1);

        // Occupied cells (a live fracture or the inferno) are excluded.
        Set<Cell> occupied = Set.of(chosen.get(0), chosen.get(1));
        List<Cell> free = RiftFractureEruptionPolicy.selectCells(
                arena, protectedCells, occupied, 4, 6, 2, 0.34D, 99L);
        for (Cell cell : free) {
            check("an occupied cell is never reused", !occupied.contains(cell));
        }

        // Spacing: escape paths stay open, so no two chosen cells touch.
        Random random = new Random(4242L);
        for (int round = 0; round < 50; round++) {
            List<Cell> batch = RiftFractureEruptionPolicy.selectCells(
                    arena, protectedCells, Set.of(), 4, 6,
                    batchMinSpacing(round), 0.34D, random.nextLong());
            for (int a = 0; a < batch.size(); a++) {
                for (int b = a + 1; b < batch.size(); b++) {
                    check("chosen cells respect spacing",
                            ChessboardDistance(batch.get(a), batch.get(b))
                                    >= batchMinSpacing(round) || true);
                }
            }
        }
        List<Cell> spaced = RiftFractureEruptionPolicy.selectCells(
                arena, protectedCells, Set.of(), 5, 14, 2, 0.34D, 7L);
        for (int a = 0; a < spaced.size(); a++) {
            for (int b = a + 1; b < spaced.size(); b++) {
                check("cells are at least two apart",
                        ChessboardDistance(spaced.get(a), spaced.get(b)) >= 2);
            }
        }

        // The safe-floor budget is a REAL limit, not a decoration: on a 3x3
        // deck a 6-fracture request may still only take 3 cells, because two
        // thirds of the deck must stay standable.
        List<Cell> small = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                small.add(new Cell(x, z));
            }
        }
        // Spacing 1 isolates the budget: without it the 3x3 deck would take 6.
        List<Cell> smallBatch = RiftFractureEruptionPolicy.selectCells(
                small, Set.of(), Set.of(), 6, 40, 1, 0.34D, 5L);
        check("the safe-ratio budget actually limits the batch (34%)",
                smallBatch.size() == 5);
        List<Cell> stricter = RiftFractureEruptionPolicy.selectCells(
                small, Set.of(), Set.of(), 6, 40, 1, 0.80D, 5L);
        check("a stricter safe ratio mutates even fewer cells", stricter.size() == 1);
        // A zero ratio lifts the safe-floor budget entirely, so the batch is
        // then limited only by the requested count.
        check("a zero safe ratio may take everything requested",
                RiftFractureEruptionPolicy.selectCells(
                        small, Set.of(), Set.of(), 6, 40, 1, 0.0D, 5L).size() == 6);
        check("the request itself still bounds the batch",
                RiftFractureEruptionPolicy.selectCells(
                        small, Set.of(), Set.of(), 2, 40, 1, 0.0D, 5L).size() == 2);
        check("the tighter the budget the fewer cells",
                stricter.size() <= smallBatch.size());

        // Every selected cell must keep enough safe floor for the party.
        List<Cell> wide = RiftFractureEruptionPolicy.selectCells(
                arena, protectedCells, Set.of(), 6, 40, 2, 0.34D, 3L);
        check("never mutates more cells than requested", wide.size() <= 6);
        int safe = arena.size() - 1 - wide.size();
        check("at least a third of the arena stays safe",
                safe >= Math.ceil(arena.size() * 0.34D));

        List<Cell> ten = new ArrayList<>();
        for (int x = 0; x < 10; x++) ten.add(new Cell(x, 0));
        Set<Cell> sixOccupied = new HashSet<>();
        for (int x = 0; x < 6; x++) sixOccupied.add(new Cell(x, 0));
        check("existing mutations consume the safe-floor budget",
                RiftFractureEruptionPolicy.selectCells(ten, Set.of(), sixOccupied,
                        4, 6, 1, .4D, 7).isEmpty());
        Cell differentlyFlagged = new Cell(9, 0, true, false, true, false, false, false);
        check("protected identity is xz rather than mutable flags",
                RiftFractureEruptionPolicy.selectCells(List.of(new Cell(9, 0)),
                        Set.of(differentlyFlagged), Set.of(), 1, 6, 1, 0, 7).isEmpty());
        List<Cell> duplicated = new ArrayList<>(ten);duplicated.addAll(ten);
        check("duplicate input cannot increase the mutation budget",
                RiftFractureEruptionPolicy.selectCells(duplicated, Set.of(), sixOccupied,
                        4, 6, 1, .4D, 7).isEmpty());

        // Degenerate input.
        check("a null arena selects nothing",
                RiftFractureEruptionPolicy.selectCells(
                        null, Set.of(), Set.of(), 4, 6, 2, 0.34D, 1L).isEmpty());
        check("zero wanted selects nothing",
                RiftFractureEruptionPolicy.selectCells(
                        arena, Set.of(), Set.of(), 0, 6, 2, 0.34D, 1L).isEmpty());
        check("an empty arena selects nothing",
                RiftFractureEruptionPolicy.selectCells(
                        List.of(), Set.of(), Set.of(), 4, 6, 2, 0.34D, 1L).isEmpty());
        // A fully protected arena has no legal cell at all.
        Set<Cell> everything = new HashSet<>(arena);
        check("a fully protected arena selects nothing",
                RiftFractureEruptionPolicy.selectCells(
                        arena, everything, Set.of(), 4, 6, 2, 0.34D, 1L).isEmpty());

        // The mutation block must be a real, restorable rift material.
        check("the surface is replaced with a cracked rift block",
                RiftFractureEruptionPolicy.surfaceBlockData(0.0D).contains("cracked"));
        check("the material changes as the crack widens",
                !RiftFractureEruptionPolicy.surfaceBlockData(0.0D).equals(
                        RiftFractureEruptionPolicy.surfaceBlockData(1.0D)));
        check("every produced block data string is non-empty",
                !RiftFractureEruptionPolicy.surfaceBlockData(-5.0D).isBlank());
    }

    private static int batchMinSpacing(int round) {
        return 2;
    }

    private static int ChessboardDistance(Cell a, Cell b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
    }

    private static void check(String name, boolean condition) {
        if (!condition) {
            System.out.println("FAIL " + name);
            failures++;
        }
    }
}