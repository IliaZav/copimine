import me.copimine.endevent.domain.Wave7MarksmanSquadPolicy;

/**
 * W7-02: the Mobile Marksman trial fields a squad of two or three archers.
 *
 * <p>The player report was "there is only one skeleton, there should be two or
 * three". The trial spawned exactly one actor and the pair logic then searched
 * for a partner that had never been created, so every pairing call returned
 * null and the "duo" was one solo archer with decorative team code around it.</p>
 */
public final class Wave7MarksmanSquadPolicyTest {
    private static int checks;

    private Wave7MarksmanSquadPolicyTest() {
    }

    public static void main(String[] args) {
        smallPartiesStillFieldTwo();
        largePartiesFieldThree();
        squadNeverExceedsTheReadableCeiling();
        partnerIndicesDoNotAliasThePrimary();
        trialCompletesOnlyWhenTheWholeSquadIsDown();
        squadSizeIsStableForARosterSize();
        System.out.println("Wave7MarksmanSquadPolicyTest: PASS (" + checks + " checks)");
    }

    private static void smallPartiesStillFieldTwo() {
        check("a solo party still meets a real pair",
                Wave7MarksmanSquadPolicy.squadSize(1) == Wave7MarksmanSquadPolicy.MIN_SQUAD);
        check("a full small party meets exactly two",
                Wave7MarksmanSquadPolicy.squadSize(
                        Wave7MarksmanSquadPolicy.THIRD_MARKSMAN_PLAYERS - 1)
                        == Wave7MarksmanSquadPolicy.MIN_SQUAD);
        check("an empty roster never fields a negative squad",
                Wave7MarksmanSquadPolicy.squadSize(0) == Wave7MarksmanSquadPolicy.MIN_SQUAD);
        check("a negative roster size is clamped, not trusted",
                Wave7MarksmanSquadPolicy.squadSize(-4) == Wave7MarksmanSquadPolicy.MIN_SQUAD);
    }

    private static void largePartiesFieldThree() {
        check("a wide party meets the third archer",
                Wave7MarksmanSquadPolicy.squadSize(
                        Wave7MarksmanSquadPolicy.THIRD_MARKSMAN_PLAYERS)
                        == Wave7MarksmanSquadPolicy.MAX_SQUAD);
        check("a very large party does not add a fourth",
                Wave7MarksmanSquadPolicy.squadSize(40) == Wave7MarksmanSquadPolicy.MAX_SQUAD);
    }

    private static void squadNeverExceedsTheReadableCeiling() {
        for (int players = 0; players <= 40; players++) {
            int size = Wave7MarksmanSquadPolicy.squadSize(players);
            if (size < Wave7MarksmanSquadPolicy.MIN_SQUAD
                    || size > Wave7MarksmanSquadPolicy.MAX_SQUAD) {
                throw new AssertionError("squad size out of bounds for " + players + ": " + size);
            }
        }
        checks++;
    }

    private static void partnerIndicesDoNotAliasThePrimary() {
        // The primary keeps -1 so every persisted checkpoint written before this
        // change still resolves it.
        for (int size = Wave7MarksmanSquadPolicy.MIN_SQUAD;
                size <= Wave7MarksmanSquadPolicy.MAX_SQUAD; size++) {
            var indices = Wave7MarksmanSquadPolicy.memberIndices(size);
            var partners = Wave7MarksmanSquadPolicy.partnerIndices(size);
            check("member indices cover the whole squad", indices.size() == size);
            check("partner indices exclude the primary", partners.size() == size - 1);
            check("the primary is not among the partner indices", !partners.contains(-1));
            for (int partner : partners) {
                check("a partner index is recognised as one",
                        Wave7MarksmanSquadPolicy.isPartnerIndex(partner));
            }
        }
        check("the primary index is not a partner index",
                !Wave7MarksmanSquadPolicy.isPartnerIndex(-1));
        check("an out-of-range index is not a partner index",
                !Wave7MarksmanSquadPolicy.isPartnerIndex(Wave7MarksmanSquadPolicy.MAX_SQUAD));
    }

    private static void trialCompletesOnlyWhenTheWholeSquadIsDown() {
        int size = Wave7MarksmanSquadPolicy.MAX_SQUAD;
        check("one archer down does not clear the trial",
                !Wave7MarksmanSquadPolicy.completesTrial(size, size - 1));
        check("two archers down still do not clear a three-archer trial",
                !Wave7MarksmanSquadPolicy.completesTrial(size, 1));
        check("the last archer completes the trial",
                Wave7MarksmanSquadPolicy.completesTrial(size, 0));
        check("a negative live count is treated as cleared",
                Wave7MarksmanSquadPolicy.completesTrial(size, -3));
    }

    private static void squadSizeIsStableForARosterSize() {
        for (int players = 0; players <= 20; players++) {
            int first = Wave7MarksmanSquadPolicy.squadSize(players);
            int second = Wave7MarksmanSquadPolicy.squadSize(players);
            if (first != second) {
                throw new AssertionError("squad size is not stable for " + players);
            }
        }
        checks++;
    }

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError("FAILED: " + name);
        checks++;
    }
}