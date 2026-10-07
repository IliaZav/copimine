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
        RitualSphereEncounterPolicy.State captured = RitualSphereEncounterPolicy.capture(waiting, prisoner);
        check(captured.prisoner().equals(prisoner),
                "capture must persist the physically captured prisoner");

        RitualSphereEncounterPolicy.State recaptured = RitualSphereEncounterPolicy.capture(captured, UUID.randomUUID());
        check(recaptured.prisoner().equals(prisoner),
                "a second capture must not replace the authoritative prisoner");
        UUID replacement = UUID.fromString("00000000-0000-0000-0000-000000000002");
        RitualSphereEncounterPolicy.State reassigned =
                RitualSphereEncounterPolicy.reassignCapturedPrisoner(captured, replacement);
        check(reassigned.prisoner().equals(replacement),
                "a lifecycle replacement must update the policy-owned prisoner");
        check(reassigned.profile().equals(captured.profile()),
                "prisoner reassignment must preserve encounter scaling");
        check(captured.profile().equals(RitualSphereScalingPolicy.forPlayers(4)),
                "waiting state must retain the configured scaling profile");

        check(RitualSealCapturePolicy.CAPTURE_RADIUS_BLOCKS == 2.35D,
                "state test must match the visible 2.35 block sphere");
        System.out.println("RitualSphereAuthoritativeStateTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
