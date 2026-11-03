package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Proves the Warden answers a pile and an encirclement, and not a spread party. */
public final class WardenSlamTriggerPolicyTest {
    private static int failures;

    public static void main(String[] args) {
        aPileOfThreeIsSlammed();
        twoPlayersAreNeverAPile();
        anEncirclementIsSlammed();
        aPartySwingingFromOneSideIsNotAnEncirclement();
        aCoolingWardenIgnoresAPile();
        anEmptyArenaIsNeverSlammed();
        theLargestPileIsMeasured();
        System.out.println(failures == 0 ? "WardenSlamTriggerPolicyTest: PASS"
                : "WardenSlamTriggerPolicyTest: FAIL " + failures);
        if (failures > 0) System.exit(1);
    }

    private static void aPileOfThreeIsSlammed() {
        var decision = WardenSlamTriggerPolicy.evaluate(List.of(
                at(1.0, 0.0), at(0.5, 1.0), at(-1.0, 0.4)), true);
        check("pile of three slams", decision.slam());
        check("pile reason", decision.reason() == WardenSlamTriggerPolicy.Reason.CLUSTER);
        check("pile size three", decision.pileSize() == 3);
    }

    private static void twoPlayersAreNeverAPile() {
        var decision = WardenSlamTriggerPolicy.evaluate(List.of(at(0.5, 0.0), at(1.0, 0.5)), true);
        check("two players never slam", !decision.slam());
    }

    private static void anEncirclementIsSlammed() {
        // Three players three blocks out, eleven blocks apart: no pile, but they
        // cover most of the compass around the Warden.
        var decision = WardenSlamTriggerPolicy.evaluate(List.of(
                at(3.0, 0.0), at(-1.5, 2.6), at(-1.5, -2.6)), true);
        check("encirclement slams", decision.slam());
        check("encirclement reason", decision.reason() == WardenSlamTriggerPolicy.Reason.ENCIRCLED);
        check("encirclement coverage", decision.coverage() > 0.6D);
    }

    private static void aPartySwingingFromOneSideIsNotAnEncirclement() {
        // Everyone east of the Warden: whatever he faces, that is where they are.
        // NOTE: an earlier version of this test used (4,0),(4,3),(4,-3) and
        // expected no slam -- that expectation was WRONG. Those three sit in
        // three different quadrants (east, south-east, north-east), which really
        // is an encirclement and really should be slammed. Only a party that all
        // stays within one quadrant is being gamed.
        var decision = WardenSlamTriggerPolicy.evaluate(List.of(
                at(4.0, 3.2), at(1.2, 4.6), at(5.6, 1.6)), true);
        check("one-quadrant party is not an encirclement", !decision.slam());
    }

    private static void aCoolingWardenIgnoresAPile() {
        var decision = WardenSlamTriggerPolicy.evaluate(List.of(
                at(1.0, 0.0), at(0.5, 1.0), at(-1.0, 0.4)), false);
        check("cooldown blocks the slam", !decision.slam());
        check("cooldown reason is none", decision.reason() == WardenSlamTriggerPolicy.Reason.NONE);
    }

    private static void anEmptyArenaIsNeverSlammed() {
        check("nobody around", !WardenSlamTriggerPolicy.evaluate(List.of(), true).slam());
    }

    private static void theLargestPileIsMeasured() {
        List<WardenSlamTriggerPolicy.Around> around = new ArrayList<>();
        around.add(at(0.0, 0.0));
        around.add(at(0.5, 0.0));
        around.add(at(9.0, 9.0));
        check("largest pile", WardenSlamTriggerPolicy.largestPile(around) == 2);
    }

    private static WardenSlamTriggerPolicy.Around at(double dx, double dz) {
        return new WardenSlamTriggerPolicy.Around(UUID.randomUUID(), dx, dz);
    }

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + label);
        }
    }
}
