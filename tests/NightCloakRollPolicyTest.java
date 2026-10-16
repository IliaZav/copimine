import me.copimine.endevent.domain.NightCloakRollPolicy;

public final class NightCloakRollPolicyTest {
    public static void main(String[] args) {
        check(NightCloakRollPolicy.resolve("WON", 0.30D, 1L).equals("WON"),
                "a persisted winning roll must never reroll");
        check(NightCloakRollPolicy.resolve("NOT_WON", 0.30D, 1L).equals("NOT_WON"),
                "a persisted losing roll must never reroll");
        String first = NightCloakRollPolicy.resolve("", 0.30D, 918273645L);
        String second = NightCloakRollPolicy.resolve("", 0.30D, 918273645L);
        check(first.equals(second), "new roll must be deterministic for its durable seed");
        check(NightCloakRollPolicy.resolve("", 0.0D, 1L).equals("NOT_WON"),
                "zero chance must never win");
        check(NightCloakRollPolicy.resolve("", 1.0D, 1L).equals("WON"),
                "full chance must always win");
        check(NightCloakRollPolicy.isWon("WON"), "WON must be recognized");
        check(NightCloakRollPolicy.isWon("DELIVERED"), "delivered cloak keeps won outcome");
        check(!NightCloakRollPolicy.isWon("NOT_WON"), "NOT_WON must not be recognized as won");
        System.out.println("NightCloakRollPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
