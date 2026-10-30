import me.copimine.endevent.domain.PressureBudgetController;
import me.copimine.endevent.domain.WaveMechanicsPolicy;
import me.copimine.endevent.domain.WavePressurePolicy;

public final class WavePressurePolicyTest {
    public static void main(String[] args) {
        var configuredWaveFive = new WaveMechanicsPolicy.WaveCounts(14, 10, 6, 6, 2);
        var twoPlayerWaveFive = WavePressurePolicy.clamp(5, configuredWaveFive, 2);
        check(twoPlayerWaveFive.total() == 16
                        && twoPlayerWaveFive.eliteEndermen() == 6
                        && twoPlayerWaveFive.eliteSkeletons() == 2
                        && twoPlayerWaveFive.endermen() + twoPlayerWaveFive.spiders()
                        + twoPlayerWaveFive.skeletons() == 8,
                "two-player Wave 5 doubles the eight-elite roster with eight bounded ordinary mobs");
        check(WavePressurePolicy.isApprovedTwoPlayerException(5, 2, twoPlayerWaveFive),
                "the doubled two-player roster is explicitly diagnosed as a scoped exception");
        check(twoPlayerWaveFive.total() == 16
                        && twoPlayerWaveFive.total() <= PressureBudgetController.HARD_CAP,
                "the scoped exception remains under the absolute entity hard cap");

        var wrongComposition = WavePressurePolicy.clamp(5,
                new WaveMechanicsPolicy.WaveCounts(5, 0, 0, 9, 4), 2);
        check(wrongComposition.eliteEndermen() == 6 && wrongComposition.eliteSkeletons() == 2
                        && wrongComposition.endermen() == 5 && wrongComposition.total() == 13,
                "the two-player exception bounds elite slots and preserves available ordinary mobs");

        var waveThree = WavePressurePolicy.clamp(3,
                new WaveMechanicsPolicy.WaveCounts(24, 2, 10, 8, 2), 2);
        check(waveThree.total() == 12
                        && !WavePressurePolicy.isApprovedTwoPlayerException(3, 2, waveThree),
                "Wave 3 doubles pressure while staying within its scoped hard bound");

        var fivePlayers = WavePressurePolicy.clamp(5,
                new WaveMechanicsPolicy.WaveCounts(1, 0, 0, 6, 2), 5);
        check(fivePlayers.total() <= WavePressurePolicy.activeCap(5, 5),
                "other Wave 5 party sizes use the bounded doubled pressure profile");

        var threePlayerWaveFive = WavePressurePolicy.clamp(5, configuredWaveFive, 3);
        check(threePlayerWaveFive.total() <= WavePressurePolicy.activeCap(5, 3),
                "three-player Wave 5 uses the doubled party pressure profile");
        check(!WavePressurePolicy.isApprovedTwoPlayerException(5, 3, threePlayerWaveFive),
                "the Wave 5 exception remains exclusive to exactly two players");

        var waveOneRequest = new WaveMechanicsPolicy.WaveCounts(12, 8, 0, 0, 0);
        var twoPlayerWaveOne = WavePressurePolicy.clamp(1, waveOneRequest, 2);
        var threePlayerWaveOne = WavePressurePolicy.clamp(1, waveOneRequest, 3);
        check(twoPlayerWaveOne.total() == waveOneRequest.total()
                        && threePlayerWaveOne.total() == waveOneRequest.total(),
                "Wave 1 allows the doubled roster for two or three players");
        var extremeWaveOne = WavePressurePolicy.clamp(1,
                new WaveMechanicsPolicy.WaveCounts(Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), 2);
        check(extremeWaveOne.total() == 24
                        && extremeWaveOne.total() <= PressureBudgetController.HARD_CAP,
                "Wave 1's doubled scoped cap remains safe for extreme input and below the hard cap");

        var onePlayerWaveOne = WavePressurePolicy.clamp(1, waveOneRequest, 1);
        check(onePlayerWaveOne.total() == 0,
                "wave-scoped pressure cannot bypass the minimum-player pressure profile");

        var fourPlayerWaveOne = WavePressurePolicy.clamp(1, waveOneRequest, 4);
        check(fourPlayerWaveOne.total()
                        <= WavePressurePolicy.activeCap(1, 4),
                "the doubled cap follows larger Wave 1 party profiles");

        var waveSix = WavePressurePolicy.clamp(6, waveOneRequest, 2);
        check(waveSix.total() <= PressureBudgetController.profileForPlayers(2).activePressure(),
                "the Waves 1 and 2 capacity does not broaden pressure for later waves");
        System.out.println("WavePressurePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
