import me.copimine.endevent.domain.BossFinalStrikePolicy;
import me.copimine.endevent.domain.BossStage;

public final class BossFinalStrikePolicyTest {
    public static void main(String[] args) {
        testOnlyCatastropheCanStartTheStrike();
        testTimelineIsFiniteAndOrdered();
        testDamageIsBoundedAndConfigurable();
        System.out.println("BossFinalStrikePolicyTest OK");
    }

    private static void testOnlyCatastropheCanStartTheStrike() {
        check(BossFinalStrikePolicy.canStart(BossStage.CATASTROPHE, false, true, true),
                "final strike must start in CATASTROPHE with a live target");
        check(!BossFinalStrikePolicy.canStart(BossStage.DISTORTION, false, true, true),
                "final strike must not start before CATASTROPHE");
        check(!BossFinalStrikePolicy.canStart(BossStage.CATASTROPHE, true, true, true),
                "final strike must be one-shot per boss generation");
        check(!BossFinalStrikePolicy.canStart(BossStage.CATASTROPHE, false, false, true),
                "dead boss must not start a final strike");
        check(!BossFinalStrikePolicy.canStart(BossStage.CATASTROPHE, false, true, false),
                "final strike must not start without a valid participant target");
    }

    private static void testTimelineIsFiniteAndOrdered() {
        check(BossFinalStrikePolicy.phaseAt(0) == BossFinalStrikePolicy.Phase.TELEGRAPH,
                "strike must begin with a telegraph");
        check(BossFinalStrikePolicy.phaseAt(BossFinalStrikePolicy.TELEGRAPH_TICKS)
                        == BossFinalStrikePolicy.Phase.CHARGE,
                "strike must enter its charge phase after the telegraph");
        check(BossFinalStrikePolicy.phaseAt(BossFinalStrikePolicy.IMPACT_TICK)
                        == BossFinalStrikePolicy.Phase.IMPACT,
                "strike must have an explicit impact phase");
        check(BossFinalStrikePolicy.isImpactTick(BossFinalStrikePolicy.IMPACT_TICK),
                "impact must occur exactly once at the configured impact tick");
        check(!BossFinalStrikePolicy.isImpactTick(BossFinalStrikePolicy.IMPACT_TICK + 1),
                "impact must not repeat on the next tick");
        check(BossFinalStrikePolicy.phaseAt(BossFinalStrikePolicy.TOTAL_TICKS)
                        == BossFinalStrikePolicy.Phase.COMPLETE,
                "strike must finish within a bounded timeline");
    }

    private static void testDamageIsBoundedAndConfigurable() {
        check(BossFinalStrikePolicy.validatedDamage(8.0D) == 8.0D,
                "default final-strike damage must be eight");
        check(BossFinalStrikePolicy.validatedDamage(-1.0D) == 0.0D,
                "negative configured damage must fail closed");
        check(BossFinalStrikePolicy.validatedDamage(999.0D) == BossFinalStrikePolicy.MAX_DAMAGE,
                "final-strike damage must have a hard safety cap");
        check(BossFinalStrikePolicy.validatedDamage(Double.NaN) == 0.0D,
                "non-finite configured damage must fail closed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
