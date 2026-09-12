import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.BossTeleportPermitPolicy;
import me.copimine.endevent.domain.CombatTacticsPolicy;
import me.copimine.endevent.domain.EndRiftObjective;

public final class CombatTacticsPolicyTest {
    public static void main(String[] args) {
        testBossTacticsChangeByPhaseAndCycle();
        testBossNeverReceivesCoreCollapsePlan();
        testObjectiveRolesAreDistinctAndDeterministic();
        testWaveManeuversAreBoundedAndRepeatable();
        testTeleportPermitIsScopedAndSingleUse();
        System.out.println("CombatTacticsPolicyTest OK");
    }

    private static void testBossTacticsChangeByPhaseAndCycle() {
        CombatTacticsPolicy.BossPlan hunt = CombatTacticsPolicy.bossPlan(
                BossPhase.HUNT, 0, 10.0D, false);
        CombatTacticsPolicy.BossPlan rift = CombatTacticsPolicy.bossPlan(
                BossPhase.RIFT, 1, 10.0D, false);
        CombatTacticsPolicy.BossPlan rage = CombatTacticsPolicy.bossPlan(
                BossPhase.RAGE, 2, 10.0D, false);
        check(hunt.shouldReposition() && rift.shouldReposition() && rage.shouldReposition(),
                "every combat phase must retain bounded repositioning");
        check(hunt.tactic() != rift.tactic() || rift.tactic() != rage.tactic(),
                "phase and cycle must change the boss intent");
        check(rage.preferredDistance() <= hunt.preferredDistance(),
                "late phase must close distance for pressure");
    }

    private static void testBossNeverReceivesCoreCollapsePlan() {
        CombatTacticsPolicy.BossPlan plan = CombatTacticsPolicy.bossPlan(
                BossPhase.OVERLOAD, 4, 0.0D, true);
        check(plan.preferOuterRing(), "a Core-standing target must force an outer-ring plan");
        check(plan.preferredDistance() >= CombatTacticsPolicy.MIN_BOSS_DISTANCE,
                "outer-ring plan must stay outside the Core safety distance");
    }

    private static void testObjectiveRolesAreDistinctAndDeterministic() {
        for (EndRiftObjective.Objective objective : EndRiftObjective.Objective.values()) {
            CombatTacticsPolicy.MobTactic first = CombatTacticsPolicy.tacticFor(
                    objective, "COMMON", 0);
            CombatTacticsPolicy.MobTactic second = CombatTacticsPolicy.tacticFor(
                    objective, "COMMON", 99);
            check(first == second, "objective assignment must be stable: " + objective);
        }
        check(CombatTacticsPolicy.tacticFor(EndRiftObjective.Objective.OBELISK_ASSAULT,
                "OBELISK_GUARD", 0) == CombatTacticsPolicy.MobTactic.OBELISK_GUARD,
                "Wave 4 guards must stay assigned to obelisks");
        check(CombatTacticsPolicy.tacticFor(EndRiftObjective.Objective.REALITY_SPLIT,
                "CHAMBER_BLADE", 0) == CombatTacticsPolicy.MobTactic.CHAMBER_BLADE,
                "Wave 7 blades must stay assigned to their chamber");
    }

    private static void testWaveManeuversAreBoundedAndRepeatable() {
        CombatTacticsPolicy.MobManeuver first = CombatTacticsPolicy.maneuverFor(
                EndRiftObjective.Objective.BLACK_FOG, "COMMON", 1, 2);
        CombatTacticsPolicy.MobManeuver second = CombatTacticsPolicy.maneuverFor(
                EndRiftObjective.Objective.BLACK_FOG, "COMMON", 1, 2);
        check(first == second, "maneuver must be deterministic for a stable slot");
        check(CombatTacticsPolicy.maneuverFor(
                EndRiftObjective.Objective.OBELISK_ASSAULT, "OBELISK_GUARD", 0, 0)
                == CombatTacticsPolicy.MobManeuver.CIRCLE_OBJECTIVE,
                "obelisk guard must circle its objective on the first beat");
        check(CombatTacticsPolicy.maneuverFor(
                EndRiftObjective.Objective.REALITY_SPLIT, "CHAMBER_BLADE", 0, 0)
                == CombatTacticsPolicy.MobManeuver.BREAK_LINE,
                "chamber blade must use a bounded line-breaking beat");
    }

    private static void testTeleportPermitIsScopedAndSingleUse() {
        BossTeleportPermitPolicy.Permit permit = BossTeleportPermitPolicy.issue(
                "event-a", 10L, "boss-a", 100L, 200L);
        check(BossTeleportPermitPolicy.accept(permit, "event-a", 10L, "boss-a", 150L),
                "fresh permit must be accepted");
        check(!BossTeleportPermitPolicy.accept(permit, "event-a", 10L, "boss-a", 150L),
                "permit must be single-use");
        BossTeleportPermitPolicy.Permit other = BossTeleportPermitPolicy.issue(
                "event-a", 10L, "boss-a", 100L, 200L);
        check(!BossTeleportPermitPolicy.accept(other, "event-a", 11L, "boss-a", 150L),
                "stale generation must be rejected");
        BossTeleportPermitPolicy.Permit expired = BossTeleportPermitPolicy.issue(
                "event-a", 10L, "boss-a", 100L, 200L);
        check(!BossTeleportPermitPolicy.accept(expired, "event-a", 10L, "boss-a", 201L),
                "permit must expire after its deadline");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
