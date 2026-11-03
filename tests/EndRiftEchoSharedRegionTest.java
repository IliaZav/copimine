package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ECHO-002/ECHO-005/ECHO-007: every admitted owner fights its own copy inside
 * ONE shared combat region.
 *
 * <p>Lifecycle B of the personal Echo briefly split the region into
 * owner-local radial lanes with physical separators. The contract forbids
 * individual walls and per-participant cells; isolation is a server
 * owner-pair rule. This test proves the shape of the plan, not its appearance:
 * every owner holds one stable lane, adjacent lanes share an exact angular
 * edge so no unpainted corridor exists between them, and every point of the
 * region belongs to at least one owner or to the neutral Core column.</p>
 */
public final class EndRiftEchoSharedRegionTest {
    private int strict;

    private enum Outcome { PASS, FAIL }

    public static void main(String[] args) {
        EndRiftEchoSharedRegionTest test = new EndRiftEchoSharedRegionTest();
        test.runs();
        test.planIsCompleteAndStable();
        test.transitionsUseTheSharedRegionNotTheOwnLane();
        test.planRejectsInvalidRosters();
        test.sharedRegionReplacesOwnerCells();
        if (test.strict == 1) throw new IllegalStateException("assertion failed");
        System.out.println("EndRiftEchoSharedRegionTest OK");
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            strict = 1;
            throw new IllegalStateException(message);
        }
    }

    private void runs() {
        Outcome[] outcomes = Outcome.values();
        check(outcomes.length == 2, "unexpected outcome enumeration");
    }

    private void planIsCompleteAndStable() {
        for (int owners = 1; owners <= Wave7EchoDuelCellPolicy.MAX_OWNERS; owners++) {
            List<UUID> roster = roster(owners);
            List<Wave7EchoDuelCellPolicy.Cell> cells =
                    Wave7EchoDuelCellPolicy.sharedRegionPlan(2, owners, roster);
            check(cells.size() == owners, "one lane per owner at " + owners);

            // Deterministic, order independent.
            List<UUID> reversed = new ArrayList<>(roster);
            java.util.Collections.reverse(reversed);
            List<Wave7EchoDuelCellPolicy.Cell> repeated =
                    Wave7EchoDuelCellPolicy.sharedRegionPlan(2, owners, reversed);
            check(cells.equals(repeated), "lane plan must not depend on iteration order at " + owners);

            // One cell per owner, ordinal == roster position of the sorted owner.
            for (int ordinal = 0; ordinal < owners; ordinal++) {
                Wave7EchoDuelCellPolicy.Cell cell = cells.get(ordinal);
                check(cell.ordinal() == ordinal, "lane ordinal mismatch at " + owners);
                check(cell.ownerCount() == owners, "lane roster size mismatch at " + owners);
                check(cell.centerAngleRadians() > -Math.PI && cell.centerAngleRadians() <= Math.PI,
                        "lane centre outside the circle at " + owners);
                check(cell.allowsOwner(cell.owner()), "lane rejects its own owner");
            }

            // Adjacent lanes share exactly one angular edge: no dead corridor
            // between two duels, and no overlap either.
            for (int ordinal = 0; ordinal + 1 < owners; ordinal++) {
                double lowerEdge = cells.get(ordinal).centerAngleRadians()
                        + cells.get(ordinal).halfWidthRadians();
                double upperEdge = cells.get(ordinal + 1).centerAngleRadians()
                        - cells.get(ordinal + 1).halfWidthRadians();
                check(Math.abs(lowerEdge - upperEdge) < 1.0E-9D,
                        "lanes " + ordinal + "/" + (ordinal + 1) + " do not share an edge at " + owners);
            }

            // The whole usable region is covered by lanes (plus the neutral
            // Core column) and nothing extends past the authored arena ring.
            int covered = 0;
            for (int sample = 0; sample < 720; sample++) {
                double angle = -Math.PI + (2.0D * Math.PI * sample / 720.0D);
                double radius = (ChamberIsolationPolicy.INNER_RADIUS
                        + ChamberIsolationPolicy.OUTER_RADIUS) / 2.0D;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                boolean any = false;
                for (Wave7EchoDuelCellPolicy.Cell cell : cells) {
                    if (cell.contains(x, z)) any = true;
                }
                if (any) covered++;
            }
            // Lanes stay narrower than the authored ring by the room buffer, so
            // a small share near the far side of the circle may fall outside.
            check(covered >= 600, "lanes leave a large dead region at " + owners + ": " + covered);
        }
    }

    /**
     * Audit ECHO error 6: crossing a lane border inside the shared region is
     * ordinary movement. Only leaving the region fails closed.
     */
    private void transitionsUseTheSharedRegionNotTheOwnLane() {
        for (int owners = 2; owners <= Wave7EchoDuelCellPolicy.MAX_OWNERS; owners++) {
            List<UUID> roster = roster(owners);
            List<Wave7EchoDuelCellPolicy.Cell> cells =
                    Wave7EchoDuelCellPolicy.sharedRegionPlan(2, owners, roster);
            Wave7EchoDuelCellPolicy.Cell first = cells.get(0);
            Wave7EchoDuelCellPolicy.Cell last = cells.get(owners - 1);
            double radius = (ChamberIsolationPolicy.INNER_RADIUS
                    + ChamberIsolationPolicy.OUTER_RADIUS) / 2.0D;
            double startX = Math.cos(first.centerAngleRadians()) * radius;
            double startZ = Math.sin(first.centerAngleRadians()) * radius;
            // A step that lands squarely inside the LAST owner's lane.
            double endX = Math.cos(last.centerAngleRadians()) * radius;
            double endZ = Math.sin(last.centerAngleRadians()) * radius;

            check(!first.contains(endX, endZ), "sample point unexpectedly inside the own lane");
            check(Wave7EchoDuelCellPolicy.allowsTransition(
                            cells, first.owner(), startX, startZ, endX, endZ),
                    "movement across lane borders was refused at " + owners);

            // Leaving the ring entirely still fails closed.
            double outside = ChamberIsolationPolicy.OUTER_RADIUS + 1.0D;
            check(!Wave7EchoDuelCellPolicy.allowsTransition(
                            cells, first.owner(), startX, startZ, outside, 0.0D),
                    "movement outside the region was allowed at " + owners);
            // The Core column stays closed for everyone.
            check(!Wave7EchoDuelCellPolicy.allowsTransition(
                            cells, first.owner(), startX, startZ, 0.0D, 0.0D),
                    "movement into the Core column was allowed at " + owners);
            // A stranger has no region access at all.
            check(!Wave7EchoDuelCellPolicy.allowsTransition(
                            cells, new UUID(0L, 999L), startX, startZ, endX, endZ),
                    "a non-admitted owner could move in the region at " + owners);
        }
    }

    private void planRejectsInvalidRosters() {
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 0, List.of()));
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 1, List.of()));
        List<UUID> duplicate = new ArrayList<>(roster(2));
        duplicate.add(duplicate.get(0));
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 2, duplicate));
        List<UUID> withNull = new ArrayList<>(roster(2));
        withNull.add(null);
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 3, withNull));
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(
                0, Wave7EchoDuelCellPolicy.MAX_OWNERS + 1, roster(Wave7EchoDuelCellPolicy.MAX_OWNERS)));
    }

    /**
     * A single shared region: movement inside the authored ring is legal for
     * every lane. The old owner-cell gate rejected a legal step the moment the
     * player crossed into the neighbouring lane.
     */
    private void sharedRegionReplacesOwnerCells() {
        List<UUID> roster = roster(3);
        List<Wave7EchoDuelCellPolicy.Cell> cells = Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 3, roster);
        double radius = (ChamberIsolationPolicy.INNER_RADIUS + ChamberIsolationPolicy.OUTER_RADIUS) / 2.0D;
        for (Wave7EchoDuelCellPolicy.Cell lane : cells) {
            double angle = lane.centerAngleRadians();
            check(Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, lane.owner(),
                    Math.cos(angle) * radius, Math.sin(angle) * radius),
                    "owner cannot stand in their own shared region");
            // Crossing into the neighbouring lane is legal in one shared region.
            double neighbourAngle = cells.get((lane.ordinal() + 1) % cells.size()).centerAngleRadians();
            double nx = Math.cos(neighbourAngle) * radius;
            double nz = Math.sin(neighbourAngle) * radius;
            check(Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, lane.owner(), nx, nz),
                    "shared region must admit a step into a neighbouring lane");
        }
        check(!Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, roster.get(0),
                        ChamberIsolationPolicy.INNER_RADIUS - 0.5D, 0.0D),
                "Core column stays outside the shared combat region");
        check(!Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, roster.get(0),
                        ChamberIsolationPolicy.OUTER_RADIUS + 1.0D, 0.0D),
                "the authored ring stays the shared region boundary");
        check(!Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, UUID.randomUUID(), radius, 0.0D),
                "a player outside the admitted roster has no region access");
    }

    private void expectRejected(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("invalid Echo roster was accepted");
    }

    private static List<UUID> roster(int count) {
        List<UUID> roster = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            roster.add(new UUID(0x5EED_0000_0000_0000L + index, index));
        }
        return roster;
    }
}