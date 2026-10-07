import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.runtime.RealitySplitTrialController;
import me.copimine.endevent.runtime.RealitySplitTrialSnapshot;

public final class RealitySplitTrialSnapshotTest {
    public static void main(String[] args) {
        List<UUID> players = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"));
        RealitySplitTrialController source = new RealitySplitTrialController();
        source.begin(73L, ChamberIsolationPolicy.assign(players));
        source.hitReflectionSeal(73L, 1, 0, true);
        source.hitJuggernautAnchor(73L, 2, 0, true);

        Map<String, String> encoded = RealitySplitTrialSnapshot.encode(73L, source.snapshot());
        RealitySplitTrialSnapshot.Data decoded = RealitySplitTrialSnapshot.decode(encoded, 73L);
        check(decoded.trials().get(1).trial() == RealitySplitTrialController.Trial.RIFT_REFLECTION,
                "snapshot keeps the assigned trial identity");
        check(decoded.trials().get(1).progress() == 1,
                "snapshot keeps the active Reflection seal index");
        check(decoded.trials().get(2).progress() == 1,
                "snapshot keeps the active Juggernaut armor index");

        RealitySplitTrialController restored = new RealitySplitTrialController();
        restored.restore(73L, ChamberIsolationPolicy.assign(players), decoded.trials());
        check(restored.trial(1).progress() == 1 && restored.trial(2).progress() == 1,
                "restore rehydrates in-progress trials for the same generation");

        boolean staleRejected = false;
        try {
            RealitySplitTrialSnapshot.decode(encoded, 74L);
        } catch (IllegalArgumentException expected) {
            staleRejected = true;
        }
        check(staleRejected, "a stale snapshot cannot be restored into a later generation");

        boolean malformedRejected = false;
        try {
            RealitySplitTrialSnapshot.decode(Map.of(
                    "reality-split-trial.generation", "73",
                    "reality-split-trial.0", "RIFT_HUNTER:ACTIVE:99"), 73L);
        } catch (IllegalArgumentException expected) {
            malformedRejected = true;
        }
        check(malformedRejected, "invalid trial progress is rejected instead of clamped");
        System.out.println("RealitySplitTrialSnapshotTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
