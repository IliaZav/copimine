import me.copimine.endevent.domain.Wave7MinibossScalingPolicy;

/** Miniboss health and damage must grow with the party, and never dip. */
public final class Wave7MinibossScalingPolicyTest {
    private static int checks;

    private Wave7MinibossScalingPolicyTest() {
    }

    public static void main(String[] args) {
        healthGrowsWithTheParty();
        damageGrowsSlowerThanHealth();
        nothingExceedsItsCap();
        badInputIsClampedNotThrown();
        System.out.println("Wave7MinibossScalingPolicyTest: PASS (" + checks + " checks)");
    }

    private static void healthGrowsWithTheParty() {
        double previous = 0.0D;
        check("the authored base is the two-player body",
                Wave7MinibossScalingPolicy.health(1_000.0D, 2) == Wave7MinibossScalingPolicy.HEALTH_FLOOR);
        for (int players = 1; players <= 20; players++) {
            double hp = Wave7MinibossScalingPolicy.health(1_000.0D, players);
            check("hp is at least the floor: " + players, hp >= Wave7MinibossScalingPolicy.HEALTH_FLOOR);
            // The old ladder saturated: ten players fought the same skeleton as
            // two. Growth has to be visible at every step.
            // Growth starts at the third player: the frozen roster never
            // reports fewer than two, and the two-player body is exactly the
            // authored base so the contract floor stays exact.
            if (players > Wave7MinibossScalingPolicy.MIN_PLAYERS) {
                check("hp grows with every player: " + players, hp > previous);
            }
            previous = hp;
        }
        // Two players versus ten must be a different fight.
        double two = Wave7MinibossScalingPolicy.health(1_000.0D, 2);
        double ten = Wave7MinibossScalingPolicy.health(1_000.0D, 10);
        check("a ten-player fight is meaningfully bigger than a two-player one",
                ten >= two * 1.5D);
    }

    private static void damageGrowsSlowerThanHealth() {
        double hpTwo = Wave7MinibossScalingPolicy.health(1_000.0D, 2);
        double hpTen = Wave7MinibossScalingPolicy.health(1_000.0D, 10);
        double dmgTwo = Wave7MinibossScalingPolicy.damageMultiplier(2);
        double dmgTen = Wave7MinibossScalingPolicy.damageMultiplier(10);
        check("damage does grow", dmgTen > dmgTwo);
        check("health outpaces damage, so the fight gets longer not lethal",
                hpTen / hpTwo > dmgTen / dmgTwo);
        double previous = 0.0D;
        for (int players = 1; players <= 20; players++) {
            double multiplier = Wave7MinibossScalingPolicy.damageMultiplier(players);
            check("damage multiplier is monotonic: " + players, multiplier >= previous);
            previous = multiplier;
        }
    }

    private static void nothingExceedsItsCap() {
        for (int players = 1; players <= 40; players++) {
            check("health honours the cap: " + players,
                    Wave7MinibossScalingPolicy.health(1_000.0D, players)
                            <= Wave7MinibossScalingPolicy.HEALTH_CAP);
            check("damage honours its cap: " + players,
                    Wave7MinibossScalingPolicy.damageMultiplier(players)
                            <= Wave7MinibossScalingPolicy.DAMAGE_CAP_MULTIPLIER);
        }
        check("health is rounded to a tenth for the client bar",
                Math.abs(Wave7MinibossScalingPolicy.health(1_000.0D, 7) * 10.0D
                        - Math.round(Wave7MinibossScalingPolicy.health(1_000.0D, 7) * 10.0D)) < 1.0E-9D);
    }

    private static void badInputIsClampedNotThrown() {
        check("zero players is treated as the smallest supported party",
                Wave7MinibossScalingPolicy.health(1_000.0D, 0)
                        == Wave7MinibossScalingPolicy.health(1_000.0D,
                                Wave7MinibossScalingPolicy.MIN_PLAYERS));
        check("negative players is treated as one",
                Wave7MinibossScalingPolicy.damageMultiplier(-5) == 1.0D);
        check("a tiny authored base still meets the floor",
                Wave7MinibossScalingPolicy.health(3.0D, 1) == Wave7MinibossScalingPolicy.HEALTH_FLOOR);
        check("NaN health input falls back to the floor",
                Wave7MinibossScalingPolicy.health(Double.NaN, 1)
                        == Wave7MinibossScalingPolicy.HEALTH_FLOOR);
        check("dead actors stay dead when clamped",
                Wave7MinibossScalingPolicy.clampHealth(0.0D, 2_000.0D) == 0.0D);
        check("health never exceeds max when clamped",
                Wave7MinibossScalingPolicy.clampHealth(9_999.0D, 2_000.0D) == 2_000.0D);
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + name);
    }
}
