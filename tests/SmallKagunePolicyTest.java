import me.copimine.endevent.domain.SmallKagunePolicy;

/**
 * The LAST_SEAL small Kagune must stay a bounded melee summon: a finite
 * budget, a finite lifetime and a real, non-negative damage model.
 */
public final class SmallKagunePolicyTest {

    public static void main(String[] args) {
        budgetScalesAndStaysBounded();
        lifetimeExpiresOnlyAtTheDeadline();
        strikeRangeIsInclusiveAndFinite();
        System.out.println("SmallKagunePolicyTest OK");
    }

    private static void budgetScalesAndStaysBounded() {
        check(SmallKagunePolicy.budget(0, 4, 0) == 0, "an empty roster cannot summon an attacker");
        check(SmallKagunePolicy.budget(2, 4, 0) == 1 && SmallKagunePolicy.budget(5, 4, 0) == 1,
                "a small roster stays at one small Kagune");
        check(SmallKagunePolicy.budget(10, 4, 0) == 2 && SmallKagunePolicy.budget(20, 4, 0) == 4,
                "a full roster reaches the tuned cap");
        check(SmallKagunePolicy.budget(20, 2, 1) == 1, "existing summons count against the configured role cap");
        check(SmallKagunePolicy.budget(20, 2, 2) == 0, "a full role budget refuses another summon");
        int previous = 0;
        for (int roster = 0; roster <= 20; roster++) {
            int budget = SmallKagunePolicy.budget(roster, 4, 0);
            check(budget >= 0 && budget <= 4,
                    "the budget must stay inside the concurrency cap");
            check(budget >= previous, "the budget must never shrink as the roster grows");
            previous = budget;
        }
        check(SmallKagunePolicy.budget(500, 4, 0) == 4,
                "an oversized roster cannot exceed the cap");
    }

    private static void lifetimeExpiresOnlyAtTheDeadline() {
        check(!SmallKagunePolicy.expired(0L, 0L), "no deadline means no expiry");
        check(!SmallKagunePolicy.expired(599L, 600L),
                "the summon is still alive one tick before its deadline");
        check(SmallKagunePolicy.expired(600L, 600L),
                "the summon retracts exactly at its deadline");
        check(SmallKagunePolicy.expired(Long.MAX_VALUE, 1L),
                "a stale deadline cannot keep a summon alive");
    }

    private static void strikeRangeIsInclusiveAndFinite() {
        check(SmallKagunePolicy.inStrikeRange(0.0D, 3.0D), "a target on top of the summon is hittable");
        check(SmallKagunePolicy.inStrikeRange(9.0D, 3.0D),
                "the configured three-block boundary is inclusive");
        check(!SmallKagunePolicy.inStrikeRange(9.0001D, 3.0D),
                "a target outside the boundary is a miss");
        check(!SmallKagunePolicy.inStrikeRange(-1.0D, 3.0D),
                "an invalid distance can never count as a hit");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
