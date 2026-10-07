import me.copimine.endevent.domain.BlackFogDamagePolicy;

public final class BlackFogDamagePolicyTest {
    public static void main(String[] args) {
        check(BlackFogDamagePolicy.damageFor(20.0D) == 1.0D,
                "one fog impact is bounded to half a heart");
        check(BlackFogDamagePolicy.damageFor(2.5D) == 0.5D,
                "damage preserves the protected health floor");
        check(BlackFogDamagePolicy.damageFor(2.0D) == 0.0D,
                "fog cannot deal lethal damage at the health floor");
        check(BlackFogDamagePolicy.damageFor(Double.NaN) == 0.0D,
                "invalid health never creates damage");
        System.out.println("BlackFogDamagePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
