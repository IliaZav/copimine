import me.copimine.endevent.domain.V3WaveObjectivePolicy;

public final class V3WaveObjectivePolicyTest {
    public static void main(String[] args) {
        check(V3WaveObjectivePolicy.MAX_WAVE == 7, "V3 must have seven waves");
        check(V3WaveObjectivePolicy.objective(4)
                        == V3WaveObjectivePolicy.Objective.OBELISK_ASSAULT,
                "Wave 4 must be obelisk assault");
        check(V3WaveObjectivePolicy.objective(5)
                        == V3WaveObjectivePolicy.Objective.BLACK_FOG,
                "Wave 5 must be black fog");
        check(V3WaveObjectivePolicy.objective(6)
                        == V3WaveObjectivePolicy.Objective.COLLAPSE_RINGS,
                "Wave 6 must be collapse rings");
        check(V3WaveObjectivePolicy.objective(7)
                        == V3WaveObjectivePolicy.Objective.REALITY_SPLIT,
                "Wave 7 must be reality split");
        check(V3WaveObjectivePolicy.legacyObjectiveSlot(4) == -1,
                "Wave 4 cannot fall through to a legacy objective");
        check(V3WaveObjectivePolicy.legacyObjectiveSlot(7) == 6,
                "Wave 7 must reuse only the chamber adapter slot");
        for (int players = 0; players <= 20; players++) {
            int chambers = V3WaveObjectivePolicy.chamberCount(players);
            check(chambers == 0 || chambers >= 2 && chambers <= 4,
                    "chamber count must be bounded for players=" + players);
        }
        check(V3WaveObjectivePolicy.chamberCount(2) == 2, "2 players need 2 chambers");
        check(V3WaveObjectivePolicy.chamberCount(3) == 3, "3 players need 3 chambers");
        check(V3WaveObjectivePolicy.chamberCount(20) == 4, "20 players need 4 chambers");
        check(V3WaveObjectivePolicy.hasTransitionRunesAfter(6),
                "Wave 6 must hand off through intermission 6");
        check(!V3WaveObjectivePolicy.hasTransitionRunesAfter(7),
                "Wave 7 must not create post-wave transition runes");
        check(V3WaveObjectivePolicy.nextWave(6) == 7, "Wave 6 must lead to Wave 7");
        check(V3WaveObjectivePolicy.nextWave(7) == 0, "Wave 7 has no numbered next wave");
        System.out.println("V3WaveObjectivePolicyTest PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
