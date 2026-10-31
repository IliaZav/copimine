import me.copimine.endevent.domain.WaveTwoHuntPolicy;

public final class WaveTwoHuntPolicyTest {
    public static void main(String[] args) {
        noRotatingTargetSurvives();
        packetAssignmentIdentityIsIndependentAndCannotWrap();
        initialAndLateReinforcementsRespectBothBudgets();
        System.out.println("WaveTwoHuntPolicyTest OK");
    }

    private static void noRotatingTargetSurvives() {
        // Superseded: a rotating forty-five second mark with a minimum number of
        // completed periods. The whole party is marked for the whole wave, so
        // none of that API may survive in the policy.
        for (String removed : new String[]{"MARK_ROTATION_MILLIS", "MIN_MARK_PERIODS",
                "MAX_COMPLETED_MARK_PERIODS", "markDeadline", "markExpired",
                "nextMarkTarget", "initialMarkAssignments", "mayCompleteBatch"}) {
            boolean declared;
            try {
                WaveTwoHuntPolicy.class.getDeclaredField(removed);
                declared = true;
            } catch (NoSuchFieldException absent) {
                declared = false;
            }
            check(!declared, "Wave 2 no longer exposes the rotating mark constant " + removed);
        }
    }

    private static void initialAndLateReinforcementsRespectBothBudgets() {
        check(WaveTwoHuntPolicy.initialReinforcementCount(8, 6, 5) == 5,
                "initial reinforcement respects the remaining total reserve");
        check(WaveTwoHuntPolicy.initialReinforcementCount(8, 3, 20) == 3,
                "initial reinforcement respects active pressure slots");
        check(WaveTwoHuntPolicy.initialReinforcementCount(-1, 3, 20) == 0
                        && WaveTwoHuntPolicy.initialReinforcementCount(5, -1, 20) == 0
                        && WaveTwoHuntPolicy.initialReinforcementCount(5, 3, -1) == 0,
                "negative initial inputs fail closed to zero");

        check(WaveTwoHuntPolicy.lateReinforcementCount(20, 20) == 2,
                "late reinforcement never exceeds its two-mob ceiling");
        check(WaveTwoHuntPolicy.lateReinforcementCount(1, 20) == 1,
                "late reinforcement respects a single free pressure slot");
        check(WaveTwoHuntPolicy.lateReinforcementCount(20, 1) == 1,
                "late reinforcement respects a one-mob remaining reserve");
        check(WaveTwoHuntPolicy.lateReinforcementCount(0, 10) == 0
                        && WaveTwoHuntPolicy.lateReinforcementCount(10, 0) == 0
                        && WaveTwoHuntPolicy.lateReinforcementCount(-1, 10) == 0,
                "late reinforcement fails closed when either budget is exhausted");

        int initial = WaveTwoHuntPolicy.initialReinforcementCount(4, 8, 5);
        int remaining = 5 - initial;
        int late = WaveTwoHuntPolicy.lateReinforcementCount(8, remaining);
        check(initial == 4 && late == 1 && initial + late <= 5,
                "initial and late additions together never exceed the supplied total budget");
    }

    private static void packetAssignmentIdentityIsIndependentAndCannotWrap() {
        int ceiling = WaveTwoHuntPolicy.MAX_MARK_ASSIGNMENT_ID;
        check(WaveTwoHuntPolicy.nextMarkAssignmentId(0) == 1
                        && WaveTwoHuntPolicy.nextMarkAssignmentId(ceiling - 1) == ceiling,
                "assignment IDs advance monotonically through the final representable identity");
        check(!WaveTwoHuntPolicy.markAssignmentIdsExhausted(ceiling - 1)
                        && WaveTwoHuntPolicy.markAssignmentIdsExhausted(ceiling),
                "exhaustion is detected before integer wrap");
        expectFailure(() -> WaveTwoHuntPolicy.nextMarkAssignmentId(ceiling),
                "exhausted IDs cannot wrap and weaken stale-packet protection");
        expectFailure(() -> WaveTwoHuntPolicy.markAssignmentIdsExhausted(-1),
                "negative packet identities are rejected");

        check(WaveTwoHuntPolicy.restoreMarkAssignmentId("1000010") == 1_000_010,
                "near-boundary interrupted assignments survive snapshot restoration");
        check(WaveTwoHuntPolicy.restoreMarkAssignmentId(null) == 0,
                "a snapshot without packet identity starts a fresh wave-scoped identity");
        check(WaveTwoHuntPolicy.restoreMarkAssignmentId("0") == 0,
                "a recorded zero identity restores unchanged");
        expectFailure(() -> WaveTwoHuntPolicy.restoreMarkAssignmentId("-3"),
                "negative checkpoint identities are rejected");
        expectFailure(() -> WaveTwoHuntPolicy.restoreMarkAssignmentId("2147483648"),
                "overflowing checkpoint identities are rejected");
        expectFailure(() -> WaveTwoHuntPolicy.restoreMarkAssignmentId("not-a-number"),
                "malformed checkpoint identities are rejected");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected bounded failure.
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}