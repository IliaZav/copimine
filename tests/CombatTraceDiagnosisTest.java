import me.copimine.endevent.domain.BossAbilityState;
import me.copimine.endevent.domain.CombatTraceDiagnosis;
import me.copimine.endevent.domain.CombatTraceRecord;
import me.copimine.endevent.domain.EventPhase;

public final class CombatTraceDiagnosisTest {
    public static void main(String[] args) {
        CombatTraceRecord base = CombatTraceRecord.open(
                1L, 1L, null, null, "PLAYER", "ENTITY_ATTACK", 10.0D, 10.0D,
                false, 0, 20, 0.0D, 100.0D, EventPhase.BOSS_ACTIVE,
                BossAbilityState.NONE, false, 20.0D);
        check(base.close(false, 90.0D).diagnosis() == CombatTraceDiagnosis.APPLIED,
                "health loss must be classified as applied");
        check(base.close(true, 100.0D).diagnosis() == CombatTraceDiagnosis.CANCELLED,
                "cancelled event must be classified as cancelled");
        CombatTraceRecord resistant = CombatTraceRecord.open(
                1L, 1L, null, null, "PLAYER", "ENTITY_ATTACK", 10.0D, 10.0D,
                false, 20, 20, 10.0D, 100.0D, EventPhase.BOSS_ACTIVE,
                BossAbilityState.NONE, false, 20.0D);
        check(resistant.close(false, 100.0D).diagnosis() == CombatTraceDiagnosis.HURT_RESISTANCE,
                "unchanged health at the hurt-resistance boundary must be diagnosed");
        CombatTraceRecord stall = base.close(false, 100.0D);
        stall = new CombatTraceRecord(stall.tick(), stall.observedAtMillis(), stall.attackerId(),
                stall.victimId(), stall.attackerKind(), stall.cause(), stall.rawDamage(),
                stall.finalDamage(), stall.cancelledBefore(), stall.cancelledAfter(),
                stall.noDamageTicks(), stall.maximumNoDamageTicks(), stall.lastDamage(),
                stall.healthBefore(), stall.nextTickHealth(), stall.phase(), stall.abilityState(),
                stall.shielded(), 75.0D);
        check(stall.diagnosis() == CombatTraceDiagnosis.MAIN_THREAD_STALL,
                "high MSPT with no health change must be diagnosed as a stall");
        System.out.println("CombatTraceDiagnosisTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
