import me.copimine.endevent.domain.BlackFogSafeZonePolicy;

import java.util.List;

public final class BlackFogSafeZonePolicyTest {
    public static void main(String[] args) {
        // November 11: shrink monotonically without stranding the frozen party.
        for (int players = 1; players <= 20; players++) {
            int target = me.copimine.endevent.domain.BlackFogTimingPolicy.cycleCount(players);
            check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, players) == 3, "maximum footprint");
            int finalSide=BlackFogSafeZonePolicy.sideForCycleAndPlayers(target-1,players);
            int finalZones=me.copimine.endevent.domain.BlackFogTimingPolicy.zoneCount(players,target-1);
            check(finalSide*finalSide*finalZones>=players,"late refuge capacity");
            int previous = 3;
            for (int cycle = 0; cycle < target; cycle++) {
                int side = BlackFogSafeZonePolicy.sideForCycleAndPlayers(cycle, players);
                check(side >= 1 && side <= previous, "monotonic footprint");
                check(BlackFogSafeZonePolicy.cells(10, 20, side).size() == side * side, "exact square");
                previous = side;
            }
        }
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(-1, 2) == 0
                && BlackFogSafeZonePolicy.sideForCycleAndPlayers(10, 2) == 0
                && BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, 0) == 0, "invalid geometry rejected");
        check(BlackFogSafeZonePolicy.cells(10, 20, 1).size() == 1,
                "smallest fog safe zone must contain one cell");
        check(BlackFogSafeZonePolicy.cells(10, 20, 2).equals(List.of(
                        new BlackFogSafeZonePolicy.Cell(9, 19),
                        new BlackFogSafeZonePolicy.Cell(9, 20),
                        new BlackFogSafeZonePolicy.Cell(10, 19),
                        new BlackFogSafeZonePolicy.Cell(10, 20))),
                "second fog cycle must mark exactly a 2x2 safe footprint");
        check(BlackFogSafeZonePolicy.cells(10, 20, 3).size() == 9,
                "largest fog safe zone must contain exactly a 3x3 footprint");
        System.out.println("BlackFogSafeZonePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
