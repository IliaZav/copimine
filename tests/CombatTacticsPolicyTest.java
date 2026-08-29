import me.copimine.endevent.domain.BossStage;
import me.copimine.endevent.domain.CombatTacticsPolicy;
import me.copimine.endevent.domain.BossTeleportPermitPolicy;

public final class CombatTacticsPolicyTest {
    public static void main(String[] args) {
        testBossTacticsChangeByStageAndCycle();
        testBossFeintIsBoundedAndDeterministic();
        testBossNeverReceivesCoreCollapsePlan();
        testWaveRolesAreDistinctAndDeterministic();
        testWaveManeuversAreBoundedAndRepeatable();
        testTeleportPermitIsSingleUseAndBounded();
        System.out.println("CombatTacticsPolicyTest OK");
    }

    private static void testBossTacticsChangeByStageAndCycle() {
        CombatTacticsPolicy.BossPlan hunter = CombatTacticsPolicy.bossPlan(
                BossStage.HUNTER, 0, 10.0D, false);
        CombatTacticsPolicy.BossPlan distortion = CombatTacticsPolicy.bossPlan(
                BossStage.DISTORTION, 1, 10.0D, false);
        CombatTacticsPolicy.BossPlan catastrophe = CombatTacticsPolicy.bossPlan(
                BossStage.CATASTROPHE, 2, 10.0D, false);
        check(hunter.tactic() != distortion.tactic(), "hunter and distortion must use different movement ideas");
        check(distortion.tactic() != catastrophe.tactic(), "distortion and catastrophe must escalate differently");
        check(hunter.shouldReposition(), "hunter must periodically flank instead of standing still");
        check(catastrophe.preferredDistance() < hunter.preferredDistance(),
                "catastrophe must close distance for pressure");
    }

    private static void testBossNeverReceivesCoreCollapsePlan() {
        CombatTacticsPolicy.BossPlan plan = CombatTacticsPolicy.bossPlan(
                BossStage.ABSORPTION, 4, 0.0D, true);
        check(plan.preferOuterRing(), "a Core-standing target must force an outer-ring plan");
        check(plan.preferredDistance() >= CombatTacticsPolicy.MIN_BOSS_DISTANCE,
                "outer-ring plan must stay outside the Core safety distance");
    }

    private static void testBossFeintIsBoundedAndDeterministic() {
        CombatTacticsPolicy.BossPlan first = CombatTacticsPolicy.bossPlan(
                BossStage.HUNTER, 1, 10.0D, false);
        CombatTacticsPolicy.BossPlan second = CombatTacticsPolicy.bossPlan(
                BossStage.HUNTER, 1, 10.0D, false);
        check(first.tactic() == CombatTacticsPolicy.BossTactic.PHANTOM_FEINT,
                "Hunter cycle one must use the readable phantom feint");
        check(first.tactic() == second.tactic()
                        && first.preferredDistance() == second.preferredDistance(),
                "boss feint plan must be deterministic");
        check(first.preferredDistance() >= CombatTacticsPolicy.MIN_BOSS_DISTANCE,
                "boss feint must keep the minimum Core distance");
    }

    private static void testWaveRolesAreDistinctAndDeterministic() {
        CombatTacticsPolicy.MobTactic raider = CombatTacticsPolicy.waveTactic(4, "RAIDER", 0);
        CombatTacticsPolicy.MobTactic breaker = CombatTacticsPolicy.waveTactic(4, "BREAKER", 1);
        CombatTacticsPolicy.MobTactic artillery = CombatTacticsPolicy.waveTactic(4, "ARTILLERY", 2);
        check(raider != breaker && breaker != artillery && raider != artillery,
                "tower roles must produce three distinct tactics");
        check(raider == CombatTacticsPolicy.waveTactic(4, "RAIDER", 99),
                "role tactic must not vary randomly between ticks");
        check(CombatTacticsPolicy.waveTactic(5, "ELITE", 0)
                        == CombatTacticsPolicy.MobTactic.STORM_HUNTER,
                "Wave V elites must hunt through the storm instead of using a generic route");
    }

    private static void testWaveManeuversAreBoundedAndRepeatable() {
        CombatTacticsPolicy.MobManeuver first = CombatTacticsPolicy.waveManeuver(
                5, "ELITE", 1, 2);
        CombatTacticsPolicy.MobManeuver second = CombatTacticsPolicy.waveManeuver(
                5, "ELITE", 1, 2);
        check(first == second, "wave maneuver must be deterministic for a stable slot");
        check(first != CombatTacticsPolicy.MobManeuver.HOLD_LINE,
                "Wave V elite must make a visible tactical movement beat");
        check(CombatTacticsPolicy.waveManeuver(4, "ARTILLERY", 0, 0)
                        == CombatTacticsPolicy.MobManeuver.CROSS_FIRE,
                "artillery must alternate through a cross-fire beat");
        check(CombatTacticsPolicy.waveManeuver(4, "BREAKER", 2, 0)
                        == CombatTacticsPolicy.MobManeuver.FALLBACK,
                "a breaker must retreat briefly on its recovery beat");
    }

    private static void testTeleportPermitIsSingleUseAndBounded() {
        BossTeleportPermitPolicy.Permit permit = BossTeleportPermitPolicy.issue("boss", 10L, 100L);
        check(BossTeleportPermitPolicy.accept(permit, "boss", 20L), "fresh permit must be accepted");
        check(!BossTeleportPermitPolicy.accept(permit, "boss", 20L), "a permit must be single-use");
        check(!BossTeleportPermitPolicy.accept(
                        BossTeleportPermitPolicy.issue("boss", 10L, 100L), "other", 20L),
                "a permit must be scoped to one entity");
        check(!BossTeleportPermitPolicy.accept(
                        BossTeleportPermitPolicy.issue("boss", 10L, 100L), "boss", 101L),
                "an expired permit must be rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
