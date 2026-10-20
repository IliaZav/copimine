import me.copimine.endevent.domain.RitualSpherePresentationPolicy;

public final class RitualSpherePresentationPolicyTest {
    public static void main(String[] args) {
        check(Math.abs(RitualSpherePresentationPolicy.centerHeightOffset(0L) - 6.2D) < 1e-8,
                "sphere and captive rise one further block above the previous core+5.2 position");
        check(RitualSpherePresentationPolicy.insidePrisonerAnchor(0.1D, 0.1D, 0.1D),
                "a floating prisoner stays eligible even above the ordinary arena ceiling");
        check(!RitualSpherePresentationPolicy.insidePrisonerAnchor(0.4D, 0.4D, 0.0D)
                && !RitualSpherePresentationPolicy.insidePrisonerAnchor(0.0D, 0.6D, 0.0D)
                && !RitualSpherePresentationPolicy.insidePrisonerAnchor(Double.NaN, 0.0D, 0.0D),
                "the prisoner exception cannot authorize arbitrary positions outside the arena");
        for (long tick = 0L; tick <= 1000L; tick++) {
            double offset = RitualSpherePresentationPolicy.suspensionOffset(tick);
            if (!Double.isFinite(offset) || Math.abs(offset) > 0.120001)
                throw new AssertionError("shell motion must remain inside the prisoner clearance");
        }
        if (Math.abs(RitualSpherePresentationPolicy.suspensionOffset(60L) - 0.12D) > 1e-8
                || Math.abs(RitualSpherePresentationPolicy.suspensionOffset(180L) + 0.12D) > 1e-8
                || RitualSpherePresentationPolicy.suspensionOffset(240L) != 0D)
            throw new AssertionError("continuous periodic floating motion");
        check(RitualSpherePresentationPolicy.channelColor(1, 1) == 0xD04BFF,
                "the full one-guard solo group starts with a ritual violet beam, not a critical gold beam");
        check(RitualSpherePresentationPolicy.channelColor(2, 2) == 0xD04BFF,
                "the two-guard group starts with the same full channel material");
        check(RitualSpherePresentationPolicy.channelColor(1, 3) == 0xA36BFF,
                "a weakened channel stays violet instead of turning gold");
        check(RitualSpherePresentationPolicy.channelColor(0, 1) == 0xEC8CFF,
                "the exposed caster keeps a pale violet channel until awakening");
        check(RitualSpherePresentationPolicy.atSphereAnchor(8.5, 74.2, -38.5, 8.5, 74.2, -38.5),
                "only the real high sphere destination bypasses ordinary vertical containment");
        check(!RitualSpherePresentationPolicy.atSphereAnchor(8.5, 74.5, -38.5, 8.5, 74.2, -38.5)
                && !RitualSpherePresentationPolicy.atSphereAnchor(8.8, 74.2, -38.5, 8.5, 74.2, -38.5)
                && !RitualSpherePresentationPolicy.atSphereAnchor(8.5, Double.NaN, -38.5, 8.5, 74.2, -38.5),
                "height compatibility cannot authorize arbitrary owned teleports");
        System.out.println("RitualSpherePresentationPolicyTest OK");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
