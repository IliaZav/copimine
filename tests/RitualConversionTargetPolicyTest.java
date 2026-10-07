import me.copimine.endevent.domain.RitualConversionTargetPolicy;

public final class RitualConversionTargetPolicyTest {
    public static void main(String[] args) {
        check(allowed("WAVE_MOB", 6, true, false, false, false, true),
                "ordinary current-wave event mobs are convertible");
        check(allowed("RITUAL_GUARD", 6, true, false, false, false, true),
                "ordinary Wave 6 guards are valid Turncoat targets");
        check(!allowed("RITUAL_CASTER", 6, true, false, false, false, true),
                "objective casters are never convertible");
        check(!allowed("ELITE", 6, true, false, false, false, true),
                "elites are never convertible");
        check(!allowed("WAVE_MOB", 7, true, false, false, false, true),
                "Wave 7 mobs cannot be removed from their objective");
        check(!allowed("WAVE_MOB", 6, false, false, false, false, false),
                "mobs from another event session are not valid targets");
        check(!allowed("WAVE_MOB", 6, true, true, false, false, true),
                "wave commanders are never convertible");
        check(!allowed("WAVE_MOB", 6, true, false, true, false, true),
                "boss and elite roles are never convertible");
        check(!allowed("WAVE_MOB", 6, true, false, false, true, true),
                "objective entities are never convertible");
        check(!allowed("WAVE_MOB", 6, true, false, false, false, false),
                "a mob cannot be converted twice");
        System.out.println("RitualConversionTargetPolicyTest OK");
    }

    private static boolean allowed(String kind, int wave,
                                   boolean owned, boolean commander,
                                   boolean bossOrElite, boolean objective,
                                   boolean notConverted) {
        return RitualConversionTargetPolicy.canConvert(kind, wave, owned,
                commander, bossOrElite, objective, !notConverted);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
