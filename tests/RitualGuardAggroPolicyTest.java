import me.copimine.endevent.domain.RitualGuardAggroPolicy;

public final class RitualGuardAggroPolicyTest {
    public static void main(String[] args) {
        check(RitualGuardAggroPolicy.shouldWake(2.9D, false, false, false),
                "near-caster player must wake the local guard group");
        check(RitualGuardAggroPolicy.shouldWake(20.0D, true, false, false),
                "melee attack on caster must wake its local group");
        check(RitualGuardAggroPolicy.shouldWake(20.0D, false, true, false),
                "attack on a guard must wake its local group");
        check(RitualGuardAggroPolicy.shouldWake(20.0D, false, false, true),
                "ranged attack must wake its local group");
        check(!RitualGuardAggroPolicy.shouldWake(3.1D, false, false, false),
                "a distant idle player must not globally wake guards");
        check(RitualGuardAggroPolicy.withinLeash(0.0D), "caster origin is inside leash");
        check(RitualGuardAggroPolicy.withinLeash(RitualGuardAggroPolicy.LEASH_RADIUS_BLOCKS),
                "leash boundary is inclusive");
        check(!RitualGuardAggroPolicy.withinLeash(
                        RitualGuardAggroPolicy.LEASH_RADIUS_BLOCKS + 0.01D),
                "guards must not chase outside their bounded leash");
        System.out.println("RitualGuardAggroPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
