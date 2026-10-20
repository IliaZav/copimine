import me.copimine.endevent.domain.RitualCasterShieldPolicy;

public final class RitualCasterShieldPolicyTest {
    public static void main(String[] args) {
        check(state(true, 3, 3)
                        == RitualCasterShieldPolicy.State.FULL,
                "three living guards must provide a full shield");
        check(state(true, 2, 3)
                        == RitualCasterShieldPolicy.State.WEAKENED,
                "two living guards must provide a weakened shield");
        check(state(true, 1, 3)
                        == RitualCasterShieldPolicy.State.CRITICAL,
                "one living guard must provide a critical shield");
        check(state(true, 0, 3)
                        == RitualCasterShieldPolicy.State.BROKEN,
                "the shield must break only after all guards die");
        check(state(false, 3, 3)
                        == RitualCasterShieldPolicy.State.DEAD,
                "a dead caster cannot keep a shield");
        check(state(true, 1, 1) == RitualCasterShieldPolicy.State.FULL,
                "one surviving guard must be full when the selected profile has one guard");
        check(state(true, 0, 1) == RitualCasterShieldPolicy.State.BROKEN,
                "a one-guard profile must break only after its guard dies");
        check(state(true, 2, 2) == RitualCasterShieldPolicy.State.FULL,
                "two surviving guards must be full in a two-guard profile");
        check(state(true, 1, 2) == RitualCasterShieldPolicy.State.WEAKENED,
                "one surviving guard must be weakened in a two-guard profile");
        check(blocksDamage(true, 1, 1),
                "protected caster damage must be blocked");
        check(!blocksDamage(true, 0, 1),
                "unshielded caster must be damageable");
        check(!blocksDamage(false, 3, 3),
                "dead caster must not be treated as a live damage target");
        System.out.println("RitualCasterShieldPolicyTest OK");
    }

    private static RitualCasterShieldPolicy.State state(boolean casterAlive, int livingGuards,
                                                          int expectedGuards) {
        try {
            return (RitualCasterShieldPolicy.State) RitualCasterShieldPolicy.class
                    .getMethod("state", boolean.class, int.class, int.class)
                    .invoke(null, casterAlive, livingGuards, expectedGuards);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("shield stage must accept the selected profile guard total", error);
        }
    }

    private static boolean blocksDamage(boolean casterAlive, int livingGuards, int expectedGuards) {
        try {
            return (boolean) RitualCasterShieldPolicy.class
                    .getMethod("blocksDamage", boolean.class, int.class, int.class)
                    .invoke(null, casterAlive, livingGuards, expectedGuards);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("shield protection must use the selected profile guard total", error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
