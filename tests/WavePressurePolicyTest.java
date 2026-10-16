import me.copimine.endevent.domain.PressureBudgetController;
import me.copimine.endevent.domain.WaveMechanicsPolicy;
import me.copimine.endevent.domain.WavePressurePolicy;

public final class WavePressurePolicyTest {
    public static void main(String[] args) {
        var configuredWaveFive = new WaveMechanicsPolicy.WaveCounts(14, 10, 6, 6, 2);
        var twoPlayerWaveFive = WavePressurePolicy.clamp(5, configuredWaveFive, 2);
        check(twoPlayerWaveFive.equals(new WaveMechanicsPolicy.WaveCounts(0, 0, 0, 6, 2)),
                "two-player Wave 5 preserves exactly six elite Endermen and two elite Skeletons");
        check(WavePressurePolicy.isApprovedTwoPlayerException(5, 2, twoPlayerWaveFive),
                "the approved eight-elite composition is explicitly diagnosed as a scoped exception");
        check(twoPlayerWaveFive.total() == 8
                        && twoPlayerWaveFive.total() <= PressureBudgetController.HARD_CAP,
                "the scoped exception remains under the absolute entity hard cap");

        var wrongComposition = WavePressurePolicy.clamp(5,
                new WaveMechanicsPolicy.WaveCounts(5, 0, 0, 9, 4), 2);
        check(wrongComposition.eliteEndermen() == 6 && wrongComposition.eliteSkeletons() == 2
                        && wrongComposition.endermen() == 0,
                "the two-player exception cannot expand elite slots or add ordinary mobs");

        var waveThree = WavePressurePolicy.clamp(3,
                new WaveMechanicsPolicy.WaveCounts(1, 0, 0, 7, 4), 2);
        check(waveThree.equals(WaveMechanicsPolicy.clampToPressure(
                        new WaveMechanicsPolicy.WaveCounts(1, 0, 0, 7, 4), 2))
                        && !WavePressurePolicy.isApprovedTwoPlayerException(3, 2, waveThree),
                "the Wave 5 exception does not become a global exemption");

        var fivePlayers = WavePressurePolicy.clamp(5,
                new WaveMechanicsPolicy.WaveCounts(1, 0, 0, 6, 2), 5);
        check(fivePlayers.total() <= PressureBudgetController.profileForPlayers(5).activePressure(),
                "other Wave 5 party sizes use the regular pressure profile");

        var threePlayerWaveFive = WavePressurePolicy.clamp(5, configuredWaveFive, 3);
        check(threePlayerWaveFive.total()
                        <= PressureBudgetController.profileForPlayers(3).activePressure(),
                "three-player Wave 5 cannot inherit the two-player eight-elite exception");
        check(!WavePressurePolicy.isApprovedTwoPlayerException(5, 3, threePlayerWaveFive),
                "the Wave 5 exception remains exclusive to exactly two players");
        System.out.println("WavePressurePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
