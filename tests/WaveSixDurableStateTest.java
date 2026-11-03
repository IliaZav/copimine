package me.copimine.endevent.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Proves the Wave 6 durable receipt survives a restart without loss or duplication. */
public final class WaveSixDurableStateTest {
    private static int failures;

    public static void main(String[] args) {
        roundTripPreservesTheStageAndTheDebt();
        aReleasedPackIsNeverClaimedBeforeItWasCommitted();
        stageIndexMustAgreeWithTheDefeatReceipts();
        guardSlotsCannotExceedTheAuthoredRing();
        unknownKeysAreRefused();
        anAbsentReceiptIsReportedAsAbsent();
        outstandingPacksIsTheDifference();
        System.out.println(failures == 0 ? "WaveSixDurableStateTest: PASS"
                : "WaveSixDurableStateTest: FAIL " + failures);
        if (failures > 0) System.exit(1);
    }

    private static void roundTripPreservesTheStageAndTheDebt() {
        List<MageSignaturePolicy.MageId> order = MageSignaturePolicy.selectOrder(
                java.util.random.RandomGenerator.of("L64X128MixRandom"));
        List<MageSignaturePolicy.MageId> defeated = order.subList(0, 2);
        var encoded = WaveSixDurableState.encode(2, 3, 2, 1, false);
        var decoded = WaveSixDurableState.decode(encoded, defeated, 4);
        check("receipt is present", decoded.present());
        check("stage survives", decoded.requireState().stageIndex() == 2);
        check("filled slots survive", decoded.requireState().guardSlotsFilled() == 3);
        check("owed survives", decoded.requireState().reinforcementOwed() == 2);
        check("spawned survives", decoded.requireState().reinforcementSpawned() == 1);
        check("final clear survives", !decoded.requireState().finalClearCommitted());
    }

    private static void aReleasedPackIsNeverClaimedBeforeItWasCommitted() {
        boolean refused = false;
        try {
            WaveSixDurableState.encode(1, 0, 1, 3, false);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check("more released than committed is refused", refused);
    }

    private static void stageIndexMustAgreeWithTheDefeatReceipts() {
        List<MageSignaturePolicy.MageId> order = MageSignaturePolicy.selectOrder(
                java.util.random.RandomGenerator.of("L64X128MixRandom"));
        boolean refused = false;
        try {
            // Two deaths recorded but the receipt claims stage 3.
            WaveSixDurableState.decode(WaveSixDurableState.encode(3, 0, 3, 0, false),
                    order.subList(0, 2), 4);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check("stage/defeat disagreement is refused", refused);
    }

    private static void guardSlotsCannotExceedTheAuthoredRing() {
        boolean refused = false;
        try {
            WaveSixDurableState.decode(WaveSixDurableState.encode(0, 9, 0, 0, false),
                    List.of(), 4);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check("oversized guard fill is refused", refused);
    }

    private static void unknownKeysAreRefused() {
        var encoded = new java.util.LinkedHashMap<>(WaveSixDurableState.encode(0, 0, 0, 0, false));
        encoded.put("wave6.invented", "1");
        boolean refused = false;
        try {
            WaveSixDurableState.decode(encoded, List.of(), 4);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check("unknown key is refused", refused);
    }

    private static void anAbsentReceiptIsReportedAsAbsent() {
        var decoded = WaveSixDurableState.decode(java.util.Map.of("ritual-sphere.x", "1"),
                List.of(), 4);
        check("absent receipt reported", !decoded.present());
        check("absent receipt has no state", decoded.state() == null);
    }

    private static void outstandingPacksIsTheDifference() {
        var state = new WaveSixDurableState.State(4, 0, 4, 1, false);
        check("outstanding packs", state.outstandingPacks() == 3);
        check("stage still active", state.stageActive());
        check("finished stage", !new WaveSixDurableState.State(5, 0, 5, 5, true).stageActive());
    }

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + label);
        }
    }
}
