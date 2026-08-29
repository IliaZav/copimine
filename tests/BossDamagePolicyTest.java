import me.copimine.endevent.domain.BossCastState;
import me.copimine.endevent.domain.BossDamagePolicy;
import me.copimine.endevent.domain.BossStage;

public final class BossDamagePolicyTest {
    public static void main(String[] args) {
        testDamageIsAllowedNormallyAndAfterExpiredCast();
        testOnlyActiveBoundedCastBlocksDamage();
        testExhaustedWindowAmplifiesIncomingDamage();
        testExhaustedMultiplierIsAppliedExactlyOnce();
        System.out.println("BossDamagePolicyTest OK");
    }

    private static void testDamageIsAllowedNormallyAndAfterExpiredCast() {
        check(BossDamagePolicy.damageAllowed(BossStage.AWAKENING, BossCastState.NONE, 100L, 0L),
                "normal boss must take damage");
        check(BossDamagePolicy.damageAllowed(BossStage.ABSORPTION, BossCastState.ABSORPTION_CHANNEL, 5001L, 5000L),
                "expired absorption must take damage");
        check(BossDamagePolicy.damageAllowed(BossStage.CATASTROPHE, BossCastState.JUDGMENT_CAST, 10000L, 0L),
                "cast with no deadline must fail open to damage");
    }

    private static void testOnlyActiveBoundedCastBlocksDamage() {
        check(!BossDamagePolicy.damageAllowed(BossStage.ABSORPTION, BossCastState.ABSORPTION_CHANNEL, 4999L, 5000L),
                "active absorption channel must be invulnerable");
        check(!BossDamagePolicy.damageAllowed(BossStage.CATASTROPHE, BossCastState.JUDGMENT_CAST, 1999L, 2000L),
                "active Judgment cast must be invulnerable");
    }

    private static void testExhaustedWindowAmplifiesIncomingDamage() {
        check(BossDamagePolicy.incomingDamageMultiplier(BossCastState.EXHAUSTED) == 1.5D,
                "exhausted boss must take 50 percent more damage");
        check(BossDamagePolicy.damageAllowed(BossStage.CATASTROPHE, BossCastState.EXHAUSTED, 1L, 999999L),
                "exhausted boss must remain damageable");
    }

    private static void testExhaustedMultiplierIsAppliedExactlyOnce() {
        check(BossDamagePolicy.applyIncomingDamage(10.0D, BossCastState.EXHAUSTED) == 15.0D,
                "exhausted multiplier must be applied once to the incoming damage");
        check(BossDamagePolicy.applyIncomingDamage(10.0D, BossCastState.NONE) == 10.0D,
                "normal damage must not be changed by the cast policy");
        check(BossDamagePolicy.applyIncomingDamage(Double.NaN, BossCastState.EXHAUSTED) == 0.0D,
                "non-finite incoming damage must fail closed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
