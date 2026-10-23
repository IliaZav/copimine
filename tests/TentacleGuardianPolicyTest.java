import me.copimine.endevent.domain.TentacleGuardianPolicy;
import me.copimine.endevent.domain.TentacleAnimationPolicy;
import me.copimine.endevent.domain.TentacleScalingPolicy;
import me.copimine.endevent.domain.BossPhase;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TentacleGuardianPolicyTest {
    public static void main(String[] args) {
        testHealthBudgetIsBoundedAndDistributed();
        testShieldWindowAndRespawnBoundaries();
        testPermanentAttackScheduleIsStaggered();
        testHealthVisualStates();
        testRestartSnapshotRebasesDeadlines();
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
        check(TentacleScalingPolicy.permanentFor(10, BossPhase.LAST_SEAL) == 5,
                "health distribution must use the official guardian count");
    }

    private static void testShieldWindowAndRespawnBoundaries() {
        check(TentacleGuardianPolicy.shielded(BossPhase.LAST_SEAL, 1, false),
                "one living guardian must shield the boss");
        check(!TentacleGuardianPolicy.shielded(BossPhase.LAST_SEAL, 0, true),
                "the all-dead window must release the boss");
        check(!TentacleGuardianPolicy.shielded(BossPhase.RAGE, 1, false),
                "guardians must not shield before Last Seal");
        check(TentacleGuardianPolicy.shielded(BossPhase.LAST_SEAL, 0, false, true),
                "Last Seal must be shielded while guardians are initializing");
        check(!TentacleGuardianPolicy.shielded(BossPhase.LAST_SEAL, 0, false, false),
                "an empty non-initializing set must not invent a shield window");
        check(TentacleGuardianPolicy.damageWindowOpen(BossPhase.LAST_SEAL, 0, 100L, 399L),
                "damage window must stay open for 300 ticks");
        check(!TentacleGuardianPolicy.damageWindowOpen(BossPhase.LAST_SEAL, 0, 100L, 400L),
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
        check(TentacleGuardianPolicy.nextAttackTickAfterRecovery(1200L, 1100L, 0) == 1200L,
                "a hit flinch must preserve an already pending attack tick");
        check(TentacleGuardianPolicy.nextAttackTickAfterRecovery(1090L, 1100L, 1) == 1090L,
                "an attack that became due during a flinch must remain due after recovery");
        check(TentacleGuardianPolicy.nextAttackTickAfterRecovery(-1L, 1100L, 1)
                        == 1100L + TentacleGuardianPolicy.ATTACK_INTERVAL_TICKS
                        + TentacleGuardianPolicy.ATTACK_STAGGER_TICKS,
                "a completed attack with no pending tick must schedule its next staggered cycle");
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

    private static void testRestartSnapshotRebasesDeadlines() {
        Map<Integer, Double> health = new LinkedHashMap<>();
        health.put(0, 42.5D);
        health.put(1, 0.0D);
        Map<Integer, Long> respawn = new LinkedHashMap<>();
        respawn.put(1, 1800L);
        Map<String, String> encoded = TentacleGuardianPolicy.encodeSnapshot(
                health, respawn, 900L, 1000L);
        check(encoded.containsKey("tentacle-guardian.1.respawn-in"),
                "dead slot cooldown must be persisted");
        TentacleGuardianPolicy.SnapshotState restored =
                TentacleGuardianPolicy.decodeSnapshot(encoded, 200L);
        check(restored != null, "guardian snapshot must decode");
        check(restored.health().get(0) == 42.5D && restored.health().get(1) == 0.0D,
                "guardian health must survive a restart");
        check(restored.respawnAt().get(1) == 1000L,
                "respawn cooldown must be rebased onto the new tick counter");
        check(restored.damageWindowStartedTick() == 100L,
                "open damage window must keep its remaining duration");
        check(TentacleGuardianPolicy.decodeSnapshot(Map.of(), 0L) == null,
                "a snapshot without guardian keys must stay backward compatible");
        check(!TentacleGuardianPolicy.encodeSnapshot(
                        Map.of(0, 1.0D), Map.of(), 100L, 1000L)
                .containsKey("tentacle-guardian.damage-window-elapsed"),
                "an expired damage window must not be persisted");
        boolean rejected = false;
        try {
            TentacleGuardianPolicy.decodeSnapshot(
                    Map.of("tentacle-guardian.8.health", "10"), 0L);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "out-of-range guardian slots must fail closed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
