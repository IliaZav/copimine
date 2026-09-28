import me.copimine.endevent.domain.RitualZoneEffectPolicy;

public final class RitualZoneEffectPolicyTest {
    public static void main(String[] args) {
        RitualZoneEffectPolicy.Result active = RitualZoneEffectPolicy.effect(true, true);
        check(active.slowness(), "a player inside Gravity Well receives bounded Slowness");
        check(active.pull(), "a player inside Gravity Well is pulled toward its center");
        check(active.periodicDamage(), "Gravity Well damage is limited to its periodic pulse");

        RitualZoneEffectPolicy.Result betweenPulses = RitualZoneEffectPolicy.effect(true, false);
        check(betweenPulses.slowness() && betweenPulses.pull(),
                "movement effects continue between damage pulses");
        check(!betweenPulses.periodicDamage(), "damage does not occur every server tick");

        RitualZoneEffectPolicy.Result outside = RitualZoneEffectPolicy.effect(false, true);
        check(!outside.slowness() && !outside.pull() && !outside.periodicDamage(),
                "players outside the fixed Gravity Well radius are unaffected");
        check(RitualZoneEffectPolicy.contains(0.0D, 0.0D, 4.0D, 0.0D, 4.0D),
                "the 4 block radius edge is included");
        check(!RitualZoneEffectPolicy.contains(0.0D, 0.0D, 4.01D, 0.0D, 4.0D),
                "targets beyond the 4 block radius are excluded");

        RitualZoneEffectPolicy.Pull pull = RitualZoneEffectPolicy.pull(2.0D, 0.0D, 0.0D, 0.0D);
        check(Math.abs(pull.x() + 0.12D) < 1.0E-9D && Math.abs(pull.z()) < 1.0E-9D,
                "the horizontal pull magnitude is bounded to 0.12 blocks per update");
        RitualZoneEffectPolicy.Pull atCenter = RitualZoneEffectPolicy.pull(0.0D, 0.0D, 0.0D, 0.0D);
        check(atCenter.x() == 0.0D && atCenter.z() == 0.0D,
                "the gravity well center does not generate an invalid pull vector");

        System.out.println("RitualZoneEffectPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
