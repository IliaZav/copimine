import me.copimine.endevent.domain.WaveDamagePolicy;

public final class WaveDamagePolicyTest {
    public static void main(String[] args) {
        check(close(WaveDamagePolicy.reduce(8.0D, 4.0D), 4.0D),
                "wave damage must be reduced by four");
        check(close(WaveDamagePolicy.reduce(4.0D, 4.0D), 0.0D),
                "reduction must clamp at zero");
        check(close(WaveDamagePolicy.reduce(-2.0D, 4.0D), 0.0D),
                "invalid base damage must clamp at zero");
        check(close(WaveDamagePolicy.minimumCombatDamage(4.0D, 4.0D), 1.0D),
                "a wave attack must retain a one-damage floor");
        check(close(WaveDamagePolicy.minimumCombatDamage(12.0D, 4.0D), 8.0D),
                "the one-damage floor must not change larger attacks");
        check(close(WaveDamagePolicy.minimumCombatDamage(Double.NaN, 4.0D), 1.0D),
                "non-finite wave damage must fail closed to one");
        System.out.println("WaveDamagePolicyTest OK");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000001D;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
