import me.copimine.endevent.domain.RitualGuardAggroPolicy;

public final class RitualGuardAggroPolicyTest {
    public static void main(String[] args) {
        check(RitualGuardAggroPolicy.shouldWake(5.9D, false, false, false),
                "inner six-block threat radius must wake the local guard group");
        check(RitualGuardAggroPolicy.shouldWake(20.0D, true, false, false),
                "melee attack on caster must wake its local group");
        check(RitualGuardAggroPolicy.shouldWake(20.0D, false, true, false),
                "attack on a guard must wake its local group");
        check(RitualGuardAggroPolicy.shouldWake(20.0D, false, false, true),
                "ranged attack must wake its local group");
        check(!RitualGuardAggroPolicy.shouldWake(6.1D, false, false, false),
                "an unengaged player outside the inner radius must not wake guards");
        for (double distance : new double[]{2.0D, 3.0D, 5.0D, 6.0D}) {
            check(RitualGuardAggroPolicy.shouldWake(distance, false, false, false),
                    "unengaged target wakes the local group at " + distance + " blocks");
        }
        for (double distance : new double[]{6.01D, 8.0D, 12.0D, 14.0D}) {
            check(!RitualGuardAggroPolicy.shouldWake(distance, false, false, false),
                    "unengaged target stays outside detection at " + distance + " blocks");
        }
        check(RitualGuardAggroPolicy.shouldWake(9.9D, false, false, false, true),
                "an engaged guard retains its target through the wider hysteresis band");
        check(!RitualGuardAggroPolicy.shouldWake(10.1D, false, false, false, true),
                "an engaged guard disengages beyond the bounded outer radius");
        for (double distance : new double[]{2.0D, 3.0D, 5.0D, 6.0D, 8.0D, 10.0D}) {
            check(RitualGuardAggroPolicy.shouldWake(distance, false, false, false, true),
                    "engaged target remains committed at " + distance + " blocks");
        }
        for (double distance : new double[]{10.01D, 12.0D, 14.0D}) {
            check(!RitualGuardAggroPolicy.shouldWake(distance, false, false, false, true),
                    "engaged target is released beyond the retention boundary at " + distance + " blocks");
        }
        check(RitualGuardAggroPolicy.withinLeash(0.0D), "caster origin is inside leash");
        check(RitualGuardAggroPolicy.withinLeash(RitualGuardAggroPolicy.LEASH_RADIUS_BLOCKS),
                "leash boundary is inclusive");
        check(!RitualGuardAggroPolicy.withinLeash(
                        RitualGuardAggroPolicy.LEASH_RADIUS_BLOCKS + 0.01D),
                "guards must not chase outside their bounded leash");
        check(RitualGuardAggroPolicy.withinLeash(14.0D)
                        && !RitualGuardAggroPolicy.withinLeash(14.01D),
                "home leash accepts 14 blocks and rejects positions beyond it");
        check(RitualGuardAggroPolicy.nextReturnPathAttempt(0) == 1
                        && RitualGuardAggroPolicy.nextReturnPathAttempt(2)
                        == RitualGuardAggroPolicy.RETURN_PATH_MAX_REQUESTS
                        && RitualGuardAggroPolicy.nextReturnPathAttempt(99)
                        == RitualGuardAggroPolicy.RETURN_PATH_MAX_REQUESTS,
                "failed return path requests saturate instead of overflowing or permanently exhausting");
        check(RitualGuardAggroPolicy.returnPathRetryDelayMillis(2) == 1_000L
                        && RitualGuardAggroPolicy.returnPathRetryDelayMillis(3) == 5_000L,
                "repeated return path failures use a bounded retry backoff");
        check(RitualGuardAggroPolicy.mayRequestReturnPath(1_000L, 1_000L)
                        && !RitualGuardAggroPolicy.mayRequestReturnPath(999L, 1_000L),
                "return path retries honor their next-allowed deadline");
        double[] targetDistances = {4.0D, 9.0D, 6.0D};
        boolean[] eligibleTargets = {true, true, true};
        check(RitualGuardAggroPolicy.preferredTargetIndex(
                        targetDistances, eligibleTargets, false) == 0,
                "melee guards prefer the nearest eligible target");
        check(RitualGuardAggroPolicy.preferredTargetIndex(
                        targetDistances, eligibleTargets, true) == 1,
                "ranged spell and support guards prefer the farthest eligible target");
        check(RitualGuardAggroPolicy.preferredTargetIndex(
                        new double[]{Double.NaN, 7.0D, -1.0D},
                        new boolean[]{true, true, true}, true) == 1,
                "invalid target distances cannot win ranged target selection");
        check(RitualGuardAggroPolicy.preferredTargetIndex(
                        new double[]{5.0D, 6.0D}, new boolean[]{false, false}, true) == -1,
                "selection returns no target when every candidate is ineligible");
        check(RitualGuardAggroPolicy.preferredTargetIndex(
                        new double[]{6.0D, 6.0D}, new boolean[]{true, true}, true) == 0,
                "equal-distance target selection preserves the caller's stable UUID order");
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
