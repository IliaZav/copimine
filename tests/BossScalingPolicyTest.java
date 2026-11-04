import me.copimine.endevent.domain.BossScalingPolicy;

/**
 * The user's boss rule: from ten players health grows proportionally, capped at
 * five thousand, and damage grows too but never hard.
 */
public final class BossScalingPolicyTest {
    private static int checks;

    private BossScalingPolicyTest() {
    }

    public static void main(String[] args) {
        theCapIsTheUsersCeiling();
        healthNeverExceedsTheCap();
        belowTenIsTheFloor();
        healthGrowsWithPlayers();
        damageGrowsGently();
        clampingAndDescription();
        statsAreValidated();
        System.out.println("BossScalingPolicyTest: PASS (" + checks + " checks)");
    }

    private static void theCapIsTheUsersCeiling() {
        // The user's minimum is twenty thousand; the pool grows from there.
        check("the floor is the user's minimum", BossScalingPolicy.HP_FLOOR == 20_000.0D);
        check("the cap is above the floor", BossScalingPolicy.HP_CAP > BossScalingPolicy.HP_FLOOR);
        check("growth starts at ten", BossScalingPolicy.GROWTH_START_PLAYERS == 10);
    }

    private static void healthNeverExceedsTheCap() {
        for (int players = 1; players <= 30; players++) {
            double health = BossScalingPolicy.maxHealthForPlayers(players);
            check("under the cap at " + players, health <= BossScalingPolicy.HP_CAP);
            check("over the floor at " + players, health >= BossScalingPolicy.HP_FLOOR);
        }
        check("twenty is exactly the cap", BossScalingPolicy.maxHealthForPlayers(20) == BossScalingPolicy.HP_CAP);
        for (int players = 1; players <= 30; players++) {
            check("at least twenty thousand at " + players,
                    BossScalingPolicy.maxHealthForPlayers(players) >= 20_000.0D);
        }
        check("a crowd of fifty is still the cap",
                BossScalingPolicy.maxHealthForPlayers(50) == BossScalingPolicy.HP_CAP);
    }

    private static void belowTenIsTheFloor() {
        for (int players = 2; players <= 10; players++) {
            check("floor at " + players,
                    BossScalingPolicy.maxHealthForPlayers(players) == BossScalingPolicy.HP_FLOOR);
            check("no scaling at " + players, !BossScalingPolicy.scalesAboveFloor(players));
            check("no damage bonus at " + players,
                    BossScalingPolicy.damageMultiplierForPlayers(players) == 1.0D);
        }
    }

    private static void healthGrowsWithPlayers() {
        double previous = BossScalingPolicy.maxHealthForPlayers(10);
        for (int players = 11; players <= 20; players++) {
            double health = BossScalingPolicy.maxHealthForPlayers(players);
            check("health never falls at " + players, health >= previous);
            previous = health;
        }
        check("eleven scales", BossScalingPolicy.scalesAboveFloor(11));
        check("the curve is authored per head", BossScalingPolicy.HP_PER_EXTRA_PLAYER > 0.0D);
    }

    private static void damageGrowsGently() {
        double floor = BossScalingPolicy.damageMultiplierForPlayers(10);
        double top = BossScalingPolicy.damageMultiplierForPlayers(20);
        check("a small party takes normal damage", floor == 1.0D);
        check("the biggest party is capped", top <= BossScalingPolicy.DAMAGE_CAP_MULTIPLIER);
        check("the biggest party actually grows", top > 1.0D);
        check("growth is gentle", BossScalingPolicy.DAMAGE_CAP_MULTIPLIER <= 2.0D);
        double previous = floor;
        for (int players = 11; players <= 20; players++) {
            double current = BossScalingPolicy.damageMultiplierForPlayers(players);
            check("damage never falls at " + players, current >= previous);
            previous = current;
        }
        double fifteen = BossScalingPolicy.damageMultiplierForPlayers(15);
        double twenty = BossScalingPolicy.damageMultiplierForPlayers(20);
        check("damage grows with the roster", fifteen < twenty);
        check("half the roster is not at the cap", fifteen < BossScalingPolicy.DAMAGE_CAP_MULTIPLIER);
    }

    private static void clampingAndDescription() {
        check("fifteen players have proportional health", BossScalingPolicy.maxHealthForPlayers(15) == 30_000.0D);
        check("health clamps down", BossScalingPolicy.clampCurrent(9_000.0D, 5_000.0D) == 5_000.0D);
        check("health clamps up to zero", BossScalingPolicy.clampCurrent(-5.0D, 5_000.0D) == 0.0D);
        check("NaN health becomes zero", BossScalingPolicy.clampCurrent(Double.NaN, 5_000.0D) == 0.0D);
        // A corrupted pool must fall back to the authored ceiling, never to a
        // larger value: 100 health survives, an above-cap value is cut to the cap.
        check("a broken max falls back to the cap",
                BossScalingPolicy.clampCurrent(100.0D, -1.0D) == 100.0D);
        check("a broken max cannot authorise more than the cap",
                BossScalingPolicy.clampCurrent(BossScalingPolicy.HP_CAP + 4_000.0D, Double.NaN) == BossScalingPolicy.HP_CAP);
        String described = BossScalingPolicy.describe(15);
        check("description carries the party size", described.contains("players=15"));
        check("description carries the health", described.contains("30000"));
    }

    private static void statsAreValidated() {
        BossScalingPolicy.Stats stats = BossScalingPolicy.Stats.forPlayers(18);
        check("stats carry the party", stats.players() == 18);
        check("stats carry the health", stats.maxHealth() == BossScalingPolicy.maxHealthForPlayers(18));
        check("stats carry the damage",
                stats.damageMultiplier() == BossScalingPolicy.damageMultiplierForPlayers(18));
        boolean rejected = false;
        try {
            new BossScalingPolicy.Stats(5, 0.0D, 1.0D);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check("a zero-health pool is rejected", rejected);
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
    }
}
