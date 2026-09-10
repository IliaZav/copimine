import me.copimine.endevent.domain.TentacleGuardianPolicy;
import me.copimine.endevent.domain.TentacleAnimationPolicy;
import me.copimine.endevent.domain.TentacleScalingPolicy;
import me.copimine.endevent.domain.V2BossStage;

public final class TentacleGuardianPolicyTest {
    public static void main(String[] args) {
        testHealthBudgetIsBoundedAndDistributed();
        testShieldWindowAndRespawnBoundaries();
        testPermanentAttackScheduleIsStaggered();
        testHealthVisualStates();
        System.out.println("TentacleGuardianPolicyTest OK");
    }

    private static void testHealthBudgetIsBoundedAndDistributed() {
        check(TentacleGuardianPolicy.totalHealthBudget(2) == 120,
                "duo budget must remain small enough for a playable final phase");
        check(TentacleGuardianPolicy.totalHealthBudget(20) == 336,
                "twenty-player budget must stay bounded");
        check(TentacleGuardianPolicy.perGuardianHealth(2) == 60,
                "two guardians must receive an even duo budget");
        check(TentacleGuardianPolicy.perGuardianHealth(10) == 44,
                "five guardians must receive the rounded ten-player budget");
        check(TentacleGuardianPolicy.perGuardianHealth(20) == 42,
                "eight guardians must receive the rounded twenty-player budget");
        check(TentacleScalingPolicy.permanentFor(10, V2BossStage.LAST_SEAL) == 5,
                "health distribution must use the official guardian count");
    }

    private static void testShieldWindowAndRespawnBoundaries() {
        check(TentacleGuardianPolicy.shielded(V2BossStage.LAST_SEAL, 1, false),
                "one living guardian must shield the boss");
        check(!TentacleGuardianPolicy.shielded(V2BossStage.LAST_SEAL, 0, true),
                "the all-dead window must release the boss");
        check(!TentacleGuardianPolicy.shielded(V2BossStage.RAGE, 1, false),
                "guardians must not shield before Last Seal");
        check(TentacleGuardianPolicy.shielded(V2BossStage.LAST_SEAL, 0, false, true),
                "Last Seal must be shielded while guardians are initializing");
        check(!TentacleGuardianPolicy.shielded(V2BossStage.LAST_SEAL, 0, false, false),
                "an empty non-initializing set must not invent a shield window");
        check(TentacleGuardianPolicy.damageWindowOpen(V2BossStage.LAST_SEAL, 0, 100L, 399L),
                "damage window must stay open for 300 ticks");
        check(!TentacleGuardianPolicy.damageWindowOpen(V2BossStage.LAST_SEAL, 0, 100L, 400L),
                "damage window must close at its exact boundary");
        check(!TentacleGuardianPolicy.respawnDue(900L, 800L, true),
                "respawn must remain frozen during the damage window");
        check(TentacleGuardianPolicy.respawnDue(900L, 800L, false),
                "respawn must be due after its bounded delay");
        check(TentacleGuardianPolicy.slotReadyForSpawn(0.0D, null, 100L, false),
                "an uninitialized guardian slot must spawn immediately");
        check(!TentacleGuardianPolicy.slotReadyForSpawn(0.0D, 800L, 100L, false),
                "a defeated guardian must respect its respawn schedule");
        check(TentacleGuardianPolicy.slotReadyForSpawn(0.0D, 800L, 800L, false),
                "a guardian slot must become ready at its respawn tick");
        check(TentacleGuardianPolicy.slotReadyForSpawn(60.0D, 800L, 100L, true),
                "a living guardian slot must remain available while frozen");
    }

    private static void testPermanentAttackScheduleIsStaggered() {
        long first = TentacleGuardianPolicy.firstAttackTick(1000L, 0);
        long second = TentacleGuardianPolicy.firstAttackTick(1000L, 1);
        check(second > first, "permanent attacks must not begin on one tick");
        check(second - first == TentacleGuardianPolicy.ATTACK_STAGGER_TICKS,
                "permanent attack stagger must be deterministic");
        check(!TentacleGuardianPolicy.attackDue(first - 1L, first, false),
                "an attack must not start before its scheduled tick");
        check(TentacleGuardianPolicy.attackDue(first, first, false),
                "an attack must start at its scheduled tick");
        check(!TentacleGuardianPolicy.attackDue(first, first, true),
                "a guardian already in an attack state must not double-start");
        check(TentacleGuardianPolicy.canBeginAttack(TentacleAnimationPolicy.Kind.PERMANENT,
                        TentacleAnimationPolicy.State.SHIELD_CHANNEL),
                "permanent guardians must leave shield channel for a bounded attack");
        check(!TentacleGuardianPolicy.canBeginAttack(TentacleAnimationPolicy.Kind.TEMPORARY,
                        TentacleAnimationPolicy.State.SHIELD_CHANNEL),
                "temporary tentacles must never become shield guardians");
    }

    private static void testHealthVisualStates() {
        check("FULL".equals(TentacleGuardianPolicy.healthVisualState(60.0D, 60.0D)),
                "full guardian state must be readable");
        check("DAMAGED".equals(TentacleGuardianPolicy.healthVisualState(40.0D, 60.0D)),
                "damaged guardian state must be readable");
        check("CRITICAL".equals(TentacleGuardianPolicy.healthVisualState(10.0D, 60.0D)),
                "critical guardian state must be readable");
        check("DEAD".equals(TentacleGuardianPolicy.healthVisualState(0.0D, 60.0D)),
                "dead guardian state must be readable");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
