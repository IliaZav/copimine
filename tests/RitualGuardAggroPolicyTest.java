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
        check(RitualGuardAggroPolicy.stance(false, false, 0.2D)
                        == RitualGuardAggroPolicy.Stance.HOLD,
                "an unprovoked guard holds its post instead of wandering");
        check(RitualGuardAggroPolicy.stance(false, false, 4.0D)
                        == RitualGuardAggroPolicy.Stance.RETURN,
                "a guard displaced by combat returns to its own caster post");
        check(RitualGuardAggroPolicy.stance(true, false, 4.0D)
                        == RitualGuardAggroPolicy.Stance.INTERCEPT,
                "a nearby player or actual attack enables real combat");
        check(RitualGuardAggroPolicy.stance(true, true, 0.0D)
                        == RitualGuardAggroPolicy.Stance.CAST,
                "a casting guard plants its feet during the dodgeable telegraph");
        for (int count = 1; count <= 3; count++) {
            for (int slot = 0; slot < count; slot++) {
                var post = RitualGuardAggroPolicy.postOffset(slot, count, 0.0D);
                check(Math.abs(Math.hypot(post.x(), post.z()) - 2.4D) < 1.0E-9D,
                        "each assigned guard has a stable post 2.4 blocks from its caster");
                check(post.x() > 0.0D,
                        "posts form an outward-facing screen, away from the central ritual");
                var rotated = RitualGuardAggroPolicy.postOffset(slot, count, Math.PI / 2.0D);
                check(Math.abs(rotated.x() + post.z()) < 1.0E-9D
                                && Math.abs(rotated.z() - post.x()) < 1.0E-9D,
                        "posts rotate with the caster group, not a global shared location");
            }
        }
        System.out.println("RitualGuardAggroPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
