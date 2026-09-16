import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.runtime.RealitySplitChamberController;

public final class RealitySplitChamberControllerTest {
    public static void main(String[] args) {
        UUID playerA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID playerC = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID playerD = UUID.fromString("00000000-0000-0000-0000-000000000004");

        RealitySplitChamberController controller = new RealitySplitChamberController();
        controller.begin(42L, List.of(playerA, playerB, playerC, playerD));
        for (int chamber = 0; chamber < 4; chamber++) {
            check(controller.markChamberComplete(42L, chamber),
                    "each chamber must be completable before passage opening");
        }

        check(!controller.openCompletedPassage(42L, 0, 2),
                "a diagonal pair must not become a logical Wave 7 passage");
        check(!controller.boundaryOpen(0, 2),
                "a diagonal pair must remain closed after the objective tick");
        controller.openBoundary(42L, 0, 2);
        check(!controller.boundaryOpen(0, 2),
                "the low-level boundary API must not record a diagonal passage");
        check(controller.openCompletedPassage(42L, 0, 1),
                "an adjacent completed pair must open its logical passage");
        check(controller.boundaryOpen(0, 1),
                "the adjacent passage must be recorded as open");

        RealitySplitChamberController solo = new RealitySplitChamberController();
        solo.beginDevSolo(43L, List.of(playerA));
        check(solo.assignment().chamberCount() == 2,
                "the disposable solo probe must still build two physical chambers");
        check(solo.assignment().chamberByPlayer().get(playerA) == 0,
                "the solo probe player must have one deterministic chamber assignment");

        UUID mob = UUID.fromString("00000000-0000-0000-0000-000000000011");
        check(solo.assignedEntityCountForChamber(0) == 0,
                "a chamber with no successful spawn must not look cleared");
        check(solo.assignEntity(43L, mob, 0),
                "a successfully spawned mob must be assigned to its chamber");
        check(solo.assignedEntityCountForChamber(0) == 1,
                "the controller must retain evidence that a chamber had a spawn");
        check(solo.assignedEntityCountForChamber(1) == 0,
                "spawn evidence must remain chamber-local");

        boolean rejectedDiagonalRestore = false;
        try {
            controller.restore(42L, controller.assignment(), Set.of(),
                    Set.of(new RealitySplitChamberController.Passage(0, 2)));
        } catch (IllegalArgumentException expected) {
            rejectedDiagonalRestore = true;
        }
        check(rejectedDiagonalRestore,
                "a restored Wave 7 graph must reject a diagonal passage");

        controller.openAllBoundariesAfterObjective(42L);
        for (int first = 0; first < 4; first++) {
            for (int second = first + 1; second < 4; second++) {
                boolean adjacent = (first + 1) % 4 == second || (second + 1) % 4 == first;
                check(controller.boundaryOpen(first, second) == adjacent,
                        "objective merge must open only physical adjacent passages: "
                                + first + "-" + second);
            }
        }
        System.out.println("RealitySplitChamberControllerTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
