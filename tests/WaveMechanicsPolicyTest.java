import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.WaveMechanicsPolicy;

public final class WaveMechanicsPolicyTest {
    public static void main(String[] args) {
        WaveMechanicsPolicy.WaveCounts requested =
                new WaveMechanicsPolicy.WaveCounts(40, 20, 10, 8, 6);
        WaveMechanicsPolicy.WaveCounts capped =
                WaveMechanicsPolicy.clampToPressure(requested, 20);
        check(capped.total() <= 30,
                "the largest current pressure profile must remain bounded");
        check(capped.eliteEndermen() == 8 && capped.eliteSkeletons() == 6,
                "elite entries must be preserved while ordinary entries are trimmed");
        check(capped.endermen() >= 0 && capped.spiders() >= 0 && capped.skeletons() >= 0,
                "clamping must never create negative ordinary counts");

        WaveMechanicsPolicy.WaveCounts empty =
                WaveMechanicsPolicy.clampToPressure(null, 20);
        check(empty.total() == 0, "null composition must fail closed");
        var intro = WaveMechanicsPolicy.compositionForWave(1, requested);
        check(intro.skeletons() == 0 && intro.eliteSkeletons() == 0,
                "the canonical introduction cannot create projectile mobs from stale config");
        check(intro.total() == requested.total() && intro.endermen() == 48
                        && intro.spiders() == 36 && intro.eliteEndermen() == 0,
                "intro replacement converts stale ranged slots to ordinary melee mobs");
        check(intro.eliteEndermen() == 0 && intro.eliteSkeletons() == 0,
                "Wave 1 must never create generic elite roles");
        var waveTwo = WaveMechanicsPolicy.compositionForWave(2, requested);
        check(waveTwo.eliteEndermen() == 0 && waveTwo.eliteSkeletons() == 0
                        && waveTwo.endermen() == 48 && waveTwo.skeletons() == 16,
                "Wave 2 stale elite slots become ordinary roles without adding a new spell pool");
        for (int wave = 3; wave <= 6; wave++) {
            check(WaveMechanicsPolicy.compositionForWave(wave, requested).equals(requested),
                    "composition adaptation must not change later waves");
        }
        var realitySplit = WaveMechanicsPolicy.compositionForWave(7, requested);
        check(realitySplit.endermen() == requested.endermen()
                        && realitySplit.spiders() == requested.spiders()
                        && realitySplit.skeletons() == requested.skeletons(),
                "Wave 7 must preserve ordinary trial composition while applying elite policy");
        check(realitySplit.eliteEndermen() == 0 && realitySplit.eliteSkeletons() == 0,
                "Wave 7 generic composition must never admit elite actors");
        check(WaveMechanicsPolicy.gateCount() == EndRiftObjective.GATE_COUNT
                        && WaveMechanicsPolicy.gateCount() == 3,
                "Wave 3 must always use exactly three current gates");
        System.out.println("WaveMechanicsPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
