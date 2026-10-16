import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.RealitySplitChamberSnapshot;
import me.copimine.endevent.runtime.RealitySplitChamberController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RealitySplitChamberSnapshotTest {
    public static void main(String[] args) {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000032");
        RealitySplitChamberController controller = new RealitySplitChamberController();
        controller.begin(77L, List.of(first, second));
        int firstRoom = controller.assignment().chamberByPlayer().get(first);
        int secondRoom = controller.assignment().chamberByPlayer().get(second);
        check(controller.markChamberComplete(77L, firstRoom),
                "first chamber completion must be recorded");
        check(controller.markChamberComplete(77L, secondRoom),
                "second chamber completion must be recorded");
        check(controller.openCompletedPassage(77L, firstRoom, secondRoom),
                "completed adjacent chambers must open their passage");

        Map<String, String> encoded = RealitySplitChamberSnapshot.encode(
                77L, controller.assignment(), controller.completedChambers(),
                controller.openPassages());
        check(encoded.containsKey("reality-split.opening"),
                "passage snapshots must explicitly persist pending OPENING intents");
        RealitySplitChamberSnapshot.Data decoded = RealitySplitChamberSnapshot.decode(encoded, 77L);
        check(decoded.assignment().chamberByPlayer().equals(controller.assignment().chamberByPlayer()),
                "player room assignment must survive restart encoding");
        check(decoded.completedChambers().equals(controller.completedChambers()),
                "completed chamber state must survive restart encoding");
        check(decoded.openPassages().equals(controller.openPassages()),
                "opened passage state must survive restart encoding");
        Map<String, String> preOpeningSnapshot = new java.util.LinkedHashMap<>(encoded);
        preOpeningSnapshot.remove("reality-split.opening");
        check(RealitySplitChamberSnapshot.decode(preOpeningSnapshot, 77L).openingPassages().isEmpty(),
                "legacy Wave 7 snapshots without an opening key must remain readable as no pending intent");

        RealitySplitChamberController restored = new RealitySplitChamberController();
        restored.restore(decoded.generation(), decoded.assignment(), decoded.completedChambers(),
                decoded.openPassages());
        check(restored.assignment().chamberByPlayer().equals(controller.assignment().chamberByPlayer()),
                "restored controller must retain exact UUID assignment");
        check(restored.allChambersComplete(77L),
                "restored completed rooms must remain complete");
        check(restored.boundaryOpen(firstRoom, secondRoom),
                "restored opened passage must remain open");

        RealitySplitChamberController openingController = new RealitySplitChamberController();
        openingController.begin(80L, List.of(first, second));
        check(openingController.markChamberComplete(80L, firstRoom),
                "completed chamber must authorize a passage intent");
        check(openingController.beginOpeningPassage(80L, firstRoom, secondRoom),
                "passage intent must be recorded before physical opening");
        Map<String, String> openingEncoded = RealitySplitChamberSnapshot.encode(
                80L, openingController.assignment(), openingController.completedChambers(),
                openingController.openPassages(), openingController.openingPassages());
        RealitySplitChamberSnapshot.Data openingDecoded =
                RealitySplitChamberSnapshot.decode(openingEncoded, 80L);
        RealitySplitChamberController openingRestored = new RealitySplitChamberController();
        openingRestored.restore(openingDecoded.generation(), openingDecoded.assignment(),
                openingDecoded.completedChambers(), openingDecoded.openPassages(),
                openingDecoded.openingPassages());
        check(openingRestored.openingPassages().contains(
                        new RealitySplitChamberController.Passage(firstRoom, secondRoom)),
                "restart must retain an OPENING intent");
        check(!openingRestored.boundaryOpen(firstRoom, secondRoom),
                "OPENING must not grant passage before physical reconciliation");
        check(openingRestored.commitOpeningPassage(80L, firstRoom, secondRoom),
                "reconciliation may commit the passage after physical AIR is verified");
        check(openingRestored.openingPassages().isEmpty()
                        && openingRestored.boundaryOpen(firstRoom, secondRoom),
                "successful reconciliation moves OPENING to OPEN");
        Map<String, String> conflicting = new java.util.LinkedHashMap<>(openingEncoded);
        conflicting.put("reality-split.opening", firstRoom + ":" + secondRoom);
        conflicting.put("reality-split.open", firstRoom + ":" + secondRoom);
        expectInvalid(() -> RealitySplitChamberSnapshot.decode(conflicting, 80L),
                "a passage cannot be persisted as both OPEN and OPENING");
        Map<String, String> invalidOpening = new java.util.LinkedHashMap<>(openingEncoded);
        invalidOpening.put("reality-split.opening", "0:0");
        expectInvalid(() -> RealitySplitChamberSnapshot.decode(invalidOpening, 80L),
                "a self passage must be rejected from a pending opening snapshot");
        Map<String, String> duplicateOpening = new java.util.LinkedHashMap<>(openingEncoded);
        duplicateOpening.put("reality-split.opening", "0:1,1:0");
        expectInvalid(() -> RealitySplitChamberSnapshot.decode(duplicateOpening, 80L),
                "duplicate reversed passage intents must be rejected");

        RealitySplitChamberController disposable = new RealitySplitChamberController();
        disposable.beginDevSolo(78L, List.of(first));
        Map<String, String> disposableEncoded = RealitySplitChamberSnapshot.encodeDisposable(
                78L, disposable.assignment(), disposable.completedChambers(),
                disposable.openPassages());
        RealitySplitChamberSnapshot.Data disposableDecoded =
                RealitySplitChamberSnapshot.decodeDisposable(disposableEncoded, 78L);
        check(disposableDecoded.assignment().chamberCount() == 2,
                "a disposable solo snapshot must retain both physical chambers");
        check(disposableDecoded.assignment().chamberByPlayer().equals(
                        disposable.assignment().chamberByPlayer()),
                "a disposable solo snapshot must retain its one operator assignment");
        RealitySplitChamberController disposableRestored = new RealitySplitChamberController();
        disposableRestored.restoreDisposable(disposableDecoded.generation(),
                disposableDecoded.assignment(), disposableDecoded.completedChambers(),
                disposableDecoded.openPassages());
        check(disposableRestored.assignment().chamberByPlayer().equals(
                        disposable.assignment().chamberByPlayer()),
                "a disposable solo assignment must be restorable after restart");

        RealitySplitChamberController soloOfficial = new RealitySplitChamberController();
        var soloAssignment = ChamberIsolationPolicy.assign(List.of(first));
        Map<String, String> soloEncoded = RealitySplitChamberSnapshot.encode(
                79L, soloAssignment, java.util.Set.of(), java.util.Set.of());
        RealitySplitChamberSnapshot.Data soloDecoded =
                RealitySplitChamberSnapshot.decode(soloEncoded, 79L);
        soloOfficial.restore(79L, soloDecoded.assignment(),
                soloDecoded.completedChambers(), soloDecoded.openPassages());
        check(soloOfficial.assignment().chamberCount() == 1,
                "an official solo run uses one appropriate trial room");
        System.out.println("RealitySplitChamberSnapshotTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectInvalid(Runnable operation, String message) {
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
