import me.copimine.endevent.domain.BlackFogSafeZoneCapacityPolicy;

public final class BlackFogSafeZoneCapacityPolicyTest {
    public static void main(String[] args) {
        check(BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(5, 20, 1) == 20,
                "twenty 1x1 safe zones are required for twenty eligible players");
        check(BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(4, 20, 2) == 5,
                "five 2x2 safe zones provide twenty distinct safe cells");
        check(BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(3, 20, 3) == 3,
                "three 3x3 safe zones provide capacity for twenty eligible players");
        check(BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(6, 3, 3) == 6,
                "capacity expansion must preserve a larger planned zone count");
        check(BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(0, 4, 2) == 1,
                "zone count uses ceiling division when one zone exactly holds four players");

        check(BlackFogSafeZoneCapacityPolicy.hasCapacity(6, 6),
                "every eligible player must have a distinct usable safe cell");
        check(!BlackFogSafeZoneCapacityPolicy.hasCapacity(6, 5),
                "fog must not activate when one eligible player lacks a distinct safe cell");
        check(BlackFogSafeZoneCapacityPolicy.hasCapacity(20, 20),
                "capacity must support the maximum twenty-player roster");
        check(!BlackFogSafeZoneCapacityPolicy.hasCapacity(20, 19),
                "the maximum roster must fail closed when fewer than twenty usable cells exist");

        check(BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(-1, 2, 1) == 0
                        && BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(21, 20, 1) == 0
                        && BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(3, 0, 1) == 0
                        && BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(3, 21, 1) == 0
                        && BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(3, 2, 0) == 0
                        && BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity(3, 2, 4) == 0,
                "invalid player, zone, or footprint bounds must fail closed");
        check(!BlackFogSafeZoneCapacityPolicy.hasCapacity(0, 0)
                        && !BlackFogSafeZoneCapacityPolicy.hasCapacity(-1, 4)
                        && !BlackFogSafeZoneCapacityPolicy.hasCapacity(21, 21)
                        && !BlackFogSafeZoneCapacityPolicy.hasCapacity(2, -1),
                "invalid player or cell counts must not pass the capacity gate");

        System.out.println("BlackFogSafeZoneCapacityPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
