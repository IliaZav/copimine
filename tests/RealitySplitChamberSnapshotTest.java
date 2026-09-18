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
        RealitySplitChamberSnapshot.Data decoded = RealitySplitChamberSnapshot.decode(encoded, 77L);
        check(decoded.assignment().chamberByPlayer().equals(controller.assignment().chamberByPlayer()),
                "player room assignment must survive restart encoding");
        check(decoded.completedChambers().equals(controller.completedChambers()),
                "completed chamber state must survive restart encoding");
        check(decoded.openPassages().equals(controller.openPassages()),
                "opened passage state must survive restart encoding");

        RealitySplitChamberController restored = new RealitySplitChamberController();
        restored.restore(decoded.generation(), decoded.assignment(), decoded.completedChambers(),
                decoded.openPassages());
        check(restored.assignment().chamberByPlayer().equals(controller.assignment().chamberByPlayer()),
                "restored controller must retain exact UUID assignment");
        check(restored.allChambersComplete(77L),
                "restored completed rooms must remain complete");
        check(restored.boundaryOpen(firstRoom, secondRoom),
                "restored opened passage must remain open");

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
        System.out.println("RealitySplitChamberSnapshotTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
