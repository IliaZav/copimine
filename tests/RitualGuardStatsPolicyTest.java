import me.copimine.endevent.domain.RitualGuardStatsPolicy;

public final class RitualGuardStatsPolicyTest {
    public static void main(String[] args) {
        check(RitualGuardStatsPolicy.maximumHealth(40.0D, false) == 60.0D,
                "forty-health enderman guards receive one 50 percent increase");
        check(RitualGuardStatsPolicy.maximumHealth(20.0D, false) == 30.0D,
                "twenty-health guards receive the same bounded one-time tuning");
        check(RitualGuardStatsPolicy.maximumHealth(72.0D, false) == 80.0D,
                "tuning is capped at eighty health");
        check(RitualGuardStatsPolicy.maximumHealth(90.0D, false) == 90.0D,
                "tuning never lowers a pre-existing configured maximum");
        check(RitualGuardStatsPolicy.maximumHealth(50.0D, true) == 50.0D,
                "persisted guard health is not multiplied again after reload");
        System.out.println("RitualGuardStatsPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
