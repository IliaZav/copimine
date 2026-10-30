import me.copimine.endevent.domain.EliteCombatStatsPolicy;

public final class EliteCombatStatsPolicyTest {
    public static void main(String[] args) {
        var minimumRoster = EliteCombatStatsPolicy.forElite(40.0D, 10.0D, 0.30D,
                0.0D, 0.0D, 0.0D, 2);
        check(minimumRoster.health() == 60.0D && minimumRoster.attack() == 12.5D,
                "minimum-roster elites receive the approved health and attack increases");
        check(minimumRoster.movementSpeed() == 0.336D && minimumRoster.armor() == 3.0D
                        && minimumRoster.armorToughness() == 1.0D
                        && minimumRoster.knockbackResistance() == 0.20D,
                "all elite roles gain bounded speed, armor, toughness, and knockback resistance");

        var maximumRoster = EliteCombatStatsPolicy.forElite(40.0D, 10.0D, 0.30D,
                0.0D, 0.0D, 0.0D, 20);
        check(maximumRoster.health() > minimumRoster.health()
                        && maximumRoster.attack() > minimumRoster.attack(),
                "elite combat stats scale with the event roster");

        var bounded = EliteCombatStatsPolicy.forElite(Double.POSITIVE_INFINITY,
                Double.MAX_VALUE, Double.NaN, Double.MAX_VALUE, 0.0D, Double.MAX_VALUE, 20);
        check(bounded.health() == 0.0D && bounded.attack() == EliteCombatStatsPolicy.MAX_ATTACK_DAMAGE
                        && bounded.movementSpeed() == 0.0D
                        && bounded.armor() == EliteCombatStatsPolicy.MAX_ARMOR
                        && bounded.knockbackResistance() == EliteCombatStatsPolicy.MAX_KNOCKBACK_RESISTANCE,
                "invalid or oversized source attributes cannot exceed the elite safety bounds");
        System.out.println("EliteCombatStatsPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
