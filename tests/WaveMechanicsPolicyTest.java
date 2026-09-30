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
        check(WaveMechanicsPolicy.gateCount() == EndRiftObjective.GATE_COUNT
                        && WaveMechanicsPolicy.gateCount() == 3,
                "Wave 3 must always use exactly three current gates");
        System.out.println("WaveMechanicsPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
