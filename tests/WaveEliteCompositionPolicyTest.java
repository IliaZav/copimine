import me.copimine.endevent.domain.WaveEliteCompositionPolicy;

public final class WaveEliteCompositionPolicyTest {
    public static void main(String[] args) {
        check(WaveEliteCompositionPolicy.isObsolete(2, 7, "ELITE", "ELITE", "WAVE_GUARDIAN", false),
                "unmarked or earlier-group Wave 2 elites are removed");
        check(!WaveEliteCompositionPolicy.isObsolete(2, 7, "ELITE", "ELITE", "WAVE_GUARDIAN", false,
                        true),
                "the explicitly marked group-3 Wave 2 skeleton elite survives recovery");
        check(!WaveEliteCompositionPolicy.isObsolete(2, 7, "WAVE_GUARDIAN", "ELITE", "WAVE_GUARDIAN", true),
                "Wave 2 guardian roles are not mistaken for generic elites");
        check(WaveEliteCompositionPolicy.isObsolete(7, 7, "ELITE", "ELITE", "WAVE_GUARDIAN", false),
                "Wave 7 explicit generic elite tags are removed");
        check(WaveEliteCompositionPolicy.isObsolete(7, 7, "WAVE_GUARDIAN", "ELITE", "WAVE_GUARDIAN", true),
                "a marked legacy Wave 7 elite guardian is removed");
        check(!WaveEliteCompositionPolicy.isObsolete(7, 7, "WAVE_GUARDIAN", "ELITE", "WAVE_GUARDIAN", false),
                "ordinary Wave 7 guardians survive recovery");
        check(!WaveEliteCompositionPolicy.isObsolete(6, 7, "ELITE", "ELITE", "WAVE_GUARDIAN", true),
                "Wave 6 keeps its approved elite roles");
        System.out.println("WaveEliteCompositionPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
