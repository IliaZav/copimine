import me.copimine.endevent.domain.WaveTwoHuntPolicy;

public final class WaveTwoHuntPolicyTest {
    public static void main(String[] args) {
        twentySecondOwnerRotation();
        packetAssignmentIdentityIsIndependentAndCannotWrap();
        initialAndLateReinforcementsRespectBothBudgets();
        System.out.println("WaveTwoHuntPolicyTest OK");
    }

    private static void twentySecondOwnerRotation() {
        // The 11-November master restores one rotating owner; completed periods
        // remain presentation, never a second group-completion condition.
        check(WaveTwoHuntPolicy.MARK_ROTATION_MILLIS == 20_000L, "twenty-second mark");
        check(WaveTwoHuntPolicy.markEpoch(1_000L, 20_999L)==0L
                && WaveTwoHuntPolicy.markEpoch(1_000L, 21_000L)==1L, "exact persisted boundary");
        check(WaveTwoHuntPolicy.markEpoch(1_000L, 0L)==0L, "clock rewind is bounded");
        for(int count : new int[]{1,2,5,10,11,20}) {
            var roster=new java.util.ArrayList<java.util.UUID>();
            for(int i=0;i<count;i++) roster.add(new java.util.UUID(0,i+1));
            var eligible=new java.util.LinkedHashSet<>(roster);
            for(int group=1;group<=3;group++) for(int epoch=0;epoch<count*2;epoch++) {
                var expected=roster.get((epoch+group-1)%count);
                check(expected.equals(WaveTwoHuntPolicy.markedParticipant(roster,eligible,epoch,group)),
                        "stable fair rotation for every party and group");
            }
            eligible.remove(roster.get(0));
            var survivor=WaveTwoHuntPolicy.markedParticipant(roster,eligible,0,1);
            check(count==1?survivor==null:survivor.equals(roster.get(1)), "invalid owner is skipped");
            check(WaveTwoHuntPolicy.markedParticipant(roster,java.util.Set.of(java.util.UUID.randomUUID()),0,1)==null,
                    "a late foreign participant cannot inherit the mark");
        }
        for (String removed : new String[]{"MIN_MARK_PERIODS",
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