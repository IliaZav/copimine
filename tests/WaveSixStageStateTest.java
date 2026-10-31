import java.util.List;

import me.copimine.endevent.domain.MageSignaturePolicy;
import me.copimine.endevent.domain.MageSignaturePolicy.MageId;
import me.copimine.endevent.domain.WaveSixStageState;
import me.copimine.endevent.domain.WaveSixStageState.Stage;

/**
 * Behavioral contract for the Wave 6 sequential stage machine.
 *
 * <p>Proves three properties the audit cared about: only one mage stage can be
 * hostile at a time; the gave-up/endless-wave bug cannot be produced by a legal
 * transition sequence; and a saved stage restores without inventing a sixth mage
 * or a free prison break.</p>
 */
public final class WaveSixStageStateTest {

    private static int checks;

    private static final List<MageId> ORDER = List.of(
            MageId.KAILEN, MageId.VESPERAL, MageId.NOCTURN, MageId.MALZURETH, MageId.ORRINTH);

    public static void main(String[] args) {
        theLifecycleIsStrictlyForwardOnly();
        aFullRunPassesThroughEveryStageExactlyOnce();
        noEndlessWaveIsReachable();
        singleActiveStageIsInvariant();
        stageIndexTracksDefeats();
        persistenceRoundTrips();
        corruptSnapshotsAreRejected();
        System.out.println("WaveSixStageStateTest: " + checks + " checks passed");
    }

    /** Every legal edge is one-way; nothing re-enters a hostile stage after clear. */
    private static void theLifecycleIsStrictlyForwardOnly() {
        check(WaveSixStageState.canAdvance(Stage.PREPARE, Stage.GUARDED), "prepare -> guarded");
        check(WaveSixStageState.canAdvance(Stage.GUARDED, Stage.EXPOSED), "guards broken -> exposed");
        check(WaveSixStageState.canAdvance(Stage.EXPOSED, Stage.TRANSFER), "exposed -> transfer");
        check(WaveSixStageState.canAdvance(Stage.TRANSFER, Stage.DRAIN), "transfer -> drain");
        check(WaveSixStageState.canAdvance(Stage.DRAIN, Stage.NEXT_STAGE), "drain -> next stage");
        check(WaveSixStageState.canAdvance(Stage.DRAIN, Stage.FINAL_RELEASE), "drain -> final release");
        check(WaveSixStageState.canAdvance(Stage.NEXT_STAGE, Stage.GUARDED), "next stage -> guarded");
        check(WaveSixStageState.canAdvance(Stage.FINAL_RELEASE, Stage.FINAL_CLEAR), "release -> clear");
        check(WaveSixStageState.canAdvance(Stage.FINAL_CLEAR, Stage.COMPLETE), "clear -> complete");

        // The endless-wave defect: re-entering a hostile stage from a terminal one.
        check(!WaveSixStageState.canAdvance(Stage.COMPLETE, Stage.PREPARE),
                "a completed wave cannot restart itself");
        check(!WaveSixStageState.canAdvance(Stage.COMPLETE, Stage.GUARDED),
                "a completed wave cannot re-arm a hostile stage");
        check(!WaveSixStageState.canAdvance(Stage.FINAL_CLEAR, Stage.GUARDED),
                "cleanup cannot jump back into combat");
        check(!WaveSixStageState.canAdvance(Stage.GUARDED, Stage.NEXT_STAGE),
                "a mage cannot be skipped without being exposed and drained");
        check(!WaveSixStageState.canAdvance(Stage.EXPOSED, Stage.GUARDED),
                "a broken shield cannot be restored by a stage change");
    }

    private static void aFullRunPassesThroughEveryStageExactlyOnce() {
        WaveSixStageState.Snapshot state = WaveSixStageState.Snapshot.fresh(ORDER);
        check(state.stage() == Stage.PREPARE, "a fresh wave starts in PREPARE");
        check(state.stageIndex() == 0, "a fresh wave starts at identity zero");
        check(state.currentMage() == ORDER.get(0), "the first mage is the persisted run order head");

        int guardedSeen = 0;
        int exposedSeen = 0;
        state = advance(state, Stage.GUARDED);

        for (int stage = 0; stage < MageSignaturePolicy.MAGE_COUNT; stage++) {
            check(state.stage() == Stage.GUARDED, "each stage begins guarded");
            guardedSeen++;
            state = advance(state, Stage.EXPOSED);
            exposedSeen++;
            state = advance(state, Stage.TRANSFER);
            state = advance(state, Stage.DRAIN);

            MageId defeatedMage = state.currentMage();
            List<MageId> next = MageSignaturePolicy.recordDefeat(
                    ORDER, state.defeated(), defeatedMage);
            check(next != null, "the active mage's defeat is recorded once");

            boolean last = stage == MageSignaturePolicy.MAGE_COUNT - 1;
            // The fifth death breaks the prison in the same transaction: the
            // contract forbids a "released but prison intact" in-between state.
            state = new WaveSixStageState.Snapshot(
                    Stage.DRAIN, next.size(), ORDER, next, true, last);
            if (last) {
                state = advance(state, Stage.FINAL_RELEASE);
            } else {
                state = advance(state, Stage.NEXT_STAGE);
                state = advance(state, Stage.GUARDED);
            }
        }

        check(guardedSeen == MageSignaturePolicy.MAGE_COUNT, "exactly five guarded stages");
        check(exposedSeen == MageSignaturePolicy.MAGE_COUNT, "exactly five exposed stages");
        check(state.stage() == Stage.FINAL_RELEASE, "the fifth defeat releases the prisoner");
        check(state.prisonBroken(), "the fifth defeat breaks the prison immediately");
        check(state.currentMage() == null, "no sixth mage exists after the fifth defeat");

        state = advance(state, Stage.FINAL_CLEAR);
        state = advance(state, Stage.COMPLETE);
        check(WaveSixStageState.isComplete(state.stage()), "the run reaches COMPLETE");
        check(!WaveSixStageState.blocksTransition(state.stage()),
                "a completed wave no longer blocks the next wave");
    }

    /** Until COMPLETE, the wave must keep blocking the transition. */
    private static void noEndlessWaveIsReachable() {
        for (Stage stage : Stage.values()) {
            check(WaveSixStageState.blocksTransition(stage) == (stage != Stage.COMPLETE),
                    "only COMPLETE releases the transition, checked " + stage);
        }
        check(WaveSixStageState.nextStages(Stage.COMPLETE).isEmpty(),
                "COMPLETE has no outgoing edge, so the machine cannot loop");
    }

    /** At most one identity is hostile at any stage index. */
    private static void singleActiveStageIsInvariant() {
        for (int defeated = 0; defeated <= MageSignaturePolicy.MAGE_COUNT; defeated++) {
            List<MageId> defeatedIds = ORDER.subList(0, defeated);
            boolean broken = defeated == MageSignaturePolicy.MAGE_COUNT;
            WaveSixStageState.Snapshot state = new WaveSixStageState.Snapshot(
                    Stage.GUARDED, defeated, ORDER, defeatedIds, true, broken);
            check(state.hasSingleActiveStage(), "one active stage at index " + defeated);
            MageId current = state.currentMage();
            if (defeated == MageSignaturePolicy.MAGE_COUNT) {
                check(current == null, "index five has no current mage");
            } else {
                check(current == ORDER.get(defeated), "the active mage follows the run order");
                check(!defeatedIds.contains(current), "the active mage is not already defeated");
            }
        }
    }

    /** The stage index is the defeated count, so they can never disagree. */
    private static void stageIndexTracksDefeats() {
        expectReject(() -> new WaveSixStageState.Snapshot(
                        Stage.GUARDED, 3, ORDER, List.of(ORDER.get(0)), true, false),
                "index disagreeing with defeated count");
        expectReject(() -> new WaveSixStageState.Snapshot(
                        Stage.GUARDED, 0, ORDER, List.of(MageId.MALZURETH), true, false),
                "a defeated identity must be the prefix of the run order");
        expectReject(() -> new WaveSixStageState.Snapshot(
                        Stage.FINAL_CLEAR, 5, ORDER,
                        List.of(ORDER.get(0), ORDER.get(1), ORDER.get(2), ORDER.get(3), ORDER.get(4)),
                        true, false),
                "five defeats without a broken prison");
    }

    private static void persistenceRoundTrips() {
        WaveSixStageState.Snapshot original = new WaveSixStageState.Snapshot(
                Stage.DRAIN, 2, ORDER, ORDER.subList(0, 2), true, false);
        String encoded = original.encode();
        WaveSixStageState.Snapshot restored =
                WaveSixStageState.Snapshot.decode(encoded, "event", 1L);
        check(restored.equals(original), "stage snapshot round-trips exactly");
        check(restored.currentMage() == ORDER.get(2), "the restored mage is the same identity");

        WaveSixStageState.Snapshot fresh = WaveSixStageState.Snapshot.fresh(ORDER);
        check(WaveSixStageState.Snapshot.decode(fresh.encode(), "event", 1L).equals(fresh),
                "a fresh snapshot round-trips too");
    }

    private static void corruptSnapshotsAreRejected() {
        WaveSixStageState.Snapshot valid = new WaveSixStageState.Snapshot(
                Stage.GUARDED, 1, ORDER, ORDER.subList(0, 1), false, false);
        String good = valid.encode();

        expectReject(() -> WaveSixStageState.Snapshot.decode(
                        good.replace("schema=2", "schema=1"), "event", 1L),
                "unsupported schema");
        expectReject(() -> WaveSixStageState.Snapshot.decode(
                        good + ";extra=1", "event", 1L),
                "unknown key");
        expectReject(() -> WaveSixStageState.Snapshot.decode(
                        good.replace("stage=GUARDED", "stage=BANANA"), "event", 1L),
                "unknown stage");
        expectReject(() -> WaveSixStageState.Snapshot.decode(null, "event", 1L),
                "missing snapshot");
        expectReject(() -> WaveSixStageState.Snapshot.decode(
                        "schema=2;stage=GUARDED;index=0;order=NOCTURN,NOCTURN,KAILEN,MALZURETH,ORRINTH"
                                + ";defeated=;captured=0;broken=0",
                        "event", 1L),
                "duplicate identity in persisted order");
    }

    private static WaveSixStageState.Snapshot advance(WaveSixStageState.Snapshot state, Stage next) {
        return state.withStage(next);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectReject(Runnable action, String label) {
        checks++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected rejection for " + label);
    }
}