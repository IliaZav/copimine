package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import me.copimine.endevent.domain.WaveSixStageState.Snapshot;
import me.copimine.endevent.domain.WaveSixStageState.Stage;

/**
 * Runtime-facing advance rules for the Wave 6 stage machine.
 *
 * <p>{@link WaveSixStageState} declares the legal transition graph and the
 * snapshot invariants, but the wave controller never moved the snapshot, so a
 * run could sit in {@code PREPARE} while every fight actually happened. These
 * helpers are the only sanctioned way to move it: each one is keyed to a single
 * observed game event, so advancing is always caused by something that
 * provably happened rather than by a timer.
 *
 * <p>Every method is total and returns a snapshot; a premature call returns the
 * state unchanged instead of throwing, because a tick handler that throws would
 * take the whole wave down.
 */
public final class WaveSixStageAdvancePolicy {
    private WaveSixStageAdvancePolicy() {
    }

    /** Their guards are settled and the first mage is now the fight. */
    public static Snapshot onPrepareComplete(Snapshot state) {
        if (state == null || state.stage() != Stage.PREPARE) {
            return state;
        }
        return state.withStage(Stage.GUARDED);
    }

    /**
     * Revision of the guard count.
     *
     * <p>The shield comes off only when every living guard is gone. A partial
     * break leaves the mage guarded: this is what stops the wave advancing on a
     * guard that merely wandered out of range.
     */
    public static Snapshot onGuardBreak(Snapshot state, int livingGuards) {
        if (state == null || state.stage() != Stage.GUARDED || livingGuards > 0) {
            return state;
        }
        return state.withStage(Stage.EXPOSED);
    }

    /** The current mage died; record its identity and move to TRANSFER. */
    public static Snapshot onDefeated(Snapshot state) {
        if (state == null || state.stage() != Stage.EXPOSED) {
            return state;
        }
        MageSignaturePolicy.MageId current = state.currentMage();
        if (current == null || state.defeated().contains(current)) {
            return state;
        }
        List<MageSignaturePolicy.MageId> defeated = new ArrayList<>(state.defeated());
        defeated.add(current);
        // The snapshot invariant is explicit: the fifth defeat breaks the prison
        // as it happens, it never waits for the drain to notice afterwards.
        boolean prisonBroken = state.prisonBroken()
                || defeated.size() >= MageSignaturePolicy.MAGE_COUNT;
        return new Snapshot(Stage.TRANSFER, defeated.size(), state.order(),
                defeated, state.prisonerCaptured(), prisonBroken);
    }

    /** Its signature has been handed over. */
    public static Snapshot onTransferComplete(Snapshot state) {
        if (state == null || state.stage() != Stage.TRANSFER) {
            return state;
        }
        return state.withStage(Stage.DRAIN);
    }

    /**
     * The drain resolves. With mages left it hands over; on the last mage it
     * opens the final release, which is also the moment the prison breaks.
     */
    public static Snapshot onDrainComplete(Snapshot state) {
        if (state == null || state.stage() != Stage.DRAIN) {
            return state;
        }
        if (state.defeated().size() < MageSignaturePolicy.MAGE_COUNT) {
            return state.withStage(Stage.NEXT_STAGE);
        }
        if (state.prisonBroken()) {
            return state.withStage(Stage.FINAL_RELEASE);
        }
        return new Snapshot(Stage.FINAL_RELEASE, state.stageIndex(), state.order(),
                state.defeated(), state.prisonerCaptured(), true);
    }

    /** The next mage is placed and guarded. */
    public static Snapshot onNextStageReady(Snapshot state) {
        if (state == null || state.stage() != Stage.NEXT_STAGE) {
            return state;
        }
        return state.withStage(Stage.GUARDED);
    }

    /**
     * The final release resolves.
     *
     * @param prisonerFreed whether the prisoner actually left the cell; a
     *                      release that left them held does not complete
     */
    public static Snapshot onFinalReleaseComplete(Snapshot state, boolean prisonerFreed) {
        if (state == null || state.stage() != Stage.FINAL_RELEASE || !prisonerFreed) {
            return state;
        }
        return new Snapshot(Stage.FINAL_CLEAR, state.stageIndex(), state.order(),
                state.defeated(), true, state.prisonBroken());
    }

    /** Cleanup finished; the wave is terminal and never re-enters a hostile stage. */
    public static Snapshot onFinalClearComplete(Snapshot state) {
        if (state == null || state.stage() != Stage.FINAL_CLEAR) {
            return state;
        }
        return state.withStage(Stage.COMPLETE);
    }
}