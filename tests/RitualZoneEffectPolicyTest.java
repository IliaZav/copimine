import me.copimine.endevent.domain.RitualZoneEffectPolicy;

public final class RitualZoneEffectPolicyTest {
    public static void main(String[] args) {
        RitualZoneEffectPolicy.Result free = RitualZoneEffectPolicy.effect(false, true, false);
        check(free.wither(), "free player inside an active corrupted zone must receive Wither");
        check(free.slowness(), "free player inside an active corrupted zone must receive Slowness");
        check(free.reverseMovement(),
                "free player inside an active corrupted zone must receive reversed movement");

        RitualZoneEffectPolicy.Result swapped = RitualZoneEffectPolicy.effect(false, true, true);
        check(swapped.wither() && swapped.slowness(),
                "control swap does not suppress the zone potion debuffs");
        check(!swapped.reverseMovement(),
                "reverse movement must not stack with A<->B control swap");

        RitualZoneEffectPolicy.Result prisoner = RitualZoneEffectPolicy.effect(true, true, false);
        check(!prisoner.wither() && !prisoner.slowness() && !prisoner.reverseMovement(),
                "captured prisoner must not receive corrupted-zone effects");

        RitualZoneEffectPolicy.Result outside = RitualZoneEffectPolicy.effect(false, false, false);
        check(!outside.wither() && !outside.slowness() && !outside.reverseMovement(),
                "players outside an active corrupted zone must receive no zone effects");

        System.out.println("RitualZoneEffectPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
