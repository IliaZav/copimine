import me.copimine.endevent.domain.PortalCapturePolicy;
import me.copimine.endevent.domain.Wave3PortalProgressSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Wave3PortalProgressSnapshotTest {
    public static void main(String[] args) {
        List<PortalCapturePolicy.PortalState> states = List.of(
                new PortalCapturePolicy.PortalState(true, 5_000L, -1L, -1L),
                new PortalCapturePolicy.PortalState(false, 2_500L, -1L, -1L),
                PortalCapturePolicy.initial());
        Map<String, String> encoded = Wave3PortalProgressSnapshot.encode("event-3", 8L, states, true);
        Wave3PortalProgressSnapshot.Data decoded = Wave3PortalProgressSnapshot.decode(
                encoded, "event-3", 8L);
        check(decoded != null, "a complete checkpoint must decode");
        check(decoded.eliteSpawned(), "the elite-spawned flag must survive a restart");
        check(decoded.states().equals(states), "completed and partial capture progress must round-trip");
        check(decoded.states().get(1).lastOccupiedMillis() == -1L
                        && decoded.states().get(1).lastUpdateMillis() == -1L,
                "restored progress must rebase its occupancy timestamps");

        check(Wave3PortalProgressSnapshot.decode(Map.of(), "event-3", 8L) == null,
                "a legacy snapshot without Wave 3 keys must not fail");
        check(Wave3PortalProgressSnapshot.encode("event-3", 8L, List.of(), false).isEmpty(),
                "an objective that has not created portals has no checkpoint");

        expectFailure(() -> Wave3PortalProgressSnapshot.decode(encoded, "other-event", 8L),
                "a checkpoint from another event must be refused");
        expectFailure(() -> Wave3PortalProgressSnapshot.decode(encoded, "event-3", 9L),
                "a checkpoint from another generation must be refused");

        expectFailure(() -> Wave3PortalProgressSnapshot.encode("event-3", 8L,
                        states.subList(0, 2), false),
                "a partial portal list must never be encoded");
        expectFailure(() -> Wave3PortalProgressSnapshot.encode("event-3", 8L,
                        List.of(new PortalCapturePolicy.PortalState(true, 4_999L, -1L, -1L),
                                PortalCapturePolicy.initial(), PortalCapturePolicy.initial()), false),
                "completed must mean exactly five seconds of progress");
        expectFailure(() -> Wave3PortalProgressSnapshot.encode("event-3", 8L,
                        List.of(new PortalCapturePolicy.PortalState(false, 5_000L, -1L, -1L),
                                PortalCapturePolicy.initial(), PortalCapturePolicy.initial()), false),
                "five seconds of progress must be completed");

        Map<String, String> missingPortal = new LinkedHashMap<>(encoded);
        missingPortal.remove(Wave3PortalProgressSnapshot.PREFIX + "1.progress");
        expectFailure(() -> Wave3PortalProgressSnapshot.decode(missingPortal, "event-3", 8L),
                "a truncated checkpoint must fail closed");
        Map<String, String> missingElite = new LinkedHashMap<>(encoded);
        missingElite.remove(Wave3PortalProgressSnapshot.PREFIX + "elite-spawned");
        expectFailure(() -> Wave3PortalProgressSnapshot.decode(missingElite, "event-3", 8L),
                "a checkpoint without the elite flag must fail closed");

        System.out.println("Wave3PortalProgressSnapshotTest OK");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}