import me.copimine.endevent.domain.WaveTwoPhantomMaterializationPolicy;
import me.copimine.endevent.domain.WaveTwoPhantomMaterializationPolicy.Action;
import me.copimine.endevent.domain.WaveTwoPhantomMaterializationPolicy.Budget;
import me.copimine.endevent.domain.WaveTwoPhantomMaterializationPolicy.Candidate;
import me.copimine.endevent.domain.WaveTwoPhantomMaterializationPolicy.Decision;
import me.copimine.endevent.domain.WaveTwoPhantomMaterializationPolicy.Fence;
import me.copimine.endevent.domain.WaveTwoPhantomMaterializationPolicy.State;

/** Deterministic contract test for the pure Wave 2 phantom activation state machine. */
public final class WaveTwoPhantomMaterializationPolicyTest {
    public static void main(String[] args) {
        Fence fence = new Fence("event-A", 7L, 2);
        Candidate candidate = new Candidate(fence, "phantom-14", true);
        Budget budget = new Budget(10, 12, 2, 7);

        Decision warning = WaveTwoPhantomMaterializationPolicy.begin(null, fence, candidate, budget);
        check(warning.action() == Action.WARNING_STARTED,
                "a safe selected phantom starts a warning before any targetable entity exists");
        State pending = warning.state();
        check(WaveTwoPhantomMaterializationPolicy.ownsActivation(pending, fence, "phantom-14")
                        && !WaveTwoPhantomMaterializationPolicy.ownsActivation(
                        pending, new Fence("event-A", 7L, 3), "phantom-14")
                        && !WaveTwoPhantomMaterializationPolicy.ownsActivation(
                        pending, fence, "phantom-stale"),
                "a delayed warning callback can clear only its still-owned phantom activation");
        check(pending.phase() == WaveTwoPhantomMaterializationPolicy.Phase.WARNING
                        && warning.entityBudgetCost() == 0,
                "warning phase has no materialized entity or slot cost");

        Decision waiting = WaveTwoPhantomMaterializationPolicy.finishWarning(
                pending, fence, false, true, budget);
        check(waiting.action() == Action.WAITING_FOR_WARNING
                        && waiting.state().phase() == WaveTwoPhantomMaterializationPolicy.Phase.WARNING,
                "an incomplete caller-owned warning does not expose a targetable entity");
        check(WaveTwoPhantomMaterializationPolicy.acceptHit(
                        pending, fence, "phantom-14").action() == Action.REJECTED,
                "a warning-only phantom cannot receive a targetable hit");
        check(WaveTwoPhantomMaterializationPolicy.begin(
                        pending, fence, new Candidate(fence, "phantom-15", true), budget).action()
                        == Action.REJECTED,
                "only one phantom can be active during the warning transaction");

        Decision materialized = WaveTwoPhantomMaterializationPolicy.finishWarning(
                waiting.state(), fence, true, true, budget);
        check(materialized.action() == Action.MATERIALIZE_ENTITY
                        && materialized.state().phase() == WaveTwoPhantomMaterializationPolicy.Phase.TARGETABLE
                        && materialized.entityBudgetCost() == 1,
                "only a completed warning materializes one budgeted target");

        Decision duplicate = WaveTwoPhantomMaterializationPolicy.begin(
                materialized.state(), fence, candidate, budget);
        Candidate otherCandidate = new Candidate(fence, "phantom-15", true);
        Decision competing = WaveTwoPhantomMaterializationPolicy.begin(
                materialized.state(), fence, otherCandidate, budget);
        check(duplicate.action() == Action.DUPLICATE_ACTIVATION
                        && competing.action() == Action.REJECTED,
                "an active materialization is idempotent and blocks concurrent phantom selection");

        Decision hit = WaveTwoPhantomMaterializationPolicy.acceptHit(
                materialized.state(), fence, "phantom-14");
        Decision duplicateHit = WaveTwoPhantomMaterializationPolicy.acceptHit(
                hit.state(), fence, "phantom-14");
        Decision activationAfterHit = WaveTwoPhantomMaterializationPolicy.begin(
                hit.state(), fence, candidate, budget);
        Budget oneActivationLeft = new Budget(10, 12, 1, 6);
        Decision nextPhantom = WaveTwoPhantomMaterializationPolicy.begin(
                hit.state(), fence, otherCandidate, oneActivationLeft);
        Decision previouslyUsed = WaveTwoPhantomMaterializationPolicy.begin(
                nextPhantom.state(), fence, candidate, oneActivationLeft);
        Decision secondMaterialized = WaveTwoPhantomMaterializationPolicy.finishWarning(
                nextPhantom.state(), fence, true, true, oneActivationLeft);
        Decision secondHit = WaveTwoPhantomMaterializationPolicy.acceptHit(
                secondMaterialized.state(), fence, "phantom-15");
        Decision firstIdAfterSecondHit = WaveTwoPhantomMaterializationPolicy.begin(
                secondHit.state(), fence, candidate, oneActivationLeft);
        check(hit.action() == Action.HIT_ACCEPTED
                        && hit.state().phase() == WaveTwoPhantomMaterializationPolicy.Phase.HIT
                        && duplicateHit.action() == Action.DUPLICATE_HIT
                        && activationAfterHit.action() == Action.DUPLICATE_ACTIVATION,
                "the materialized phantom accepts at most one terminal hit");
        Fence nextGroupFence = new Fence("event-A", 7L, 3);
        Candidate nextGroupPhantom = new Candidate(nextGroupFence, "phantom-15", true);
        Decision nextGroupWhileActive = WaveTwoPhantomMaterializationPolicy.begin(
                materialized.state(), nextGroupFence, nextGroupPhantom, budget);
        Decision nextGroupAfterHit = WaveTwoPhantomMaterializationPolicy.begin(
                hit.state(), nextGroupFence, nextGroupPhantom, oneActivationLeft);
        Decision nextGroupMaterialized = WaveTwoPhantomMaterializationPolicy.finishWarning(
                nextGroupAfterHit.state(), nextGroupFence, true, true, oneActivationLeft);
        Decision nextGroupHit = WaveTwoPhantomMaterializationPolicy.acceptHit(
                nextGroupMaterialized.state(), nextGroupFence, "phantom-15");
        Decision reusedAcrossGroups = WaveTwoPhantomMaterializationPolicy.begin(
                nextGroupHit.state(), nextGroupFence,
                new Candidate(nextGroupFence, "phantom-14", true), oneActivationLeft);
        check(nextGroupWhileActive.action() == Action.REJECTED
                        && nextGroupAfterHit.action() == Action.WARNING_STARTED
                        && reusedAcrossGroups.action() == Action.DUPLICATE_ACTIVATION,
                "a new group waits for the prior entity to finish and retains used IDs across group fences");
        check(nextPhantom.action() == Action.WARNING_STARTED
                        && previouslyUsed.action() == Action.DUPLICATE_ACTIVATION
                        && secondMaterialized.action() == Action.MATERIALIZE_ENTITY
                        && secondMaterialized.entityBudgetCost() == 1
                        && secondHit.action() == Action.HIT_ACCEPTED
                        && firstIdAfterSecondHit.action() == Action.DUPLICATE_ACTIVATION,
                "a terminal phantom permits the next distinct phantom but never reuses an old ID");
        Budget noActivation = new Budget(10, 12, 0, 6);
        check(WaveTwoPhantomMaterializationPolicy.begin(
                        secondHit.state(), fence, new Candidate(fence, "phantom-16", true), noActivation)
                        .action() == Action.REJECTED,
                "positive total reserve cannot bypass an exhausted late-activation allowance");

        Candidate unsafe = new Candidate(fence, "phantom-unsafe", false);
        check(WaveTwoPhantomMaterializationPolicy.begin(null, fence, unsafe, budget).action()
                        == Action.REJECTED,
                "a candidate outside a validated arena position cannot start");
        Budget fullPressure = new Budget(12, 12, 2, 7);
        Budget noReserve = new Budget(10, 12, 2, 0);
        check(WaveTwoPhantomMaterializationPolicy.begin(null, fence, candidate, fullPressure).action()
                        == Action.REJECTED
                        && WaveTwoPhantomMaterializationPolicy.begin(null, fence, candidate, noReserve).action()
                        == Action.REJECTED,
                "activation fails closed when pressure slots or total reserve are exhausted");
        Decision startedForBudget = WaveTwoPhantomMaterializationPolicy.begin(null, fence, candidate, budget);
        Decision blockedAtMaterialization = WaveTwoPhantomMaterializationPolicy.finishWarning(
                startedForBudget.state(), fence, true, true, fullPressure);
        Decision blockedAtArena = WaveTwoPhantomMaterializationPolicy.finishWarning(
                startedForBudget.state(), fence, true, false, budget);
        check(blockedAtMaterialization.action() == Action.CANCELLED
                        && blockedAtMaterialization.state().phase()
                        == WaveTwoPhantomMaterializationPolicy.Phase.CANCELLED
                        && blockedAtMaterialization.entityBudgetCost() == 0
                        && blockedAtArena.action() == Action.CANCELLED
                        && blockedAtArena.entityBudgetCost() == 0,
                "warning completion revalidates safe arena position and live entity budget");
        Decision afterCancel = WaveTwoPhantomMaterializationPolicy.begin(
                blockedAtArena.state(), fence, new Candidate(fence, "phantom-16", true), budget);
        check(afterCancel.action() == Action.WARNING_STARTED,
                "a cancelled warning releases the active transaction for a distinct phantom");

        Fence staleFence = new Fence("event-A", 6L, 2);
        Fence staleEvent = new Fence("event-old", 7L, 2);
        Candidate staleCandidate = new Candidate(staleFence, "phantom-old", true);
        Candidate staleEventCandidate = new Candidate(staleEvent, "phantom-old-event", true);
        check(WaveTwoPhantomMaterializationPolicy.begin(null, fence, staleCandidate, budget).action()
                        == Action.REJECTED
                        && WaveTwoPhantomMaterializationPolicy.begin(null, fence, staleEventCandidate, budget)
                        .action() == Action.REJECTED
                        && WaveTwoPhantomMaterializationPolicy.finishWarning(
                        pending, staleFence, true, true, budget).action() == Action.REJECTED
                        && WaveTwoPhantomMaterializationPolicy.acceptHit(
                        materialized.state(), staleFence, "phantom-14").action() == Action.REJECTED,
                "event-generation fencing rejects stale starts, completions, and hits");

        check(WaveTwoPhantomMaterializationPolicy.lateReinforcementCount(20, 20) == 2
                        && WaveTwoPhantomMaterializationPolicy.lateReinforcementCount(20, 1) == 1
                        && WaveTwoPhantomMaterializationPolicy.lateReinforcementCount(0, 20) == 0,
                "late materialization allowance is capped by pressure and total reserve");
        System.out.println("WaveTwoPhantomMaterializationPolicyTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
