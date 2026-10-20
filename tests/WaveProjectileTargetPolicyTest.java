import me.copimine.endevent.domain.WaveProjectileTargetPolicy;

public final class WaveProjectileTargetPolicyTest {
    public static void main(String[] args) {
        check(WaveProjectileTargetPolicy.allowsPlayerAoe(true, true, true, true, true),
                "a visible eligible player inside the same-world radius is admitted");
        check(!WaveProjectileTargetPolicy.allowsPlayerAoe(true, true, true, true, false),
                "a solid wall between the AoE source and player prevents damage admission");
        check(!WaveProjectileTargetPolicy.allowsPlayerAoe(false, true, true, true, true),
                "an owned mob is never admitted as a player-targeted spell victim");
        check(!WaveProjectileTargetPolicy.allowsPlayerAoe(true, false, true, true, true),
                "an ineligible encounter owner cannot damage a player");
        check(!WaveProjectileTargetPolicy.allowsPlayerAoe(true, true, false, true, true),
                "cross-world targets are rejected");
        check(!WaveProjectileTargetPolicy.allowsPlayerAoe(true, true, true, false, true),
                "targets outside the named AoE radius are rejected");
        System.out.println("WaveProjectileTargetPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
