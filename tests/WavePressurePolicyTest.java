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

        var waveOneRequest = new WaveMechanicsPolicy.WaveCounts(12, 8, 0, 0, 0);
        var twoPlayerWaveOne = WavePressurePolicy.clamp(1, waveOneRequest, 2);
        var threePlayerWaveOne = WavePressurePolicy.clamp(1, waveOneRequest, 3);
        check(twoPlayerWaveOne.total() == 10 && threePlayerWaveOne.total() == 10,
                "Wave 1 uses ten simultaneous mobs for two or three players");
        var extremeWaveOne = WavePressurePolicy.clamp(1,
                new WaveMechanicsPolicy.WaveCounts(Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), 2);
        check(extremeWaveOne.total() == 10
                        && extremeWaveOne.total() <= PressureBudgetController.HARD_CAP,
                "Wave 1's scoped cap remains safe for extreme input and below the hard cap");

        var onePlayerWaveOne = WavePressurePolicy.clamp(1, waveOneRequest, 1);
        check(onePlayerWaveOne.total() == 0,
                "wave-scoped pressure cannot bypass the minimum-player pressure profile");

        var fourPlayerWaveOne = WavePressurePolicy.clamp(1, waveOneRequest, 4);
        check(fourPlayerWaveOne.total()
                        <= PressureBudgetController.profileForPlayers(4).activePressure(),
                "the extra two slots apply only to two- and three-player parties");

        var waveSix = WavePressurePolicy.clamp(6, waveOneRequest, 2);
        check(waveSix.total() <= PressureBudgetController.profileForPlayers(2).activePressure(),
                "the Waves 1 and 2 capacity does not broaden pressure for later waves");
        System.out.println("WavePressurePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
