import me.copimine.endevent.domain.Wave5PostFogPackPolicy;
import me.copimine.endevent.domain.WaveMechanicsPolicy;

/**
 * W5-004: "After EACH fog, including the last one, spawn one finite pack of 10-15
 * enemies. Do not end immediately on clearing an early pack." The November 9 rules
 * allow five to ten fog activations, so the pack contract follows that ceiling.
 */
public final class Wave5PostFogPackPolicyTest {
    private static int failures;

    public static void main(String[] args) {
        packAlwaysInsideTheContractWindow();
        packGrowsWithTheParty();
        everyCycleOwesAPackIncludingTheLast();
        packsAreTrackedAndNeverGoIntoDebt();
        completionWaitsForOwedPacks();
        composedPackKeepsBothRoles();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("Wave5PostFogPackPolicyTest OK");
    }

    private static void packAlwaysInsideTheContractWindow() {
        for (int players = 0; players <= 64; players++) {
            int size = Wave5PostFogPackPolicy.packSize(players);
            check(size >= 10 && size <= 15,
                    "pack for " + players + " players must be 10-15, was " + size);
        }
    }

    private static void packGrowsWithTheParty() {
        int small = Wave5PostFogPackPolicy.packSize(2);
        int large = Wave5PostFogPackPolicy.packSize(20);
        check(small == 10, "the smallest party takes the floor of 10, was " + small);
        check(large == 15, "the full roster takes the ceiling of 15, was " + large);
        int previous = 0;
        for (int i = 2; i <= 20; i++) {
            int size = Wave5PostFogPackPolicy.packSize(i);
            check(size >= previous, "pack size must be monotonic, " + i + " players gave " + size);
            previous = size;
        }
    }

    private static void everyCycleOwesAPackIncludingTheLast() {
        // The November 9 rules replaced the old three-cycle model with five to ten fog
        // activations, so the pack contract runs to the policy's own ceiling instead of
        // stopping at five. A sixth, seventh, eighth, ninth and tenth cycle are all part
        // of the contract; only a cycle beyond that ceiling owes nothing.
        int ceiling = Wave5PostFogPackPolicy.REQUIRED_CYCLES;
        check(ceiling >= 5 && ceiling <= 10,
                "the pack ceiling must follow the 5-10 fog contract, was " + ceiling);
        check(!Wave5PostFogPackPolicy.cycleOwesPack(0),
                "no pack is owed before the first fog resolves");
        for (int cycle = 1; cycle <= ceiling; cycle++) {
            check(Wave5PostFogPackPolicy.cycleOwesPack(cycle),
                    "cycle " + cycle + " must owe a pack, including the final one");
        }
        check(!Wave5PostFogPackPolicy.cycleOwesPack(ceiling + 1),
                "a cycle past the " + ceiling + "-cycle ceiling is not part of the contract");
    }

    private static void packsAreTrackedAndNeverGoIntoDebt() {
        check(Wave5PostFogPackPolicy.packsOwed(5, 0) == 5, "five cycles without packs owe five");
        check(Wave5PostFogPackPolicy.packsOwed(5, 3) == 2, "five cycles with three packs owe two");
        check(Wave5PostFogPackPolicy.packsOwed(5, 5) == 0, "five cycles with five packs owe nothing");
        check(Wave5PostFogPackPolicy.packsOwed(5, 9) == 0,
                "extra packs must not create negative debt");
        check(Wave5PostFogPackPolicy.packsOwed(0, 0) == 0, "no cycles owe nothing");
    }

    private static void completionWaitsForOwedPacks() {
        check(Wave5PostFogPackPolicy.blocksCompletion(5, 4),
                "the fifth pack missing must block completion");
        check(!Wave5PostFogPackPolicy.blocksCompletion(5, 5),
                "all five packs resolved means completion is free to proceed");
        check(!Wave5PostFogPackPolicy.blocksCompletion(3, 3),
                "a mid-run balance does not block anything by itself");
    }

    private static void composedPackKeepsBothRoles() {
        for (int size = 10; size <= 15; size++) {
            WaveMechanicsPolicy.WaveCounts counts = Wave5PostFogPackPolicy.compose(size);
            check(counts.total() == size, "composed pack must total " + size + ", was " + counts.total());
            check(counts.skeletons() > 0, "a pack of " + size + " needs ranged pressure");
            check(counts.endermen() + counts.spiders() > 0, "a pack of " + size + " needs melee pressure");
            check(counts.endermen() > 0 && counts.spiders() > 0,
                    "melee must be mixed, not one mob type, for size " + size);
        }
        check(Wave5PostFogPackPolicy.compose(0).total() == 0, "an empty pack stays empty");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + message);
        }
    }
}
