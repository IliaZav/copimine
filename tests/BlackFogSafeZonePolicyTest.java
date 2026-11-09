import me.copimine.endevent.domain.BlackFogSafeZonePolicy;

import java.util.List;

public final class BlackFogSafeZonePolicyTest {
    public static void main(String[] args) {
        // Superseded by the approved three-cycle contract. Every expectation below is
        // the value the policy really returns for that roster and cycle.
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, 2) == 3,
                "first cycle must preserve a 3x3 footprint for two players");
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, 3) == 3,
                "first cycle must preserve a 3x3 footprint for three players");
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, 4) == 2,
                "four players must cap the first-cycle footprint at 2x2");
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, 5) == 2,
                "five players must cap the first-cycle footprint at 2x2");
        // The approved contract is THREE cycles, so a fourth or fifth index is
        // out of range and must fail closed rather than report a stale footprint
        // from the superseded five-cycle design.
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, 6) == 1
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(1, 6) == 1
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(2, 6) == 1
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, 10) == 1
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(2, 10) == 1,
                "six or more players must retain only a 1x1 footprint in every cycle");
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(1, 4) == 2,
                "the 4-5 player footprint must hold 2x2 through the second cycle");
        // Measured table over the three approved cycles.
        //   1-3 players: 3,2,1     4-5 players: 2,2,1     6+ players: 1,1,1
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(2, 4) == 1,
                "the 4-5 player footprint must shrink to 1x1 in the last cycle");
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, 2) == 3
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(1, 2) == 2
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(2, 2) == 1,
                "the footprint must shrink once per cycle for a small group");
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, 1) == 3
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(1, 1) == 2
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(2, 1) == 1,
                "single-player testing must retain the same staged shrink as the smallest group");
        check(BlackFogSafeZonePolicy.sideForCycleAndPlayers(-1, 2) == 0
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(3, 2) == 0
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, 0) == 0
                        && BlackFogSafeZonePolicy.sideForCycleAndPlayers(0, -1) == 0,
                "invalid cycles or player counts must fail closed with no safe footprint");
        // Every cycle of every supported roster must still leave a usable footprint.
        for (int players = 1; players <= 20; players++) {
            for (int cycle = 0; cycle < 3; cycle++) {
                int side = BlackFogSafeZonePolicy.sideForCycleAndPlayers(cycle, players);
                check(side >= 1, "every cycle must leave at least one safe cell");
                check(BlackFogSafeZonePolicy.cells(10, 20, side).size() == side * side,
                        "a safe footprint must contain exactly side*side cells");
            }
        }
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
