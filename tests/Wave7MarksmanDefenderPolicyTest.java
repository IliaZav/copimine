import me.copimine.endevent.domain.Wave7MarksmanDefenderPolicy;

/**
 * MARK-007 / MARK-009 / W7-003: the Marksman summons exactly one Giant
 * defender around half health, once, behind a durable receipt, and that
 * defender is owned support rather than a second miniboss.
 */
public final class Wave7MarksmanDefenderPolicyTest {
    public static void main(String[] args) {
        summonsOnceAtHalfHealth();
        neverSummonsMoreThanOneOverAFullFight();
        survivesARestartWithoutDuplicating();
        defenderDeathIsNotAStageCompletion();
        System.out.println("Wave7MarksmanDefenderPolicyTest OK");
    }

    private static void summonsOnceAtHalfHealth() {
        check(Wave7MarksmanDefenderPolicy.TRIGGER_FRACTION == 0.5D,
                "the accepted support trigger is around half health");
        double max = 1000.0D;
        check(!Wave7MarksmanDefenderPolicy.shouldSummon(max, max * 0.51D, false),
                "above half health the defender stays away");
        check(Wave7MarksmanDefenderPolicy.shouldSummon(max, max * 0.5D, false),
                "at half health the defender is summoned");
        check(Wave7MarksmanDefenderPolicy.shouldSummon(max, max * 0.2D, false),
                "crossing the threshold between ticks still triggers exactly once");
        check(!Wave7MarksmanDefenderPolicy.shouldSummon(max, max * 0.5D, true),
                "a consumed receipt blocks a second summon");
        // The warning precedes the spawn so the support is readable.
        check(Wave7MarksmanDefenderPolicy.WARNING_TICKS > 0L,
                "the defender is announced before it appears");
    }

    private static void neverSummonsMoreThanOneOverAFullFight() {
        double max = 2500.0D;
        boolean receipt = false;
        int summons = 0;
        for (double health = max; health >= 0.0D; health -= max / 100.0D) {
            if (Wave7MarksmanDefenderPolicy.shouldSummon(max, health, receipt)) {
                summons++;
                receipt = true;
            }
        }
        check(summons == 1, "a full health descent summons exactly one defender");
        check(Wave7MarksmanDefenderPolicy.MAX_DEFENDERS == 1,
                "the contract allows one Giant defender, not a pack of elites");
        // Healing back up must not rearm the receipt.
        check(!Wave7MarksmanDefenderPolicy.shouldSummon(max, max * 0.9D, receipt),
                "healing above the threshold does not rearm the summon");
    }

    private static void survivesARestartWithoutDuplicating() {
        // A receipt written before the actor existed still blocks a duplicate.
        check(Wave7MarksmanDefenderPolicy.shouldSummon(1000.0D, 400.0D, true) == false,
                "a durable receipt survives the restart that re-reads it");
        check(Wave7MarksmanDefenderPolicy.receiptIsDurable(),
                "the receipt is durable, so a crash mid-summon cannot spawn a second Giant");
    }

    private static void defenderDeathIsNotAStageCompletion() {
        check(!Wave7MarksmanDefenderPolicy.defenderDeathCompletesStage(),
                "W7-003: the defender is a child actor and cannot complete the stage");
        check(Wave7MarksmanDefenderPolicy.defenderIsOwnedSupport(),
                "the defender is explicit owned support, not ordinary wave pressure");
        check(!Wave7MarksmanDefenderPolicy.defenderShieldsMiniboss(),
                "the defender neither shields nor buffs the miniboss through a mob aura");
        check(!Wave7MarksmanDefenderPolicy.defenderGrantsReward(),
                "defender removal is not a separate reward");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}