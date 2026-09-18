import java.util.UUID;
import me.copimine.endevent.domain.RitualSphereEncounterPolicy;
import me.copimine.endevent.domain.RitualSphereScalingPolicy;

public final class RitualSphereEncounterPolicyTest {
    public static void main(String[] args) {
        UUID prisoner = UUID.randomUUID();
        RitualSphereEncounterPolicy.State initial = RitualSphereEncounterPolicy.initial(
                7L, prisoner, 20, 1_000L);
        check(initial.profile().casterCount() == 6, "20 players must use six casters");
        check(initial.profile().guardCount() == 18, "every caster must have three guards");
        check(!RitualSphereEncounterPolicy.drainDue(initial, 20_999L),
                "drain must not happen before 20 seconds");

        RitualSphereEncounterPolicy.DrainTransition first = RitualSphereEncounterPolicy.advanceDrain(
                initial, 20.0D, 21_000L);
        check(first.applied(), "first due drain must apply");
        check(first.health().remainingHealth() == 18.0D, "drain must remove exactly two HP");
        check(first.state().successfulDrains() == 1, "successful drain raises intensity once");
        check(first.state().intensity() == 1, "intensity must track successful drains");

        RitualSphereEncounterPolicy.State floor = first.state();
        for (int index = 0; index < 20; index++) {
            RitualSphereEncounterPolicy.DrainTransition next = RitualSphereEncounterPolicy.advanceDrain(
                    floor, 1.0D, 41_000L + index * 20_000L);
            floor = next.state();
        }
        check(floor.intensity() == 1, "floor drains must not raise intensity");
        check(floor.successfulDrains() == 1, "floor drains must not count as successful");

        check(RitualSphereEncounterPolicy.abilityEnabled(initial, 0, true),
                "first caster owns the projectile ability");
        check(!RitualSphereEncounterPolicy.abilityEnabled(initial, 0, false),
                "dead caster disables its ability");
        check(!RitualSphereEncounterPolicy.abilityEnabled(initial, 5, true),
                "amplifier casters must not create a sixth spell type");
        check(!RitualSphereEncounterPolicy.shouldComplete(1, 0),
                "living caster keeps the ritual active");
        check(RitualSphereEncounterPolicy.shouldComplete(0, 0),
                "ritual completes only when caster and guards are gone");
        check(RitualSphereScalingPolicy.projectileDamageMultiplier(100) <= 1.20D,
                "intensity scaling must stay bounded");
        System.out.println("RitualSphereEncounterPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
