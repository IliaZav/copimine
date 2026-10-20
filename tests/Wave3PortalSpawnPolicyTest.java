import me.copimine.endevent.domain.Wave3PortalSpawnPolicy;

public final class Wave3PortalSpawnPolicyTest {
    public static void main(String[] args) {
        check(Wave3PortalSpawnPolicy.spawnCountWithinBudget(0, 1) == 1,
                "an active empty portal can send one mob on this tick");
        check(Wave3PortalSpawnPolicy.spawnCountWithinBudget(2, 20) == 1,
                "the scheduler emits at most one mob per tick");
        check(Wave3PortalSpawnPolicy.spawnCountWithinBudget(3, 1) == 0,
                "the live portal roster is capped");
        check(Wave3PortalSpawnPolicy.spawnCountWithinBudget(0, 5, 6, 1) == 1,
                "a portal can use one remaining slot in the shared Wave 3 pressure budget");
        check(Wave3PortalSpawnPolicy.spawnCountWithinBudget(0, 6, 6, 1) == 0,
                "portal mobs cannot push total Wave 3 pressure over the party profile");
        check(Wave3PortalSpawnPolicy.spawnCountWithinBudget(3, 5, 6, 1) == 0,
                "portal mobs retain their independent live-roster cap");
        check(Wave3PortalSpawnPolicy.spawnCountWithinBudget(2, 5, 6, 10) == 1,
                "the shared pressure and one-per-tick limits both apply");
        check(Wave3PortalSpawnPolicy.spawnCountWithinBudget(-1, 1) == 0
                        && Wave3PortalSpawnPolicy.spawnCountWithinBudget(0, -1) == 0,
                "invalid counts fail closed");
        check(Wave3PortalSpawnPolicy.spawnCountWithinBudget(0, -1, 6, 1) == 0
                        && Wave3PortalSpawnPolicy.spawnCountWithinBudget(0, 0, -1, 1) == 0,
                "invalid shared-budget counts fail closed");
        System.out.println("Wave3PortalSpawnPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
