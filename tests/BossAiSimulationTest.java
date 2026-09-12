import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.BossAbilityId;
import me.copimine.endevent.domain.BossBrain;
import me.copimine.endevent.domain.BossHazardBudget;
import me.copimine.endevent.domain.BossIntent;
import me.copimine.endevent.domain.BossPerceptionSnapshot;
import me.copimine.endevent.domain.BossPhase;

public final class BossAiSimulationTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    public static void main(String[] args) {
        BossBrain brain = new BossBrain();
        BossHazardBudget budget = new BossHazardBudget(BossHazardBudget.profileForPlayers(2));

        BossPerceptionSnapshot stacked = snapshot(
                BossPerceptionSnapshot.PlayerObservation.at(A, 0, 0, 0, 1.0, 5, true),
                BossPerceptionSnapshot.PlayerObservation.at(B, 2, 0, 0, 1.0, 5, true));
        BossBrain.Decision stackDecision = brain.decide(stacked,
                List.of(BossAbilityId.RIFT_PROJECTILE, BossAbilityId.VOID_BLAST),
                null, null, budget, 1L, 0);
        check(stackDecision.intent() == BossIntent.PUNISH_STACK, "stacked party must trigger anti-stack intent");
        check(stackDecision.ability().ability() == BossAbilityId.VOID_BLAST,
                "anti-stack intent must prefer Void Blast");
        BossBrain.Decision rotatedDecision = brain.decide(stacked,
                List.of(BossAbilityId.RIFT_PROJECTILE, BossAbilityId.VOID_BLAST,
                        BossAbilityId.RIFT_ARROWS),
                null, null, budget, 1L, 1);
        check(rotatedDecision.ability().ability() != stackDecision.ability().ability(),
                "a later decision cursor must rotate instead of pinning the third-ranked ability");

        BossPerceptionSnapshot spread = snapshot(
                new BossPerceptionSnapshot.PlayerObservation(A, true, 1.0, 10, true,
                        true, 0, false, false, false, 0, 0, 0, 0, 0, 0, ""),
                new BossPerceptionSnapshot.PlayerObservation(B, true, 1.0, 10, true,
                        false, 0, false, false, false, 12, 0, 0, 0, 0, 0, ""));
        BossBrain.Decision spreadDecision = brain.decide(spread,
                List.of(BossAbilityId.RIFT_PROJECTILE, BossAbilityId.VOID_BLAST),
                null, null, budget, 1L, 0);
        check(spreadDecision.intent() == BossIntent.PUNISH_SPREAD,
                "spread party must trigger spread intent");

        BossPerceptionSnapshot noLos = new BossPerceptionSnapshot(
                BossPhase.HUNT, null, List.of(
                new BossPerceptionSnapshot.PlayerObservation(A, true, 1.0, 8, false,
                        false, 0, false, false, false, 0, 0, 0, 0, 0, 0, "")),
                new BossPerceptionSnapshot.Position(0, 0, 0),
                BossPerceptionSnapshot.HazardSnapshot.EMPTY, "", 1L, false);
        BossBrain.Decision noLosDecision = brain.decide(noLos,
                List.of(BossAbilityId.RIFT_PROJECTILE, BossAbilityId.REPOSITION),
                null, null, budget, 1L, 0);
        check(noLosDecision.ability().ability() == BossAbilityId.REPOSITION,
                "no line of sight must reject projectile and reposition");

        BossHazardBudget hardBudget = new BossHazardBudget(BossHazardBudget.profileForPlayers(2));
        check(hardBudget.reserve(A, BossHazardBudget.MechanicKind.MAJOR_GRAB, 1L).accepted(),
                "first hard control must reserve");
        check(!hardBudget.reserve(A, BossHazardBudget.MechanicKind.FINAL_STRIKE, 1L).accepted(),
                "second hard control on one player must be rejected");
        check(hardBudget.releaseGeneration(1L) == 1, "generation cleanup must release reservation");

        BossPerceptionSnapshot.PlayerObservation survivor =
                new BossPerceptionSnapshot.PlayerObservation(B, true, 0.2, 6, true,
                        false, 3, true, true, false, 1, 0, 0, 0, 0, 0, "");
        BossBrain.Decision survivorDecision = brain.decide(
                new BossPerceptionSnapshot(BossPhase.RAGE, null, List.of(survivor),
                        new BossPerceptionSnapshot.Position(0, 0, 0),
                        BossPerceptionSnapshot.HazardSnapshot.EMPTY, "", 1L, false),
                List.of(BossAbilityId.RIFT_PROJECTILE), null, B, budget, 1L, 0);
        check(survivorDecision.target().target().equals(B), "one survivor remains targetable");
        System.out.println("BossAiSimulationTest OK");
    }

    private static BossPerceptionSnapshot snapshot(BossPerceptionSnapshot.PlayerObservation... players) {
        return new BossPerceptionSnapshot(BossPhase.AWAKENING, null, List.of(players),
                new BossPerceptionSnapshot.Position(0, 0, 0),
                BossPerceptionSnapshot.HazardSnapshot.EMPTY, "", 1L, false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
