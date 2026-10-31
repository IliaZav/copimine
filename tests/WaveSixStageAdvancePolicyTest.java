import java.util.List;
import me.copimine.endevent.domain.MageSignaturePolicy;
import me.copimine.endevent.domain.WaveSixStageAdvancePolicy;
import me.copimine.endevent.domain.WaveSixStageState;
import me.copimine.endevent.domain.WaveSixStageState.Stage;

/**
 * The Wave 6 stage machine existed but was never advanced by the runtime, so a
 * run could sit in PREPARE forever. This pins the runtime-facing advance rules.
 */
public final class WaveSixStageAdvancePolicyTest {
    public static void main(String[] args) {
        preparesThenGuardsTheFirstMage();
        aGuardedMageBecomesExposedOnlyWhenItCanBeHit();
        exposureTransfersIntoTheDrain();
        drainBranchesOnWhetherTheLastMageFell();
        finalReleaseRequiresThePrisonerToBeFree();
        refusesIllegalAndPrematureAdvances();
        System.out.println("WaveSixStageAdvancePolicyTest OK");
    }

    private static void preparesThenGuardsTheFirstMage() {
        var order = mageOrder();
        var fresh = WaveSixStageState.Snapshot.fresh(order);
        check(fresh.stage() == Stage.PREPARE, "a fresh wave starts in PREPARE");
        var guarded = WaveSixStageAdvancePolicy.onPrepareComplete(fresh);
        check(guarded.stage() == Stage.GUARDED, "PREPARE advances to the guarded first mage");
        check(guarded.currentMage() == order.get(0), "the first mage is the active one");
        check(WaveSixStageState.isHostile(guarded.stage()), "a guarded mage is a live combatant");
    }

    private static void aGuardedMageBecomesExposedOnlyWhenItCanBeHit() {
        var guarded = WaveSixStageAdvancePolicy.onPrepareComplete(
                WaveSixStageState.Snapshot.fresh(mageOrder()));
        // Guards alive: the mage is not yet exposed.
        check(WaveSixStageAdvancePolicy.onGuardBreak(guarded, 3).stage() == Stage.GUARDED,
                "living guards keep the mage guarded");
        var exposed = WaveSixStageAdvancePolicy.onGuardBreak(guarded, 0);
        check(exposed.stage() == Stage.EXPOSED,
                "losing every living guard exposes the mage");
    }

    private static void exposureTransfersIntoTheDrain() {
        var exposed = WaveSixStageAdvancePolicy.onGuardBreak(
                WaveSixStageAdvancePolicy.onPrepareComplete(
                        WaveSixStageState.Snapshot.fresh(mageOrder())), 0);
        var transfer = WaveSixStageAdvancePolicy.onDefeated(exposed);
        check(transfer.stage() == Stage.TRANSFER, "a defeated mage transfers its power");
        check(transfer.defeated().size() == 1, "exactly one identity is recorded as defeated");
        var drained = WaveSixStageAdvancePolicy.onTransferComplete(transfer);
        check(drained.stage() == Stage.DRAIN, "TRANSFER completes into DRAIN");
    }

    private static void drainBranchesOnWhetherTheLastMageFell() {
        var state = WaveSixStageAdvancePolicy.onPrepareComplete(
                WaveSixStageState.Snapshot.fresh(mageOrder()));
        // Walk four mages out of five; the drain must continue, not finish.
        for (int i = 0; i < 4; i++) {
            state = WaveSixStageAdvancePolicy.onGuardBreak(state, 0);
            state = WaveSixStageAdvancePolicy.onDefeated(state);
            state = WaveSixStageAdvancePolicy.onTransferComplete(state);
            // The drain resolves for every mage, including the fourth.
            state = WaveSixStageAdvancePolicy.onDrainComplete(state);
            if (i < 3) {
                check(state.stage() == Stage.NEXT_STAGE,
                        "with mages left, DRAIN hands over to the next stage");
                state = WaveSixStageAdvancePolicy.onNextStageReady(state);
                check(state.stage() == Stage.GUARDED, "NEXT_STAGE guards the next mage");
            }
        }
        check(state.defeated().size() == 4, "four identities are defeated");
        check(state.stage() == Stage.NEXT_STAGE,
                "the fourth drain hands over to the last mage");
        state = WaveSixStageAdvancePolicy.onNextStageReady(state);
        state = WaveSixStageAdvancePolicy.onGuardBreak(state, 0);
        state = WaveSixStageAdvancePolicy.onDefeated(state);
        state = WaveSixStageAdvancePolicy.onTransferComplete(state);
        var finalRelease = WaveSixStageAdvancePolicy.onDrainComplete(state);
        check(finalRelease.stage() == Stage.FINAL_RELEASE,
                "with every mage down, DRAIN goes to the final release");
        check(finalRelease.prisonBroken(),
                "the fifth defeat breaks the prison rather than waiting for it");
    }

    private static void finalReleaseRequiresThePrisonerToBeFree() {
        var state = finalReleaseState();
        check(WaveSixStageAdvancePolicy.onFinalReleaseComplete(state, false).stage()
                        == Stage.FINAL_RELEASE,
                "the final release does not complete while the prisoner is still held");
        var cleared = WaveSixStageAdvancePolicy.onFinalReleaseComplete(state, true);
        check(cleared.stage() == Stage.FINAL_CLEAR, "a freed prisoner clears the final stage");
        check(cleared.prisonerCaptured(), "release records that the prisoner was captured");
        var complete = WaveSixStageAdvancePolicy.onFinalClearComplete(cleared);
        check(complete.stage() == Stage.COMPLETE, "FINAL_CLEAR completes the wave");
        check(WaveSixStageState.isComplete(complete.stage()), "COMPLETE is terminal");
        check(WaveSixStageAdvancePolicy.onFinalClearComplete(complete).stage() == Stage.COMPLETE,
                "a completed wave never re-enters a hostile stage");
    }

    private static void refusesIllegalAndPrematureAdvances() {
        var fresh = WaveSixStageState.Snapshot.fresh(mageOrder());
        // PREPARE cannot jump straight to the drain.
        check(!WaveSixStageState.canAdvance(Stage.PREPARE, Stage.DRAIN),
                "PREPARE cannot skip to DRAIN");
        // A guarded mage cannot be treated as defeated: guards must fall first.
        var guarded = WaveSixStageAdvancePolicy.onPrepareComplete(fresh);
        check(WaveSixStageAdvancePolicy.onGuardBreak(guarded, 1).stage() == Stage.GUARDED,
                "a surviving guard blocks exposure regardless of other input");
    }

    private static WaveSixStageState.Snapshot finalReleaseState() {
        var state = WaveSixStageAdvancePolicy.onPrepareComplete(
                WaveSixStageState.Snapshot.fresh(mageOrder()));
        for (int i = 0; i < 5; i++) {
            state = WaveSixStageAdvancePolicy.onGuardBreak(state, 0);
            state = WaveSixStageAdvancePolicy.onDefeated(state);
            state = WaveSixStageAdvancePolicy.onTransferComplete(state);
            state = WaveSixStageAdvancePolicy.onDrainComplete(state);
            if (i < 4) {
                check(state.stage() == Stage.NEXT_STAGE,
                        "the drain hands over after mage " + i);
                state = WaveSixStageAdvancePolicy.onNextStageReady(state);
            }
        }
        check(state.stage() == Stage.FINAL_RELEASE, "five defeats reach the final release");
        return state;
    }

    private static List<MageSignaturePolicy.MageId> mageOrder() {
        return List.of(MageSignaturePolicy.MageId.values());
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}