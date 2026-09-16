import me.copimine.endevent.domain.RitualCasterShieldPolicy;

public final class RitualCasterShieldPolicyTest {
    public static void main(String[] args) {
        check(RitualCasterShieldPolicy.state(true, 3)
                        == RitualCasterShieldPolicy.State.FULL,
                "three living guards must provide a full shield");
        check(RitualCasterShieldPolicy.state(true, 2)
                        == RitualCasterShieldPolicy.State.WEAKENED,
                "two living guards must provide a weakened shield");
        check(RitualCasterShieldPolicy.state(true, 1)
                        == RitualCasterShieldPolicy.State.CRITICAL,
                "one living guard must provide a critical shield");
        check(RitualCasterShieldPolicy.state(true, 0)
                        == RitualCasterShieldPolicy.State.BROKEN,
                "the shield must break only after all guards die");
        check(RitualCasterShieldPolicy.state(false, 3)
                        == RitualCasterShieldPolicy.State.DEAD,
                "a dead caster cannot keep a shield");
        check(RitualCasterShieldPolicy.blocksDamage(true, 1),
                "protected caster damage must be blocked");
        check(!RitualCasterShieldPolicy.blocksDamage(true, 0),
                "unshielded caster must be damageable");
        check(!RitualCasterShieldPolicy.blocksDamage(false, 3),
                "dead caster must not be treated as a live damage target");
        System.out.println("RitualCasterShieldPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
