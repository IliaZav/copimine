import me.copimine.endevent.domain.RitualPrisonerHealthPolicy;

public final class RitualPrisonerHealthPolicyTest {
    public static void main(String[] args) {
        RitualPrisonerHealthPolicy.DrainResult first = RitualPrisonerHealthPolicy.drain(20.0D);
        check(first.appliedDamage() == 2.0D, "one drain must remove exactly two HP");
        check(first.remainingHealth() == 18.0D, "drain must subtract one heart");
        check(first.intensityGain() == 1, "a successful drain must increase intensity once");

        RitualPrisonerHealthPolicy.DrainResult floor = RitualPrisonerHealthPolicy.drain(1.5D);
        check(floor.remainingHealth() == RitualPrisonerHealthPolicy.MIN_HEALTH,
                "drain must stop at one HP");
        check(floor.appliedDamage() == 0.5D,
                "drain must apply only the non-lethal remainder at the floor");
        RitualPrisonerHealthPolicy.DrainResult stopped = RitualPrisonerHealthPolicy.drain(1.0D);
        check(stopped.remainingHealth() == 1.0D && stopped.appliedDamage() == 0.0D,
                "further drains must not reduce the floor");
        check(stopped.intensityGain() == 0,
                "intensity must not grow after the prisoner reaches the floor");

        check(RitualPrisonerHealthPolicy.drainDue(20_000L, 0L),
                "drain must be due after twenty seconds");
        check(!RitualPrisonerHealthPolicy.drainDue(19_999L, 0L),
                "drain must not fire early");
        check(RitualPrisonerHealthPolicy.drainDue(60_000L, 40_000L),
                "cadence must work for subsequent ticks");
        check(RitualPrisonerHealthPolicy.safeExternalDamage(20.0D, 99.0D) == 0.0D,
                "captured prisoner must ignore all non-ritual external damage");
        check(RitualPrisonerHealthPolicy.safeExternalDamage(5.0D, 1.0D) == 0.0D,
                "small external hits must also deal zero damage while captured");
        check(RitualPrisonerHealthPolicy.safeExternalDamage(1.0D, 99.0D) == 0.0D,
                "external damage must remain zero at the ritual floor");
        System.out.println("RitualPrisonerHealthPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
