import me.copimine.endevent.domain.RitualSealCapturePolicy;
import me.copimine.endevent.domain.RitualSphereEncounterPolicy;
import me.copimine.endevent.domain.RitualSphereScalingPolicy;

import java.util.UUID;

public final class RitualSphereAuthoritativeStateTest {
    public static void main(String[] args) {
        UUID prisoner = UUID.fromString("00000000-0000-0000-0000-000000000001");

        RitualSphereEncounterPolicy.State waiting = RitualSphereEncounterPolicy.waiting(
                7L, 4);
        check(waiting.prisoner() == null,
                "a started Wave 6 encounter must have no prisoner before seal entry");
        check(waiting.lastDrainMillis() < 0L,
                "waiting state must not expose a drain timestamp");
        check(!RitualSphereEncounterPolicy.drainDue(waiting, 120_000L),
                "waiting for seal capture must never make a drain due");

        RitualSphereEncounterPolicy.State captured = RitualSphereEncounterPolicy.capture(
                waiting, prisoner, 100_000L);
        check(captured.prisoner().equals(prisoner),
                "capture must persist the physically captured prisoner");
        check(captured.lastDrainMillis() == 100_000L,
                "capture time must become the first-drain clock");
        check(!RitualSphereEncounterPolicy.drainDue(captured, 119_999L),
                "first drain must not happen before twenty seconds after capture");
        check(RitualSphereEncounterPolicy.drainDue(captured, 120_000L),
                "first drain must happen at the twenty-second boundary");

        RitualSphereEncounterPolicy.State recaptured = RitualSphereEncounterPolicy.capture(
                captured, UUID.randomUUID(), 200_000L);
        check(recaptured.prisoner().equals(prisoner),
                "a second capture must not replace the authoritative prisoner");
        check(recaptured.lastDrainMillis() == captured.lastDrainMillis(),
                "a second capture must not reset the drain clock");
        UUID replacement = UUID.fromString("00000000-0000-0000-0000-000000000002");
        RitualSphereEncounterPolicy.State reassigned =
                RitualSphereEncounterPolicy.reassignCapturedPrisoner(captured, replacement);
        check(reassigned.prisoner().equals(replacement),
                "a lifecycle replacement must update the policy-owned prisoner");
        check(reassigned.lastDrainMillis() == captured.lastDrainMillis()
                        && reassigned.successfulDrains() == captured.successfulDrains()
                        && reassigned.profile().equals(captured.profile()),
                "prisoner reassignment must preserve drain clock and scaling state");
        check(captured.profile().equals(RitualSphereScalingPolicy.forPlayers(4)),
                "waiting state must retain the configured scaling profile");

        check(RitualSealCapturePolicy.CAPTURE_RADIUS_BLOCKS == 1.25D,
                "state test must use the physical seal capture contract");
        System.out.println("RitualSphereAuthoritativeStateTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
