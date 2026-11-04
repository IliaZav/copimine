import me.copimine.endevent.domain.EliteCombatStatsPolicy;

public final class EliteCombatStatsPolicyTest {
    public static void main(String[] args) {
        // Contract: elite max HP is exactly 3x the ordinary role's max HP under
        // the same frozen party scaling, with no hidden absolute cap.
        var minimumRoster = EliteCombatStatsPolicy.forElite(40.0D, 10.0D, 0.30D,
                0.0D, 0.0D, 0.0D, 2);
        check(minimumRoster.health() == 120.0D,
                "minimum-roster elites receive exactly three times ordinary health");
        check(minimumRoster.attack() == 17.5D,
                "minimum-roster elites receive the approved attack increase");
        check(minimumRoster.movementSpeed() == 0.336D && minimumRoster.armor() == 3.0D
                        && minimumRoster.armorToughness() == 1.0D
                        && minimumRoster.knockbackResistance() == 0.20D,
                "all elite roles gain bounded speed, armor, toughness, and knockback resistance");

        // The ordinary baseline already carries roster scaling, so the elite
        // ratio must hold at every roster size rather than scale again.
        var maximumRoster = EliteCombatStatsPolicy.forElite(40.0D, 10.0D, 0.30D,
                0.0D, 0.0D, 0.0D, 20);
        check(maximumRoster.health() == EliteCombatStatsPolicy.eliteHealth(40.0D),
                "elite health is the exact ratio at every roster size");
        check(maximumRoster.attack() > minimumRoster.attack(),
                "non-health elite attack still scales with the event roster");

        // A large but legitimate ordinary baseline must not be clipped, because
        // clipping would silently break the three-times contract.
        check(EliteCombatStatsPolicy.eliteHealth(1_000.0D) == 3_000.0D,
                "no hidden health cap may truncate the elite ratio");

        // Applying the ratio to an already-elite value must be caller-visible and
        // explicit, never an accidental compounding path: callers pass the
        // ordinary maximum, so the helper is a pure function of that input.
        check(EliteCombatStatsPolicy.eliteHealth(EliteCombatStatsPolicy.eliteHealth(10.0D)) == 90.0D,
                "double application is an explicit 9x, never a silent restore bug");

        var bounded = EliteCombatStatsPolicy.forElite(Double.POSITIVE_INFINITY,
                Double.MAX_VALUE, Double.NaN, Double.MAX_VALUE, 0.0D, Double.MAX_VALUE, 20);
        check(bounded.health() == 0.0D && bounded.attack() == EliteCombatStatsPolicy.MAX_ATTACK_DAMAGE
                        && bounded.movementSpeed() == 0.0D
                        && bounded.armor() == EliteCombatStatsPolicy.MAX_ARMOR
                        && bounded.knockbackResistance() == EliteCombatStatsPolicy.MAX_KNOCKBACK_RESISTANCE,
                "invalid or oversized source attributes cannot exceed the non-health elite safety bounds");
        System.out.println("EliteCombatStatsPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}