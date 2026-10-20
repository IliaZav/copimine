import java.util.UUID;
import me.copimine.endevent.domain.RitualSphereEncounterPolicy;
import me.copimine.endevent.domain.RitualSphereScalingPolicy;

public final class RitualSphereEncounterPolicyTest {
    public static void main(String[] args) {
        UUID prisoner = UUID.randomUUID();
        RitualSphereEncounterPolicy.State initial = RitualSphereEncounterPolicy.waiting(7L, 20);
        check(initial.profile().casterCount() == 5, "20 players must use exactly five casters");
        check(initial.profile().guardCount() == 15, "large groups use three guards per caster");
        RitualSphereEncounterPolicy.State captured = RitualSphereEncounterPolicy.capture(initial, prisoner);
        check(RitualSphereEncounterPolicy.hasCaptured(captured), "capture persists the prisoner");
        check(captured.prisoner().equals(prisoner), "capture stores the selected prisoner");
        check(RitualSphereEncounterPolicy.capture(captured, UUID.randomUUID()) == captured,
                "repeat capture must not replace the current prisoner");
        check(RitualSphereEncounterPolicy.reassignCapturedPrisoner(captured, prisoner) == captured,
                "same-prisoner reassignment is idempotent");
        check(!RitualSphereEncounterPolicy.hasCaptured(initial), "waiting has no prisoner");
        check(!RitualSphereEncounterPolicy.shouldComplete(1, 0),
                "living caster keeps the ritual active");
        check(RitualSphereEncounterPolicy.shouldComplete(0, 0),
                "ritual completes only when caster and guards are gone");
        check(!RitualSphereEncounterPolicy.shouldComplete(0, 1),
                "living guard keeps the ritual active");
        System.out.println("RitualSphereEncounterPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
