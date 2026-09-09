import me.copimine.artifacts.NightCloakPolicy;

public final class NightCloakPolicyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(NightCloakPolicy.isNight(13000L, "NORMAL"), "night lower bound");
        check(NightCloakPolicy.isNight(22999L, "NORMAL"), "night upper bound");
        check(!NightCloakPolicy.isNight(23000L, "NORMAL"), "day boundary");
        check(!NightCloakPolicy.isNight(13000L, "NETHER"), "nether disabled");
        check(!NightCloakPolicy.isNight(13000L, "THE_END"), "end disabled");
        check(NightCloakPolicy.isBelowTenPercent(0.9D, 10.0D), "strictly below ten percent");
        check(!NightCloakPolicy.isBelowTenPercent(1.0D, 10.0D), "ten percent is not below");
        check(NightCloakPolicy.COOLDOWN_SECONDS == 420, "berserker cooldown");
        System.out.println("NightCloakPolicyTest OK");
    }
}
