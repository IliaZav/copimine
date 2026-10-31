import java.util.List;

import me.copimine.endevent.domain.EliteCombatStatsPolicy;
import me.copimine.endevent.domain.MageSignaturePolicy;
import me.copimine.endevent.domain.WaveSixRosterPolicy;

/**
 * Behavioral contract for the Wave 6 roster tables.
 *
 * <p>Two separate invariants are proved here: the guard-count table never
 * exceeds the contractual five and never regresses as the party grows; and the
 * "enhanced elite guard" is the ordinary elite calculation applied exactly once,
 * never an extra multiplier stacked on an already-scaled body.</p>
 */
public final class WaveSixRosterPolicyTest {

    private static int checks;

    public static void main(String[] args) {
        guardTableMatchesTheContract();
        guardCountNeverExceedsFive();
        guardCountIsMonotonic();
        guardHealthIsTheEliteValueAppliedOnce();
        mageHealthScalesWithTheFrozenRoster();
        invalidRostersAreRejected();
        bindingTableCoversEveryIdentityExactlyOnce();
        System.out.println("WaveSixRosterPolicyTest: " + checks + " checks passed");
    }

    /** The exact published table: 1/2/3/4/5 at each roster band. */
    private static void guardTableMatchesTheContract() {
        expectGuards(2, 1);
        expectGuards(3, 1);
        expectGuards(4, 1);
        expectGuards(5, 2);
        expectGuards(6, 2);
        expectGuards(8, 2);
        expectGuards(9, 3);
        expectGuards(12, 3);
        expectGuards(13, 4);
        expectGuards(16, 4);
        expectGuards(17, 5);
        expectGuards(20, 5);
    }

    private static void guardCountNeverExceedsFive() {
        for (int roster = WaveSixRosterPolicy.MIN_PARTICIPANTS;
                roster <= WaveSixRosterPolicy.MAX_PARTICIPANTS; roster++) {
            int guards = WaveSixRosterPolicy.guardsFor(roster);
            check(guards >= 1, "every stage has at least one guard at roster " + roster);
            check(guards <= WaveSixRosterPolicy.MAX_GUARDS_PER_MAGE,
                    "guards never exceed five at roster " + roster + ", got " + guards);
        }
    }

    /** A larger party must never face fewer guards than a smaller one. */
    private static void guardCountIsMonotonic() {
        int previous = 0;
        for (int roster = WaveSixRosterPolicy.MIN_PARTICIPANTS;
                roster <= WaveSixRosterPolicy.MAX_PARTICIPANTS; roster++) {
            int guards = WaveSixRosterPolicy.guardsFor(roster);
            check(guards >= previous,
                    "guard count must not regress at roster " + roster);
            previous = guards;
        }
    }

    /**
     * The guard rule the specification calls out explicitly: x3 the ordinary
     * role maximum, applied once. Passing an already-elite value must not
     * produce nine times the ordinary value.
     */
    private static void guardHealthIsTheEliteValueAppliedOnce() {
        double ordinary = 20.0D;
        double expectedElite = EliteCombatStatsPolicy.eliteHealth(ordinary);
        check(Math.abs(expectedElite - 60.0D) < 1.0e-9,
                "elite health is exactly three times ordinary, got " + expectedElite);

        // The local constant must stay equal to the shared elite contract.
        check(Math.abs(WaveSixRosterPolicy.ELITE_HEALTH_MULTIPLIER
                        - EliteCombatStatsPolicy.HEALTH_MULTIPLIER) < 1.0e-9,
                "the restated elite multiplier matches the shared policy");

        double guard = WaveSixRosterPolicy.guardHealth(ordinary);
        check(Math.abs(guard - 60.0D) < 1.0e-9,
                "a guard at ordinary 20 has 60 HP, got " + guard);
        check(Math.abs(guard - expectedElite) < 1.0e-9,
                "the guard helper agrees with the shared elite policy");

        expectReject(() -> WaveSixRosterPolicy.guardHealth(0.0D), "zero ordinary health");
        expectReject(() -> WaveSixRosterPolicy.guardHealth(-5.0D), "negative ordinary health");
        expectReject(() -> WaveSixRosterPolicy.guardHealth(Double.NaN), "NaN ordinary health");
    }

    /** Mage health must be a named miniboss profile, distinct from elite stats. */
    private static void mageHealthScalesWithTheFrozenRoster() {
        double smallest = WaveSixRosterPolicy.mageHealth(2);
        double largest = WaveSixRosterPolicy.mageHealth(20);
        check(Math.abs(smallest - WaveSixRosterPolicy.MAGE_BASE_HEALTH) < 1.0e-9,
                "smallest party uses the base profile, got " + smallest);
        check(largest > smallest, "a larger party faces a tougher mage");
        check(largest > WaveSixRosterPolicy.MAGE_BASE_HEALTH * 2.0D,
                "the largest party meaningfully exceeds the base profile");

        // It must not be the generic elite formula and must not borrow the
        // Wave 7 thousand-HP floor.
        check(smallest != EliteCombatStatsPolicy.eliteHealth(20.0D),
                "mage health is its own profile, not the elite formula");

        int previous = 0;
        for (int roster = WaveSixRosterPolicy.MIN_PARTICIPANTS;
                roster <= WaveSixRosterPolicy.MAX_PARTICIPANTS; roster++) {
            int value = (int) WaveSixRosterPolicy.mageHealth(roster);
            check(value >= previous, "mage health is monotonic at roster " + roster);
            previous = value;
        }
    }

    private static void invalidRostersAreRejected() {
        expectReject(() -> WaveSixRosterPolicy.guardsFor(1), "roster below minimum");
        expectReject(() -> WaveSixRosterPolicy.guardsFor(21), "roster above maximum");
        expectReject(() -> WaveSixRosterPolicy.mageHealth(0), "zero roster");
    }

    /** The audit table must cover all five identities exactly once. */
    private static void bindingTableCoversEveryIdentityExactlyOnce() {
        List<WaveSixRosterPolicy.Binding> table = WaveSixRosterPolicy.bindings();
        check(table.size() == MageSignaturePolicy.MAGE_COUNT,
                "binding table lists every identity, got " + table.size());

        for (MageSignaturePolicy.MageId mage : MageSignaturePolicy.MageId.values()) {
            long count = table.stream().filter(binding -> binding.mage() == mage).count();
            check(count == 1, "identity appears exactly once: " + mage);
        }

        // No two mages may claim the same signature, or killing either would
        // hand the sphere an ambiguous spell.
        for (int i = 0; i < table.size(); i++) {
            for (int j = i + 1; j < table.size(); j++) {
                check(table.get(i).signature() != table.get(j).signature(),
                        "signatures are unique across identities");
            }
        }

        for (WaveSixRosterPolicy.Binding binding : table) {
            check(binding.combatRole() == MageSignaturePolicy.roleFor(binding.mage()),
                    "binding keeps the identity's existing combat role");
        }
    }

    private static void expectGuards(int roster, int expected) {
        int actual = WaveSixRosterPolicy.guardsFor(roster);
        check(actual == expected,
                "roster " + roster + " expects " + expected + " guards, got " + actual);
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