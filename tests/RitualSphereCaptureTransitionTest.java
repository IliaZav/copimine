import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.RitualSealCapturePolicy;
import me.copimine.endevent.domain.RitualSphereEncounterPolicy;
import me.copimine.endevent.runtime.EncounterContext;
import me.copimine.endevent.runtime.encounter.RitualSphereEncounter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RitualSphereCaptureTransitionTest {
    public static void main(String[] args) {
        UUID prisoner = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff0");
        UUID outsideLowest = UUID.fromString("00000000-0000-0000-0000-000000000001");
        EncounterContext context = EncounterContext.forWave(
                "ritual-capture-test", 7L, "CopiMine", 0, 64, 0,
                new EncounterContext.ArenaBounds(-20, 0, -20, 20, 100, 20),
                Set.of(prisoner, outsideLowest), Set.of(prisoner, outsideLowest), 6);
        RitualSphereEncounter encounter = new RitualSphereEncounter();

        check(encounter.start(context).status() ==
                        me.copimine.endevent.runtime.encounter.WaveEncounter.Status.STARTED,
                "Ritual Sphere must start");
        check(encounter.state() != null && !RitualSphereEncounterPolicy.hasCaptured(encounter.state()),
                "Wave 6 must start in an explicit waiting state before physical seal capture");

        long capturedAt = 100_000L;
        check(encounter.capture(context, List.of(
                new RitualSealCapturePolicy.Candidate(outsideLowest, true, 4.0D, 4.0D)
        ), 0.0D, 0.0D, capturedAt).accepted(),
                "an outside participant must leave the encounter waiting");
        check(encounter.state() != null && !RitualSphereEncounterPolicy.hasCaptured(encounter.state()),
                "an outside participant must leave the authoritative state waiting");
        check(!RitualSphereEncounterPolicy.drainDue(encounter.state(), capturedAt + 20_000L),
                "a waiting encounter must not make a drain due");

        check(encounter.capture(context, List.of(
                new RitualSealCapturePolicy.Candidate(prisoner, true, 1.25D, 0.0D)
        ), 0.0D, 0.0D, capturedAt).accepted(),
                "an eligible participant on the seal boundary must be captured");
        check(encounter.state() != null, "capture must create prisoner state");
        check(prisoner.equals(encounter.state().prisoner()),
                "capture must persist the physically captured UUID");
        check(encounter.state().lastDrainMillis() == capturedAt,
                "the first drain clock must start at capture time");
        check(!RitualSphereEncounterPolicy.drainDue(encounter.state(), capturedAt + 19_999L),
                "the first drain must not happen before twenty seconds after capture");
        check(RitualSphereEncounterPolicy.drainDue(encounter.state(), capturedAt + 20_000L),
                "the first drain must happen at the twenty-second boundary after capture");

        check(encounter.capture(context, List.of(
                new RitualSealCapturePolicy.Candidate(outsideLowest, true, 0.0D, 0.0D)
        ), 0.0D, 0.0D, capturedAt + 1_000L).accepted(),
                "a repeated capture attempt must remain idempotently accepted");
        check(prisoner.equals(encounter.state().prisoner()),
                "a second player must not replace the captured prisoner");

        System.out.println("RitualSphereCaptureTransitionTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
