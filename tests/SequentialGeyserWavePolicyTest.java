import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import me.copimine.endevent.domain.SequentialGeyserWavePolicy;
import me.copimine.endevent.domain.SequentialGeyserWavePolicy.Cell;
import me.copimine.endevent.domain.SequentialGeyserWavePolicy.Phase;
import me.copimine.endevent.domain.SequentialGeyserWavePolicy.Plan;
import me.copimine.endevent.domain.SequentialGeyserWavePolicy.Section;
import me.copimine.endevent.domain.SequentialGeyserWavePolicy.Timeline;

/**
 * Guards the Phase 3 Sequential 3x3 Geyser Wave contract:
 * the strip is contiguous (no diagonal corner pockets), it covers the captured
 * target, it erupts in order with a fair warning, it deals exactly one hit per
 * cast, and its launch is bounded.
 */
public final class SequentialGeyserWavePolicyTest {
    private SequentialGeyserWavePolicyTest() {
    }

    public static void main(String[] args) {
        testSectionFootprint();
        testAdjacencyOnAxis();
        testShortStripCoversTarget();
        testMediumStripIsContiguous();
        testDiagonalHasNoCornerPockets();
        testWallTruncatesBeforeTarget();
        testSectionCapIsBounded();
        testTimingOrdering();
        testWarningAlwaysPrecedesDamage();
        testExactlyOneDamageWindowPerSection();
        testPerCastDamageDedup();
        testLaunchIsBounded();
        testContainsUsesRealFootprint();
        testSimultaneousFrontIsBounded();
        testInvalidInputsStaySafe();
        System.out.println("SequentialGeyserWavePolicyTest OK");
    }

    private static Plan plan(double cx, double cz, double tx, double tz) {
        return SequentialGeyserWavePolicy.plan(cx, cz, tx, tz, section -> true);
    }

    private static void testSectionFootprint() {
        Section section = new Section(0, 9, 12);
        List<Cell> cells = section.cells();
        check(cells.size() == 9, "a section owns nine floor cells");
        Set<Cell> unique = new HashSet<>(cells);
        check(unique.size() == 9, "section cells are unique");
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Cell cell : cells) {
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxZ = Math.max(maxZ, cell.z());
        }
        check(minX == 9 && maxX == 11, "width is exactly three blocks");
        check(minZ == 12 && maxZ == 14, "depth is exactly three blocks");
        check(Math.abs(section.centerX() - 10.5D) < 1e-9, "centre X is on the half block");
        check(Math.abs(section.centerZ() - 13.5D) < 1e-9, "centre Z is on the half block");
    }

    private static void testAdjacencyOnAxis() {
        Section a = new Section(0, 0, 0);
        Section b = new Section(1, 3, 0);
        check(a.adjacentTo(b) && b.adjacentTo(a), "axis neighbours share an edge");
        Section corner = new Section(1, 3, 3);
        check(!a.adjacentTo(corner), "corner-touching sections are not adjacent");
        check(!a.adjacentTo(null), "null is not adjacent");
    }

    private static void testShortStripCoversTarget() {
        Plan plan = plan(0.5D, 0.5D, 4.5D, 0.5D);
        check(plan.coversTarget(), "a short strip reaches the target");
        check(!plan.sections().isEmpty(), "a short strip is viable");
        Section last = plan.sections().get(plan.sections().size() - 1);
        int targetTile = Math.floorDiv(4, SequentialGeyserWavePolicy.SECTION_SIZE);
        check(Math.floorDiv(last.minX(), SequentialGeyserWavePolicy.SECTION_SIZE) == targetTile,
                "the last section sits on the target tile");
    }

    private static void testMediumStripIsContiguous() {
        Plan plan = plan(0.5D, 0.5D, 40.5D, 0.5D);
        check(plan.coversTarget(), "a medium strip reaches the target");
        List<Section> sections = plan.sections();
        check(sections.size() == 14, "forty blocks of axis strip is fourteen sections, got " + sections.size());
        for (int i = 1; i < sections.size(); i++) {
            check(sections.get(i - 1).adjacentTo(sections.get(i)),
                    "consecutive sections must share an edge at " + i);
            check(sections.get(i).index() == i, "section indices are sequential");
        }
    }

    private static void testDiagonalHasNoCornerPockets() {
        Plan plan = plan(0.5D, 0.5D, 18.5D, 18.5D);
        check(plan.coversTarget(), "a diagonal strip reaches the target");
        List<Section> sections = plan.sections();
        check(sections.size() > 1, "a diagonal strip has multiple sections");
        // The union of all footprints must be orthogonally connected: walking the
        // section chain, every step shares an edge. That is what removes the
        // corner pocket an eight-connected line would leave.
        for (int i = 1; i < sections.size(); i++) {
            check(sections.get(i - 1).adjacentTo(sections.get(i)),
                    "diagonal sections must be edge-adjacent at " + i);
        }
        // And the union of the 3x3 footprints is a single connected region.
        Set<Cell> union = new HashSet<>();
        for (Section section : sections) {
            union.addAll(section.cells());
        }
        check(orthogonallyConnected(union), "the diagonal strip has no unreachable pocket");
    }

    private static boolean orthogonallyConnected(Set<Cell> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        Cell start = cells.iterator().next();
        Set<Cell> seen = new HashSet<>();
        List<Cell> stack = new ArrayList<>();
        stack.add(start);
        while (!stack.isEmpty()) {
            Cell current = stack.remove(stack.size() - 1);
            if (!seen.add(current)) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != 1) {
                        continue;
                    }
                    Cell next = new Cell(current.x() + dx, current.z() + dz);
                    if (cells.contains(next) && !seen.contains(next)) {
                        stack.add(next);
                    }
                }
            }
        }
        return seen.size() == cells.size();
    }

    private static void testWallTruncatesBeforeTarget() {
        // A wall at tile four (block x = 12) stops the strip before it.
        Plan plan = SequentialGeyserWavePolicy.plan(0.5D, 0.5D, 40.5D, 0.5D,
                section -> section.minX() < 12);
        check(!plan.coversTarget(), "a wall means the target is not covered");
        check(!plan.sections().isEmpty(), "the legal prefix still exists");
        for (Section section : plan.sections()) {
            check(section.minX() < 12, "no section may tunnel through the wall");
        }
    }

    private static void testSectionCapIsBounded() {
        Plan plan = plan(0.5D, 0.5D, 10_000.5D, 10_000.5D);
        check(plan.sections().size() <= SequentialGeyserWavePolicy.MAX_SECTIONS,
                "the strip never exceeds the authored section cap");
        check(!plan.coversTarget(), "a route beyond the cap is not claimed as covered");
    }

    private static void testTimingOrdering() {
        int sections = 5;
        Timeline timeline = new Timeline(100L, sections);
        check(timeline.eruptionStart(0) == 100L + SequentialGeyserWavePolicy.INITIAL_WARNING_TICKS,
                "the first section erupts after the initial warning");
        for (int i = 1; i < sections; i++) {
            long gap = timeline.eruptionStart(i) - timeline.eruptionStart(i - 1);
            check(gap == SequentialGeyserWavePolicy.ACTIVATION_INTERVAL_TICKS,
                    "consecutive eruptions are one interval apart at " + i);
        }
        for (int i = 0; i < sections; i++) {
            check(timeline.eruptionEnd(i) - timeline.eruptionStart(i)
                            == SequentialGeyserWavePolicy.ERUPTION_TICKS,
                    "each eruption lasts the authored duration at " + i);
            check(timeline.eruptionStart(i) - timeline.startTick()
                            >= SequentialGeyserWavePolicy.INITIAL_WARNING_TICKS,
                    "every section warns at least the initial warning at " + i);
        }
        // The later a section sits, the longer its warning — never shorter.
        for (int i = 1; i < sections; i++) {
            long earlierWarning = timeline.eruptionStart(i - 1) - timeline.startTick();
            long laterWarning = timeline.eruptionStart(i) - timeline.startTick();
            check(laterWarning > earlierWarning, "warnings never shrink down the strip at " + i);
        }
        check(timeline.endTick() == timeline.restoreEnd(sections - 1),
                "the whole strip ends when the last section is restored");
    }

    private static void testWarningAlwaysPrecedesDamage() {
        Timeline timeline = new Timeline(0L, 3);
        for (int index = 0; index < 3; index++) {
            long start = timeline.eruptionStart(index);
            for (long tick = 0; tick < start; tick++) {
                check(timeline.phase(index, tick) == Phase.WARNING,
                        "section " + index + " must warn before it erupts at " + tick);
            }
            check(timeline.damages(index, start), "damage starts exactly on the eruption tick");
            check(!timeline.damages(index, start - 1), "damage never precedes the eruption");
        }
    }

    private static void testExactlyOneDamageWindowPerSection() {
        Timeline timeline = new Timeline(0L, 4);
        for (int index = 0; index < 4; index++) {
            int windows = 0;
            long start = timeline.eruptionStart(index);
            long end = timeline.eruptionEnd(index);
            for (long tick = start; tick < end + 40; tick++) {
                if (timeline.damages(index, tick)) {
                    windows++;
                    check(tick >= start && tick < end, "damage stays inside the eruption window");
                }
            }
            check(windows == SequentialGeyserWavePolicy.ERUPTION_TICKS,
                    "the eruption window is contiguous and bounded for " + index);
            // A section never damages before it was even planned.
            check(!timeline.damages(index, start - 5), "no early damage for " + index);
        }
    }

    private static void testPerCastDamageDedup() {
        String castId = "cast-1";
        UUID player = UUID.randomUUID();
        Set<String> ledger = new HashSet<>();
        check(SequentialGeyserWavePolicy.mayDamagePlayer(castId, player, ledger),
                "the first covering section hits the player");
        check(!SequentialGeyserWavePolicy.mayDamagePlayer(castId, player, ledger),
                "a second section of the same cast must not hit the same player");
        // A different player is independent.
        UUID other = UUID.randomUUID();
        check(SequentialGeyserWavePolicy.mayDamagePlayer(castId, other, ledger),
                "a different player is still hittable");
        // A new cast is a new ledger.
        Set<String> fresh = new HashSet<>();
        check(SequentialGeyserWavePolicy.mayDamagePlayer("cast-2", player, fresh),
                "a new cast can hit the same player again");
        check(!SequentialGeyserWavePolicy.mayDamagePlayer(castId, null, ledger),
                "a missing player is never damaged");
    }

    private static void testLaunchIsBounded() {
        SequentialGeyserWavePolicy.Launch launch = SequentialGeyserWavePolicy.launch(3.0D, 4.0D);
        double horizontal = Math.hypot(launch.x(), launch.z());
        check(Math.abs(horizontal - SequentialGeyserWavePolicy.ERUPTION_LAUNCH_VELOCITY) < 1e-6,
                "horizontal launch is exactly the authored velocity");
        check(launch.y() <= SequentialGeyserWavePolicy.MAX_LAUNCH_Y,
                "vertical launch is bounded");
        SequentialGeyserWavePolicy.Launch degenerate = SequentialGeyserWavePolicy.launch(0.0D, 0.0D);
        check(Double.isFinite(degenerate.x()) && Double.isFinite(degenerate.y())
                        && Double.isFinite(degenerate.z()),
                "a degenerate direction never produces a NaN launch");
        SequentialGeyserWavePolicy.Launch infinite = SequentialGeyserWavePolicy.launch(Double.NaN, 1.0D);
        check(Double.isFinite(infinite.x()), "a non-finite direction is sanitised");
    }

    private static void testContainsUsesRealFootprint() {
        Section section = new Section(0, 0, 0);
        int floorY = 64;
        // Inside the footprint centre.
        check(SequentialGeyserWavePolicy.contains(section, floorY, 1.5D, 65.0D, 1.5D),
                "the section centre is inside the damage cylinder");
        // Just inside the corridor floor.
        check(SequentialGeyserWavePolicy.contains(section, floorY, 0.5D, 65.0D, 0.5D),
                "a footprint corner block is inside the damage cylinder");
        // Above the column top.
        check(!SequentialGeyserWavePolicy.contains(section, floorY, 1.5D, 65.0D + 20.0D, 1.5D),
                "a body far above the column is not hit");
        // Below the floor.
        check(!SequentialGeyserWavePolicy.contains(section, floorY, 1.5D, 64.0D - 5.0D, 1.5D),
                "a body under the floor is not hit");
        // Outside the radius.
        check(!SequentialGeyserWavePolicy.contains(section, floorY, 10.0D, 65.0D, 10.0D),
                "a body far to the side is not hit");
    }

    private static void testSimultaneousFrontIsBounded() {
        int front = SequentialGeyserWavePolicy.simultaneousEruptions();
        check(front >= 1, "at least one section must be erupting");
        check(front <= 3, "the front stays narrow and readable");
        int gap = SequentialGeyserWavePolicy.waveGapTicks(20);
        check(gap >= SequentialGeyserWavePolicy.waveGapTicks(2),
                "a larger party does not get a shorter cadence");
        check(SequentialGeyserWavePolicy.waveGapTicks(0) > 0, "an empty roster still has a gap");
    }

    private static void testInvalidInputsStaySafe() {
        check(!SequentialGeyserWavePolicy.plan(Double.NaN, 0.0D, 0.0D, 0.0D, s -> true).isViable(),
                "a non-finite caster is refused");
        check(!SequentialGeyserWavePolicy.plan(0.0D, 0.0D, 0.0D, 0.0D, null).isViable(),
                "a missing legality predicate is refused");
        Plan refusal = SequentialGeyserWavePolicy.plan(0.0D, 0.0D, 10.0D, 10.0D, s -> false);
        check(refusal.sections().isEmpty(), "a fully illegal route yields no sections");
        check(!refusal.coversTarget(), "a fully illegal route never claims coverage");
        check(new Timeline(-5L, -3).sections() == 0, "a negative timeline is clamped");
        check(new Timeline(-5L, 0).endTick() == 0L, "a negative start is clamped to zero");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
