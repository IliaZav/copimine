import me.copimine.endevent.domain.BossStatsPolicy;

public final class BossStatsPolicyTest {
    public static void main(String[] args) {
        check(Math.abs(BossStatsPolicy.attackDamage(3.0D, 5.0D) - 8.0D) < 0.0001D,
                "vanilla Enderman 3 plus configured five must equal eight");
        check(Math.abs(BossStatsPolicy.attackDamage(3.0D, -4.0D) - 3.0D) < 0.0001D,
                "negative configured bonus must not reduce base damage");
        check(Math.abs(BossStatsPolicy.attackDamage(Double.NaN, 5.0D) - 5.0D) < 0.0001D,
                "non-finite base must fail closed");
        System.out.println("BossStatsPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
