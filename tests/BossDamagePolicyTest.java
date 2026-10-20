import me.copimine.endevent.domain.BossAbilityState;
import me.copimine.endevent.domain.BossDamagePolicy;
import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.BossPhasePolicy;
import me.copimine.endevent.domain.BossRealHealthDamagePolicy;

import java.util.List;

public final class BossDamagePolicyTest {
    public static void main(String[] args) {
        check(BossDamagePolicy.damageAllowed(BossPhase.AWAKENING,
                        BossPhasePolicy.DamageImmunityReason.NONE),
                "normal boss damage must be allowed");
        check(BossDamagePolicy.damageAllowed(BossPhase.RIFT,
                        BossPhasePolicy.DamageImmunityReason.NONE),
                "an ordinary cast phase must not make the boss immune");
        check(!BossDamagePolicy.damageAllowed(BossPhase.LAST_SEAL,
                        BossPhasePolicy.DamageImmunityReason.LAST_SEAL_GUARDIANS),
                "Last Seal guardian shield must reject damage explicitly");
        check(!BossDamagePolicy.damageAllowed(BossPhase.AWAKENING,
                        BossPhasePolicy.DamageImmunityReason.BOSS_CINEMATIC),
                "cinematic must reject damage explicitly");
        check(BossDamagePolicy.damageAllowed(BossPhase.OVERLOAD,
                        BossPhasePolicy.DamageImmunityReason.NONE),
                "ordinary ability timeline state must not reject a valid hit");
        check(BossDamagePolicy.applyIncomingDamage(10.0D) == 10.0D,
                "normal damage must not be multiplied");
        check(BossDamagePolicy.applyIncomingDamage(Double.NaN) == 0.0D,
                "non-finite damage must fail closed");

        var hits = BossRealHealthDamagePolicy.applyHits(5_000.0D,
                List.of(10.0D, 14.0D, 8.0D), 5_000.0D);
        check(hits.remainingHealth() == 4_968.0D,
                "three same-tick players must compose on real health");
        var fivePlayer = BossRealHealthDamagePolicy.applyHits(5_000.0D,
                List.of(10.0D, 14.0D, 8.0D, 6.0D, 12.0D), 5_000.0D);
        check(fivePlayer.remainingHealth() == 4_950.0D,
                "five independent player hits must not lose an update");
        check(BossAbilityState.NONE != null, "canonical ability state is present");
        System.out.println("BossDamagePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
