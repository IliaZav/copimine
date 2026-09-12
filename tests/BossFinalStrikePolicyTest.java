import me.copimine.endevent.domain.BossFinalStrikePolicy;
import me.copimine.endevent.domain.BossPhase;

public final class BossFinalStrikePolicyTest {
    public static void main(String[] args) {
        check(BossFinalStrikePolicy.canStart(BossPhase.LAST_SEAL, false, true, true),
                "final strike must start only in Last Seal with a live target");
        check(!BossFinalStrikePolicy.canStart(BossPhase.RAGE, false, true, true),
                "final strike must not start before Last Seal");
        check(!BossFinalStrikePolicy.canStart(BossPhase.LAST_SEAL, true, true, true),
                "final strike must be one-shot per boss generation");
        check(!BossFinalStrikePolicy.canStart(BossPhase.LAST_SEAL, false, false, true),
                "dead boss must not start a strike");
        check(!BossFinalStrikePolicy.canStart(BossPhase.LAST_SEAL, false, true, false),
                "strike must have an eligible target");
        check(BossFinalStrikePolicy.phaseAt(0) == BossFinalStrikePolicy.Phase.TELEGRAPH,
                "strike begins with a telegraph");
        check(BossFinalStrikePolicy.phaseAt(BossFinalStrikePolicy.IMPACT_TICK)
                        == BossFinalStrikePolicy.Phase.IMPACT,
                "strike has an explicit impact phase");
        check(BossFinalStrikePolicy.isImpactTick(BossFinalStrikePolicy.IMPACT_TICK),
                "impact is scheduled once");
        check(!BossFinalStrikePolicy.isImpactTick(BossFinalStrikePolicy.IMPACT_TICK + 1),
                "impact is not repeated");
        check(BossFinalStrikePolicy.validatedDamage(999.0D) == BossFinalStrikePolicy.MAX_DAMAGE,
                "strike damage has a hard cap");
        System.out.println("BossFinalStrikePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
