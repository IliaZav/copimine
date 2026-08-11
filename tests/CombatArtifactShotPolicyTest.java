import me.copimine.artifacts.CombatArtifactShotPolicy;

public final class CombatArtifactShotPolicyTest {
    public static void main(String[] args) {
        testMultishotAllowsExactlyThreeProjectilesFromOneShot();
        testCooldownBlocksASecondShot();
        testWindowExpiresAfterTwoTicks();
        System.out.println("CombatArtifactShotPolicyTest OK");
    }

    private static void testMultishotAllowsExactlyThreeProjectilesFromOneShot() {
        var first = CombatArtifactShotPolicy.decide(100L, 200L, 0L, true, null);
        check(first.allowed() && first.startsShot(), "first projectile must start the shot");
        var second = CombatArtifactShotPolicy.decide(100L, 200L, 115L, true, first.window());
        var third = CombatArtifactShotPolicy.decide(100L, 201L, 115L, true, second.window());
        var fourth = CombatArtifactShotPolicy.decide(100L, 202L, 115L, true, third.window());
        check(second.allowed() && !second.startsShot(), "second multishot projectile must be accepted without a second shot");
        check(third.allowed() && !third.startsShot(), "third multishot projectile must be accepted without a second shot");
        check(!fourth.allowed(), "a fourth projectile must not bypass the cooldown");
        check(third.window().projectileCount() == 3, "multishot window must contain exactly three projectiles");
    }

    private static void testCooldownBlocksASecondShot() {
        var first = CombatArtifactShotPolicy.decide(100L, 200L, 0L, false, null);
        var blocked = CombatArtifactShotPolicy.decide(101L, 220L, 115L, false, null);
        var ready = CombatArtifactShotPolicy.decide(115L, 220L, 115L, false, null);
        check(first.allowed() && first.startsShot(), "ordinary artifact must start its first shot");
        check(!blocked.allowed(), "ordinary artifact must be blocked during cooldown");
        check(ready.allowed() && ready.startsShot(), "ordinary artifact must be ready at cooldown expiry");
    }

    private static void testWindowExpiresAfterTwoTicks() {
        var first = CombatArtifactShotPolicy.decide(100L, 200L, 0L, true, null);
        var expired = CombatArtifactShotPolicy.decide(100L, 203L, 115L, true, first.window());
        check(!expired.allowed(), "a delayed projectile must not be mistaken for the same multishot");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
