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
        testGuardianRespawnNeedsNinetySecondsOfActiveCombat();
        testLastDefenderWindowKeepsFifteenSecondsOfVulnerability();
        testGuardianDeadlinesStayIndependentPerSlot();
        testGuardianDeadlinesUseWallClockMilliseconds();
        testGuardianSnapshotKeepsRemainingActiveTime();
        testLegacyGuardianSnapshotMigratesRemainingTicks();
        testDeadGuardianSlotsStayUnspawnableUntilDue();
        testDeferredRespawnWarnsBeforeEffectiveAppearance();
        testPartialGuardianHealthAndDeferredGraceSurviveRestart();
        testExpiredUnshieldedWindowDoesNotRefillAfterRestart();
        testDefendersStrikeWithTheAuthoredThrowCost();
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
        check(!TentacleGuardianPolicy.shielded(BossPhase.LAST_SEAL, 0, false, true),
                "pending or failed initialization must not create an invisible shield");
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

    /** KG-01: an individual defender returns only after 90 s of active combat. */
    private static void testGuardianRespawnNeedsNinetySecondsOfActiveCombat() {
        long deadline = TentacleGuardianPolicy.respawnDeadline(0L, 800);
        check(deadline == 90_000L,
                "a legacy forty-second configuration must not shorten the ninety second floor");
        check(!TentacleGuardianPolicy.respawnDueAt(deadline - 1L, deadline),
                "89.999 s of active combat must not respawn a guardian");
        check(!TentacleGuardianPolicy.respawnWarningDueAt(84_999L, deadline),
                "the respawn warning must wait for the last five seconds");
        check(TentacleGuardianPolicy.respawnWarningDueAt(85_000L, deadline),
                "the respawn warning must fire five seconds before appearance");
        check(!TentacleGuardianPolicy.respawnWarningDueAt(90_000L, deadline),
                "the warning must stop when the guardian is due");
        check(TentacleGuardianPolicy.respawnDueAt(90_000L, deadline),
                "90 s of active combat must respawn the guardian");
        check(TentacleGuardianPolicy.respawnDelayMillis(3_600) == 180_000L,
                "configuration may extend but never shorten the respawn floor");
        check(TentacleGuardianPolicy.respawnDelayMillis(0) == 90_000L,
                "a zero configuration must fall back to the floor");
    }

    /** KG-02: the last defender's death guarantees at least fifteen seconds. */
    private static void testLastDefenderWindowKeepsFifteenSecondsOfVulnerability() {
        check(TentacleGuardianPolicy.damageWindowMillis(300) == 15_000L,
                "the default vulnerability window must be fifteen seconds");
        check(TentacleGuardianPolicy.damageWindowMillis(100) == 15_000L,
                "a five second configuration must not shorten the vulnerability floor");
        check(TentacleGuardianPolicy.damageWindowMillis(400) == 20_000L,
                "longer configured windows must be honoured");
        long deadline = 10_000L;
        check(!TentacleGuardianPolicy.slotReadyForSpawnAt(20_000L, deadline, 0.0D, true),
                "a dead slot must stay dead while the last-defender window is frozen");
        check(TentacleGuardianPolicy.slotReadyForSpawnAt(20_000L, deadline, 0.0D, false),
                "an elapsed deadline must spawn once the window ends");
        check(TentacleGuardianPolicy.slotReadyForSpawnAt(0L, 90_000L, 40.0D, true),
                "an alive slot must reconcile its missing display even while frozen");
        check(!TentacleGuardianPolicy.slotReadyForSpawnAt(89_999L, 90_000L, 0.0D, false),
                "a stored deadline must still gate the spawn");
    }

    /** KG-03: live defenders keep their own deadlines; a later death never resets them. */
    private static void testGuardianDeadlinesStayIndependentPerSlot() {
        long first = TentacleGuardianPolicy.respawnDeadline(1_000L, 1_800);
        long second = TentacleGuardianPolicy.respawnDeadline(30_000L, 1_800);
        check(first == 91_000L && second == 120_000L,
                "each death must receive its own ninety second deadline");
        check(TentacleGuardianPolicy.respawnDueAt(first, first)
                        && !TentacleGuardianPolicy.respawnDueAt(first, second),
                "a guardian that died later must not return with an earlier one");
        check(TentacleGuardianPolicy.respawnDueAt(second, second)
                        && !TentacleGuardianPolicy.respawnDueAt(second - 1L, second),
                "the later guardian must return exactly at its own deadline");
    }

    /** KG-04: deadlines are wall-clock milliseconds, so tick rate cannot stretch them. */
    private static void testGuardianDeadlinesUseWallClockMilliseconds() {
        check(TentacleGuardianPolicy.TICK_MILLIS * 1_800L
                        == TentacleGuardianPolicy.respawnDelayMillis(1_800),
                "the default must equal ninety seconds of real time");
        check(TentacleGuardianPolicy.respawnDeadline(12_345L, 1_800) == 102_345L,
                "deadlines must be pure millisecond arithmetic");
        long deadline = TentacleGuardianPolicy.respawnDeadline(12_345L, 1_800);
        check(!TentacleGuardianPolicy.respawnDueAt(102_344L, deadline)
                        && TentacleGuardianPolicy.respawnDueAt(102_345L, deadline),
                "a lagging server must not respawn a guardian earlier in wall clock time");
    }

    /** KG-05: a restart restores what is left instead of refilling the timers. */
    private static void testGuardianSnapshotKeepsRemainingActiveTime() {
        Map<Integer, Double> health = new LinkedHashMap<>();
        Map<Integer, Long> deadlines = new LinkedHashMap<>();
        health.put(0, 0.0D);
        deadlines.put(0, 95_000L);
        Map<String, String> encoded = TentacleGuardianPolicy.encodeSnapshotMillis(
                health, deadlines, 20_000L, 5_000L);
        TentacleGuardianPolicy.SnapshotStateMillis atSave =
                TentacleGuardianPolicy.decodeSnapshotMillis(encoded, 0L);
        check(atSave.respawnAtMillis().get(0) == 90_000L,
                "the saved snapshot must keep the remaining ninety seconds");
        check(atSave.damageWindowEndsMillis() == 15_000L,
                "the saved snapshot must keep the remaining vulnerability window");
        TentacleGuardianPolicy.SnapshotStateMillis afterRestart =
                TentacleGuardianPolicy.decodeSnapshotMillis(encoded, 30_000L);
        check(afterRestart.respawnAtMillis().get(0) - atSave.respawnAtMillis().get(0) == 30_000L,
                "elapsed wall clock must be replayed, not the remaining active time");
        check(afterRestart.damageWindowEndsMillis() - atSave.damageWindowEndsMillis() == 30_000L,
                "a restart must not refill the guaranteed vulnerability window");
    }

    private static void testLegacyGuardianSnapshotMigratesRemainingTicks() {
        Map<String, String> legacy = new LinkedHashMap<>();
        legacy.put("tentacle-guardian.damage-window-elapsed", "120");
        legacy.put("tentacle-guardian.1.health", "0.0");
        legacy.put("tentacle-guardian.1.respawn-in", "600");
        TentacleGuardianPolicy.SnapshotStateMillis migrated =
                TentacleGuardianPolicy.decodeSnapshotMillis(legacy, 0L);
        check(migrated.damageWindowEndsMillis() == 9_000L,
                "a legacy elapsed window must migrate its remaining nine seconds");
        check(migrated.respawnAtMillis().get(1) == 30_000L,
                "a legacy tick deadline must migrate to milliseconds");
        check(migrated.health().get(1) == 0.0D, "legacy dead slots must stay dead");
    }

    /** KG-06: a dead slot with a future deadline must never look alive to the adapter. */
    private static void testDeadGuardianSlotsStayUnspawnableUntilDue() {
        check(!TentacleGuardianPolicy.slotReadyForSpawnAt(0L, 90_000L, 0.0D, false),
                "a dead slot must not be spawnable before its deadline");
        check(TentacleGuardianPolicy.slotReadyForSpawnAt(90_000L, 90_000L, 0.0D, false),
                "the same slot becomes spawnable exactly at the deadline");
        check(!TentacleGuardianPolicy.slotReadyForSpawnAt(90_000L, 90_000L, 0.0D, true),
                "the guaranteed vulnerability window overrides the deadline");
    }

    private static void testDeferredRespawnWarnsBeforeEffectiveAppearance() {
        long guardianA = TentacleGuardianPolicy.respawnDeadline(0L, 1800);
        long guardianB = TentacleGuardianPolicy.respawnDeadline(85_000L, 1800);
        long graceEnds = 100_000L;
        check(!TentacleGuardianPolicy.respawnWarningDueAt(85_000L, guardianA, graceEnds),
                "a deferred guardian must not warn before its effective five-second window");
        check(!TentacleGuardianPolicy.respawnWarningDueAt(94_999L, guardianA, graceEnds),
                "effective warning must not begin even one millisecond early");
        check(TentacleGuardianPolicy.respawnWarningDueAt(95_000L, guardianA, graceEnds),
                "the deferred appearance at 100s must warn at 95s");
        check(!TentacleGuardianPolicy.respawnDueAt(99_999L, guardianA, graceEnds),
                "the deferred guardian cannot appear inside the protected window");
        check(TentacleGuardianPolicy.respawnDueAt(100_000L, guardianA, graceEnds),
                "the deferred guardian becomes due when the protected window ends");
        check(!TentacleGuardianPolicy.respawnWarningDueAt(100_000L, guardianA, graceEnds),
                "warning must not start after effective appearance is already due");
        check(TentacleGuardianPolicy.effectiveRespawnDeadline(guardianB, graceEnds) == 175_000L,
                "another guardian keeps its own later 90-second deadline");
        check(!TentacleGuardianPolicy.respawnWarningDueAt(169_999L, guardianB, graceEnds)
                        && TentacleGuardianPolicy.respawnWarningDueAt(170_000L, guardianB, graceEnds),
                "the second guardian's warning remains independent of the deferred first slot");
        check(TentacleGuardianPolicy.effectiveRespawnDeadline(null, graceEnds) == null,
                "an uninitialized or missing live actor must not acquire a death deadline");
        check(TentacleGuardianPolicy.effectiveRespawnDeadline(guardianA, -1L) == 90_000L,
                "without a protected window the original deadline stays unchanged");
    }

    private static void testPartialGuardianHealthAndDeferredGraceSurviveRestart() {
        Map<String, String> encoded = TentacleGuardianPolicy.encodeSnapshotMillis(
                Map.of(0, 37.5D, 1, 0.0D, 2, 0.0D),
                Map.of(1, 90_000L, 2, 175_000L), 100_000L, 89_000L);
        TentacleGuardianPolicy.SnapshotStateMillis restored =
                TentacleGuardianPolicy.decodeSnapshotMillis(encoded, 0L);
        check(restored.health().get(0) == 37.5D,
                "a damaged living guardian must retain its exact partial health after restart");
        check(restored.health().get(1) == 0.0D && restored.health().get(2) == 0.0D,
                "the two defeated guardians must remain dead after restart");
        check(!restored.respawnAtMillis().containsKey(0),
                "the partial-health living slot must not acquire a respawn schedule");
        check(restored.damageWindowEndsMillis() == 11_000L,
                "restart must preserve the remaining 11 seconds without refilling the window");
        check(TentacleGuardianPolicy.effectiveRespawnDeadline(
                        restored.respawnAtMillis().get(1), restored.damageWindowEndsMillis()) == 11_000L,
                "the expired-first guardian remains deferred to the saved vulnerability end");
        check(TentacleGuardianPolicy.respawnWarningDueAt(6_000L,
                        restored.respawnAtMillis().get(1), restored.damageWindowEndsMillis()),
                "after restart the same effective appearance must still have five seconds warning");
        check(restored.respawnAtMillis().get(2) == 86_000L,
                "the second guardian's remaining 86 seconds must not be grouped with the first");
    }

    private static void testExpiredUnshieldedWindowDoesNotRefillAfterRestart() {
        Map<String, String> encoded = TentacleGuardianPolicy.encodeSnapshotMillis(
                Map.of(0, 0.0D), Map.of(0, 90_000L), 15_000L, 30_000L);
        TentacleGuardianPolicy.SnapshotStateMillis restored =
                TentacleGuardianPolicy.decodeSnapshotMillis(encoded, 0L);
        check(restored.damageWindowEndsMillis() == 0L,
                "an expired but committed window must restore as expired, not become uninitialized");
        check(!TentacleGuardianPolicy.slotReadyForSpawnAt(59_999L,
                        restored.respawnAtMillis().get(0), 0.0D, false),
                "restoring an expired window must keep the defender's remaining 60-second deadline");
    }

    private static void testDefendersStrikeWithTheAuthoredThrowCost() {
        check(TentacleGuardianPolicy.STRIKE_DAMAGE > 0.0D,
                "a defender's telegraphed hit must deal real damage");
        check(TentacleGuardianPolicy.STRIKE_DAMAGE
                        == me.copimine.endevent.domain.TentacleThrowPolicy.RELEASE_DAMAGE,
                "defender strike reuses the approved throw cost instead of new tuning");
        check(TentacleGuardianPolicy.STRIKE_KNOCKBACK > 0.0D
                        && TentacleGuardianPolicy.STRIKE_KNOCKBACK <= 1.0D,
                "knockback must be bounded");
        check(TentacleGuardianPolicy.STRIKE_LIFT > 0.0D
                        && TentacleGuardianPolicy.STRIKE_LIFT < 1.0D,
                "lift must be a small bounded hop");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
