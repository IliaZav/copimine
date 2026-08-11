import me.copimine.artifacts.RepairKitMath;

public final class RepairKitMathTest {
    public static void main(String[] args) {
        testRepairsExactlyOneQuarterOfMaximum();
        testRepairDamageIsClampedAtZero();
        testFirstSuccessfulRepairLeavesFourUses();
        testSecondSuccessfulRepairLeavesThreeUses();
        testThirdSuccessfulRepairLeavesTwoUses();
        testFourthSuccessfulRepairLeavesOneUse();
        testFifthSuccessfulRepairExhaustsKit();
        testInvalidTargetsCannotConsumeUse();
        testVisibleDurabilityTracksFiveStates();
        System.out.println("RepairKitMathTest OK");
    }

    private static void testRepairsExactlyOneQuarterOfMaximum() {
        check(RepairKitMath.repairedDamage(750, 1000) == 500,
            "1000 max durability must restore exactly 250 points");
        check(RepairKitMath.repairedDamage(1000, 1000) == 750,
            "a fully damaged item must restore one quarter of its maximum");
    }

    private static void testRepairDamageIsClampedAtZero() {
        check(RepairKitMath.repairedDamage(100, 1000) == 0,
            "repair must not produce negative damage");
        check(RepairKitMath.repairedDamage(0, 1000) == 0,
            "a whole item must remain whole");
        check(RepairKitMath.repairedDamage(100, 0) == 100,
            "invalid maximum durability must not mutate damage");
    }

    private static void testFirstSuccessfulRepairLeavesFourUses() {
        check(RepairKitMath.remainingUsesAfterSuccess(5) == 4,
            "first successful repair must leave four uses");
    }

    private static void testSecondSuccessfulRepairLeavesThreeUses() {
        check(RepairKitMath.remainingUsesAfterSuccess(4) == 3,
            "second successful repair must leave three uses");
    }

    private static void testThirdSuccessfulRepairLeavesTwoUses() {
        check(RepairKitMath.remainingUsesAfterSuccess(3) == 2,
            "third successful repair must leave two uses");
    }

    private static void testFourthSuccessfulRepairLeavesOneUse() {
        check(RepairKitMath.remainingUsesAfterSuccess(2) == 1,
            "fourth successful repair must leave one use");
    }

    private static void testFifthSuccessfulRepairExhaustsKit() {
        check(RepairKitMath.remainingUsesAfterSuccess(1) == 0,
            "fifth successful repair must exhaust the kit");
        check(RepairKitMath.remainingUsesAfterSuccess(0) == 0,
            "an exhausted kit must stay exhausted");
    }

    private static void testInvalidTargetsCannotConsumeUse() {
        check(!RepairKitMath.canRepair(0, 1000, 5, false, false, false),
            "a whole target must not consume a use");
        check(!RepairKitMath.canRepair(100, 0, 5, false, false, false),
            "a non-damageable target must not consume a use");
        check(!RepairKitMath.canRepair(100, 1000, 5, true, false, false),
            "an official target must not consume a use");
        check(!RepairKitMath.canRepair(100, 1000, 5, false, true, false),
            "another repair kit must not consume a use");
        check(!RepairKitMath.canRepair(100, 1000, 5, false, false, true),
            "a cancelled event must not consume a use");
        check(!RepairKitMath.canRepair(100, 1000, 0, false, false, false),
            "an exhausted kit must not consume a use");
    }

    private static void testVisibleDurabilityTracksFiveStates() {
        check(RepairKitMath.damageForRemainingUses(5, 5) == 0,
            "new kit must have an empty durability bar");
        check(RepairKitMath.damageForRemainingUses(5, 4) == 1,
            "four-use kit must show wear");
        check(RepairKitMath.damageForRemainingUses(5, 1) > RepairKitMath.damageForRemainingUses(5, 4),
            "fewer uses must show more wear");
        check(RepairKitMath.damageForRemainingUses(5, 0) == 5,
            "zero-use state must map to full five-point damage");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
