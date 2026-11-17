import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.RealitySplitChamberSnapshot;
import me.copimine.endevent.runtime.RealitySplitChamberController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wave 7 topology contract: the official event runs one open arena, so a durable
 * checkpoint with two or four sectors is legacy and must be refused by the
 * official restore instead of being silently collapsed. The two-room passage
 * transaction is therefore exercised through the documented disposable probe,
 * which is the only shape that still creates physical partitions.
 */
public final class RealitySplitChamberSnapshotTest {
    public static void main(String[] args) {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000032");
        RealitySplitChamberController controller = new RealitySplitChamberController();
        controller.beginDevSolo(77L, List.of(first, second));
        int firstRoom = controller.assignment().chamberByPlayer().get(first);
        int secondRoom = controller.assignment().chamberByPlayer().get(second);
        check(controller.assignment().chamberCount() == 2,
                "the disposable probe must build the two physical rooms it inspects");
        check(controller.markChamberComplete(77L, firstRoom),
                "first chamber completion must be recorded");
        check(controller.markChamberComplete(77L, secondRoom),
                "second chamber completion must be recorded");
        check(controller.openCompletedPassage(77L, firstRoom, secondRoom),
                "completed adjacent chambers must open their passage");

        Map<String, String> encoded = RealitySplitChamberSnapshot.encodeDisposable(
                77L, controller.assignment(), controller.completedChambers(),
                controller.openPassages());
        check(encoded.containsKey("reality-split.opening"),
                "passage snapshots must explicitly persist pending OPENING intents");
        RealitySplitChamberSnapshot.Data decoded =
                RealitySplitChamberSnapshot.decodeDisposable(encoded, 77L);
        check(decoded.assignment().chamberByPlayer().equals(controller.assignment().chamberByPlayer()),
                "player room assignment must survive restart encoding");
        check(decoded.completedChambers().equals(controller.completedChambers()),
                "completed chamber state must survive restart encoding");
        check(decoded.openPassages().equals(controller.openPassages()),
                "opened passage state must survive restart encoding");
        Map<String, String> preOpeningSnapshot = new java.util.LinkedHashMap<>(encoded);
        preOpeningSnapshot.remove("reality-split.opening");
        check(RealitySplitChamberSnapshot.decodeDisposable(preOpeningSnapshot, 77L)
                        .openingPassages().isEmpty(),
                "legacy Wave 7 snapshots without an opening key must remain readable as no pending intent");

        RealitySplitChamberController restored = new RealitySplitChamberController();
        restored.restoreDisposable(decoded.generation(), decoded.assignment(),
                decoded.completedChambers(), decoded.openPassages());
        check(restored.assignment().chamberByPlayer().equals(controller.assignment().chamberByPlayer()),
                "restored controller must retain exact UUID assignment");
        check(restored.allChambersComplete(77L),
                "restored completed rooms must remain complete");
        check(restored.boundaryOpen(firstRoom, secondRoom),
                "restored opened passage must remain open");

        // The same bytes are legacy to the official path: it must refuse them
        // rather than drop a durable partition and its progress on the floor.
        RealitySplitChamberSnapshot.Data asDurableCheckpoint = RealitySplitChamberSnapshot.decode(
                RealitySplitChamberSnapshot.encode(77L, controller.assignment(),
                        controller.completedChambers(), controller.openPassages()), 77L);
        RealitySplitChamberController officialRefusal = new RealitySplitChamberController();
        String refusal = expectRefusal(() -> officialRefusal.restore(
                        asDurableCheckpoint.generation(), asDurableCheckpoint.assignment(),
                        asDurableCheckpoint.completedChambers(), asDurableCheckpoint.openPassages()),
                "the official restore must refuse a legacy two-sector Wave 7 checkpoint");
        check("WAVE7_LEGACY_PARTITIONS_REQUIRE_EXPLICIT_MIGRATION".equals(refusal),
                "the refusal must name the explicit migration requirement, got: " + refusal);
        check(officialRefusal.assignment().chamberCount() == 0 && !officialRefusal.owns(77L),
                "a refused legacy checkpoint must not publish its partition");
        RealitySplitChamberController migrated = new RealitySplitChamberController();
        migrated.restoreDisposable(asDurableCheckpoint.generation(), asDurableCheckpoint.assignment(),
                asDurableCheckpoint.completedChambers(), asDurableCheckpoint.openPassages());
        check(migrated.completedChambers().equals(Set.of(firstRoom, secondRoom))
                        && migrated.boundaryOpen(firstRoom, secondRoom),
                "the refused data must stay intact and restorable through the documented probe");

        RealitySplitChamberController openingController = new RealitySplitChamberController();
        openingController.beginDevSolo(80L, List.of(first, second));
        check(openingController.markChamberComplete(80L, firstRoom),
                "completed chamber must authorize a passage intent");
        check(openingController.beginOpeningPassage(80L, firstRoom, secondRoom),
                "passage intent must be recorded before physical opening");
        Map<String, String> openingEncoded = RealitySplitChamberSnapshot.encodeDisposable(
                80L, openingController.assignment(), openingController.completedChambers(),
                openingController.openPassages(), openingController.openingPassages());
        RealitySplitChamberSnapshot.Data openingDecoded =
                RealitySplitChamberSnapshot.decodeDisposable(openingEncoded, 80L);
        RealitySplitChamberController openingRestored = new RealitySplitChamberController();
        openingRestored.restoreDisposable(openingDecoded.generation(), openingDecoded.assignment(),
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
        expectInvalid(() -> RealitySplitChamberSnapshot.decodeDisposable(conflicting, 80L),
                "a passage cannot be persisted as both OPEN and OPENING");
        Map<String, String> invalidOpening = new java.util.LinkedHashMap<>(openingEncoded);
        invalidOpening.put("reality-split.opening", "0:0");
        expectInvalid(() -> RealitySplitChamberSnapshot.decodeDisposable(invalidOpening, 80L),
                "a self passage must be rejected from a pending opening snapshot");
        Map<String, String> duplicateOpening = new java.util.LinkedHashMap<>(openingEncoded);
        duplicateOpening.put("reality-split.opening", "0:1,1:0");
        expectInvalid(() -> RealitySplitChamberSnapshot.decodeDisposable(duplicateOpening, 80L),
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

        // The official path: one open arena, no interior boundary, so no passage
        // can be encoded at all and the arena restore is the only legal shape.
        var openArena = ChamberIsolationPolicy.assignOpenArena(List.of(first, second));
        check(openArena.chamberCount() == 1,
                "the official Wave 7 assignment must be one open arena");
        Map<String, String> arenaEncoded = RealitySplitChamberSnapshot.encode(
                81L, openArena, Set.of(0), Set.of());
        RealitySplitChamberSnapshot.Data arenaDecoded =
                RealitySplitChamberSnapshot.decode(arenaEncoded, 81L);
        RealitySplitChamberController arenaRestored = new RealitySplitChamberController();
        arenaRestored.restore(arenaDecoded.generation(), arenaDecoded.assignment(),
                arenaDecoded.completedChambers(), arenaDecoded.openPassages());
        check(arenaRestored.assignment().chamberCount() == 1
                        && arenaRestored.allChambersComplete(81L)
                        && arenaRestored.openPassages().isEmpty()
                        && arenaRestored.openingPassages().isEmpty(),
                "the official arena checkpoint must restore one completed arena and no passage");
        expectInvalid(() -> RealitySplitChamberSnapshot.encode(81L, openArena, Set.of(),
                        Set.of(new RealitySplitChamberController.Passage(0, 1))),
                "a one-arena snapshot cannot encode an interior passage");

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

    private static String expectRefusal(Runnable operation, String message) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return expected.getMessage();
        }
        throw new AssertionError(message);
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
