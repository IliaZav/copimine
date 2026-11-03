package me.copimine.endevent.runtime;

import java.util.List;

/** Audit ECHO error 10: the ordered fight history must survive compression and a restart. */
public final class CombatSequenceTest {
    public static void main(String[] args) {
        CombatSequenceTest test = new CombatSequenceTest();
        test.compressesRepeatsWithoutLosingOrder();
        test.differentHistoriesLookDifferent();
        test.survivesAnEncodeDecodeRoundTrip();
        test.boundsGrowth();
        System.out.println("CombatSequenceTest OK");
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void compressesRepeatsWithoutLosingOrder() {
        CombatSequence sequence = new CombatSequence();
        for (int i = 0; i < 50; i++) sequence.record(1, "melee", 1);
        for (int i = 0; i < 30; i++) sequence.record(2, "ranged", 1);
        for (int i = 0; i < 20; i++) sequence.record(2, "melee", 1);
        check(sequence.size() == 3, "repeats were not compressed: " + sequence.size());
        check(sequence.totalActions() == 100L, "actions lost: " + sequence.totalActions());
        check(sequence.transitions() == 2, "transitions wrong: " + sequence.transitions());
        List<CombatSequence.Run> runs = sequence.runs();
        check(runs.get(0).action().equals("melee") && runs.get(0).wave() == 1, "first run wrong");
        check(runs.get(1).action().equals("ranged") && runs.get(1).count() == 30L, "second run wrong");
        check(runs.get(2).action().equals("melee") && runs.get(2).count() == 20L, "third run wrong");
    }

    /** Two players with the same totals but a different shape must read differently. */
    private void differentHistoriesLookDifferent() {
        CombatSequence alternating = new CombatSequence();
        for (int i = 0; i < 20; i++) {
            alternating.record(1, "melee", 1);
            alternating.record(1, "ranged", 1);
        }
        CombatSequence straight = new CombatSequence();
        for (int i = 0; i < 20; i++) straight.record(1, "melee", 1);
        for (int i = 0; i < 20; i++) straight.record(1, "ranged", 1);
        check(alternating.totalActions() == straight.totalActions(), "totals should match");
        check(alternating.transitions() != straight.transitions(),
                "different histories must not collapse to the same profile");
        check(!alternating.encode().equals(straight.encode()), "encoded histories are identical");
    }

    private void survivesAnEncodeDecodeRoundTrip() {
        CombatSequence original = new CombatSequence();
        original.record(1, "melee", 3);
        original.record(2, "ranged", 1);
        original.record(5, "heal", 2);
        original.record(7, "shield", 1);
        String encoded = original.encode();
        CombatSequence decoded = CombatSequence.decode(encoded);
        check(decoded.runs().equals(original.runs()), "round trip lost the history");
        check(decoded.encode().equals(encoded), "encoding is not stable");
        // A damaged payload degrades to "nothing known", never to an exception.
        check(CombatSequence.decode("garbage;;1::2;9:x:y").isEmpty(), "damaged payload was accepted");
        check(CombatSequence.decode(null).isEmpty(), "null payload was accepted");
    }

    private void boundsGrowth() {
        CombatSequence sequence = new CombatSequence();
        for (int i = 0; i < 5000; i++) {
            sequence.record(1 + (i % 7), "action-" + (i % 13), 1);
        }
        check(sequence.size() <= CombatSequence.MAX_RUNS, "sequence grew past its bound");
        check(!sequence.isEmpty(), "bounded sequence lost everything");
    }
}
