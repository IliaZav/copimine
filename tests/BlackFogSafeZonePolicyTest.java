import me.copimine.endevent.domain.BlackFogSafeZonePolicy;

import java.util.List;

public final class BlackFogSafeZonePolicyTest {
    public static void main(String[] args) {
        check(BlackFogSafeZonePolicy.cells(10, 20, 1).size() == 1,
                "smallest fog safe zone must contain one cell");
        check(BlackFogSafeZonePolicy.cells(10, 20, 2).equals(List.of(
                        new BlackFogSafeZonePolicy.Cell(9, 19),
                        new BlackFogSafeZonePolicy.Cell(9, 20),
                        new BlackFogSafeZonePolicy.Cell(10, 19),
                        new BlackFogSafeZonePolicy.Cell(10, 20))),
                "second fog cycle must mark exactly a 2x2 safe footprint");
        check(BlackFogSafeZonePolicy.cells(10, 20, 3).size() == 9,
                "largest fog safe zone must contain exactly a 3x3 footprint");
        System.out.println("BlackFogSafeZonePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
