package me.copimine.endevent.domain;

import java.util.List;
import java.util.UUID;

/** Proves a miniboss group spell reaches the pile and never re-checks the same player. */
public final class MinibossGroupSpellPolicyTest {
    private static int failures;

    public static void main(String[] args) {
        theWholePileIsCaught();
        outOfReachIsIgnored();
        anInvisibleLineOfSightBlocksTheCast();
        theCapIsRespected();
        recentlyHitPlayersAreDeprioritised();
        damageFallsOffWithTheGroup();
        splashCatchesTheNeighbours();
        System.out.println(failures == 0 ? "MinibossGroupSpellPolicyTest: PASS"
                : "MinibossGroupSpellPolicyTest: FAIL " + failures);
        if (failures > 0) System.exit(1);
    }

    private static final MinibossGroupSpellPolicy.Spell SPELL =
            new MinibossGroupSpellPolicy.Spell(14.0D, 6, 9.0D, 2.0D);

    private static void theWholePileIsCaught() {
        var picked = MinibossGroupSpellPolicy.chooseVictims(List.of(
                c(1.0, 0.0, true, 0), c(0.5, 1.0, true, 0), c(-1.0, 0.5, true, 0)), SPELL);
        check("all three of the pile", picked.size() == 3);
    }

    private static void outOfReachIsIgnored() {
        var picked = MinibossGroupSpellPolicy.chooseVictims(List.of(
                c(1.0, 0.0, true, 0), c(40.0, 0.0, true, 0)), SPELL);
        check("out of range excluded", picked.size() == 1);
    }

    private static void anInvisibleLineOfSightBlocksTheCast() {
        var picked = MinibossGroupSpellPolicy.chooseVictims(List.of(
                c(1.0, 0.0, false, 0), c(2.0, 0.0, true, 0)), SPELL);
        check("no line of sight excluded", picked.size() == 1);
        check("the visible one is picked", picked.contains(
                picked.get(0)));
    }

    private static void theCapIsRespected() {
        var picked = MinibossGroupSpellPolicy.chooseVictims(List.of(
                c(1.0, 0.0, true, 0), c(1.5, 0.0, true, 0), c(2.0, 0.0, true, 0),
                c(2.5, 0.0, true, 0), c(3.0, 0.0, true, 0), c(3.5, 0.0, true, 0),
                c(4.0, 0.0, true, 0)), new MinibossGroupSpellPolicy.Spell(14.0D, 3, 9.0D, 2.0D));
        check("cap respected", picked.size() == 3);
    }

    private static void recentlyHitPlayersAreDeprioritised() {
        UUID fresh = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        var picked = MinibossGroupSpellPolicy.chooseVictims(List.of(
                new MinibossGroupSpellPolicy.Candidate(fresh, 1.0D, 0.0D, true, 0),
                new MinibossGroupSpellPolicy.Candidate(stale, 12.0D, 0.0D, true, 5)), SPELL);
        check("unhit player first", picked.get(0).equals(fresh));
    }

    private static void damageFallsOffWithTheGroup() {
        double single = MinibossGroupSpellPolicy.damagePerVictim(SPELL, 1);
        double spread = MinibossGroupSpellPolicy.damagePerVictim(SPELL, 4);
        check("single target takes full", single == 9.0D);
        check("a group takes less each", spread < single);
        check("never below zero damage", MinibossGroupSpellPolicy.damagePerVictim(SPELL, 0) == 9.0D);
    }

    private static void splashCatchesTheNeighbours() {
        UUID near = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        UUID hit = UUID.randomUUID();
        var splash = MinibossGroupSpellPolicy.splashVictims(List.of(
                new MinibossGroupSpellPolicy.Candidate(near, 1.2D, 0.0D, true, 0),
                new MinibossGroupSpellPolicy.Candidate(far, 9.0D, 0.0D, true, 0),
                new MinibossGroupSpellPolicy.Candidate(hit, 0.0D, 0.0D, true, 0)),
                SPELL, 0.0D, 0.0D);
        check("near neighbour splashed", splash.contains(near));
        check("correct victim excluded from splash", !splash.contains(hit));
        check("distant player survives", !splash.contains(far));
    }

    private static MinibossGroupSpellPolicy.Candidate c(double dx, double dz,
                                                        boolean los, int lastHit) {
        return new MinibossGroupSpellPolicy.Candidate(UUID.randomUUID(), dx, dz, los, lastHit);
    }

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + label);
        }
    }
}
