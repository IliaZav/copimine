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
        var acrossOrdinaryBands = BossRealHealthDamagePolicy.apply(4_500.0D,
                2_000.0D, BossDamagePolicy.evaluate(BossPhase.AWAKENING,
                        BossPhasePolicy.DamageImmunityReason.NONE), 5_000.0D);
        check(acrossOrdinaryBands.remainingHealth() == 2_500.0D
                        && acrossOrdinaryBands.appliedDamage() == 2_000.0D,
                "an accepted hit must retain all damage across ordinary thresholds: health="
                        + acrossOrdinaryBands.remainingHealth() + " applied="
                        + acrossOrdinaryBands.appliedDamage());
        var sameTickAcrossBands = BossRealHealthDamagePolicy.applyHits(4_500.0D,
                List.of(500.0D, 1_500.0D), 5_000.0D);
        check(sameTickAcrossBands.remainingHealth() == 2_500.0D
                        && sameTickAcrossBands.appliedDamage() == 2_000.0D,
                "sequential accepted hits must compose across exact thresholds");
        var exactSecondThreshold = BossRealHealthDamagePolicy.apply(4_500.0D,
                1_500.0D, 1.0D, 5_000.0D);
        check(exactSecondThreshold.remainingHealth() == 3_000.0D,
                "an accepted hit can land exactly on a later ordinary threshold");
        var crossed = BossPhasePolicy.transition(BossPhase.AWAKENING,
                acrossOrdinaryBands.remainingHealth(), 5_000.0D);
        check(crossed.current() == BossPhase.RIFT
                        && crossed.entered().equals(List.of(BossPhase.HUNT, BossPhase.RIFT)),
                "crossed phases must be returned once in order");
        var lastSeal = BossRealHealthDamagePolicy.apply(900.0D, 1_000.0D, 1.0D, 5_000.0D);
        check(lastSeal.lethal() && lastSeal.remainingHealth() == 0.0D,
                "LAST_SEAL must let the authored finisher reach zero");
        var exactFirstThreshold = BossRealHealthDamagePolicy.apply(4_500.0D, 500.0D, 1.0D, 5_000.0D);
        check(exactFirstThreshold.remainingHealth() == 4_000.0D
                        && BossPhasePolicy.phaseFor(exactFirstThreshold.remainingHealth(), 5_000.0D)
                        == BossPhase.HUNT,
                "a hit landing exactly on a threshold must enter the next band once");
        var shieldAtThreshold = BossRealHealthDamagePolicy.apply(4_000.0D, 2_000.0D,
                BossDamagePolicy.evaluate(BossPhase.LAST_SEAL,
                        BossPhasePolicy.DamageImmunityReason.LAST_SEAL_GUARDIANS), 5_000.0D);
        check(!shieldAtThreshold.applied()
                        && shieldAtThreshold.remainingHealth() == 4_000.0D
                        && shieldAtThreshold.appliedDamage() == 0.0D,
                "the Last Seal guardian shield must reject the whole hit, not clamp it");
        var healed = BossPhasePolicy.transition(BossPhase.RIFT, 4_500.0D, 5_000.0D);
        check(healed.current() == BossPhase.RIFT && healed.entered().isEmpty(),
                "healing across a crossed threshold must not replay a phase callback");
        var replay = BossPhasePolicy.transition(BossPhase.RIFT, 2_500.0D, 5_000.0D);
        check(replay.current() == BossPhase.RIFT && replay.entered().isEmpty(),
                "a restart with the phase already persisted must not replay callbacks");
        var deepHit = BossRealHealthDamagePolicy.apply(5_000.0D, 3_000.0D, 1.0D, 5_000.0D);
        var deepCross = BossPhasePolicy.transition(BossPhase.AWAKENING,
                deepHit.remainingHealth(), 5_000.0D);
        check(deepHit.appliedDamage() == 3_000.0D
                        && deepCross.current() == BossPhase.OVERLOAD
                        && deepCross.entered().equals(List.of(BossPhase.HUNT, BossPhase.RIFT,
                        BossPhase.OVERLOAD)),
                "one large accepted hit must keep full damage and report every crossed band");
        var lethalAcrossAll = BossRealHealthDamagePolicy.apply(5_000.0D, 9_999.0D, 1.0D, 5_000.0D);
        check(lethalAcrossAll.lethal() && lethalAcrossAll.remainingHealth() == 0.0D
                        && lethalAcrossAll.appliedDamage() == 5_000.0D,
                "lethal damage must apply exactly the remaining real health");
        System.out.println("BossRealHealthDamagePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
