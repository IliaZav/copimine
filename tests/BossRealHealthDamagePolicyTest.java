import java.util.List;
import me.copimine.endevent.domain.BossDamagePolicy;
import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.BossPhasePolicy;
import me.copimine.endevent.domain.BossRealHealthDamagePolicy;

public final class BossRealHealthDamagePolicyTest {
    public static void main(String[] args) {
        var hits = BossRealHealthDamagePolicy.applyHits(5000.0D,
                List.of(10.0D, 14.0D, 8.0D), 5000.0D);
        check(hits.remainingHealth() == 4968.0D, "same-tick hits must sum on real HP");
        check(hits.appliedDamage() == 32.0D, "all three final damages must be applied");
        var blocked = BossRealHealthDamagePolicy.apply(100.0D, 10.0D,
                BossDamagePolicy.evaluate(BossPhase.LAST_SEAL,
                        BossPhasePolicy.DamageImmunityReason.LAST_SEAL_GUARDIANS), 5000.0D);
        check(!blocked.applied() && blocked.remainingHealth() == 100.0D,
                "explicit Last Seal shield must leave real HP unchanged");
        var allowed = BossRealHealthDamagePolicy.apply(100.0D, 10.0D,
                BossDamagePolicy.evaluate(BossPhase.RIFT,
                        BossPhasePolicy.DamageImmunityReason.NONE), 5000.0D);
        check(allowed.applied() && allowed.remainingHealth() == 90.0D,
                "ordinary boss casts must remain damageable");
        check(BossRealHealthDamagePolicy.scaleBaseDamage(10, 8, 12) == 15,
                "base damage scaling must preserve requested final amount");
        var lethal = BossRealHealthDamagePolicy.apply(5, 10, 1.0D, 5000);
        check(lethal.lethal() && lethal.remainingHealth() == 0,
                "lethal real-health transaction must clamp to zero");
        System.out.println("BossRealHealthDamagePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
