import me.copimine.endevent.domain.ArenaInfernoPolicy;

/**
 * Guards the inferno contract: a warning must always precede vent damage, the
 * floor is fully burning (no coordinate-hash safe sectors), and armour wear is
 * capped for the whole cast.
 */
public final class ArenaInfernoPolicyTest {
    private ArenaInfernoPolicyTest() {
    }

    public static void main(String[] args) {
        testVentCadence();
        testWarningPrecedesDamage();
        testVentStride();
        testArmorWearBudget();
        testIgniteCell();
        System.out.println("ArenaInfernoPolicyTest OK");
    }

    private static void testVentCadence() {
        check(ArenaInfernoPolicy.ventPhase(0) == ArenaInfernoPolicy.VentPhase.WARNING, "tick 0 warns");
        check(ArenaInfernoPolicy.ventPhase(19) == ArenaInfernoPolicy.VentPhase.WARNING, "tick 19 warns");
        check(ArenaInfernoPolicy.ventPhase(20) == ArenaInfernoPolicy.VentPhase.DAMAGE, "tick 20 damages");
        check(ArenaInfernoPolicy.ventPhase(29) == ArenaInfernoPolicy.VentPhase.DAMAGE, "tick 29 damages");
        check(ArenaInfernoPolicy.ventPhase(30) == ArenaInfernoPolicy.VentPhase.IDLE, "tick 30 idles");
        check(ArenaInfernoPolicy.ventPhase(59) == ArenaInfernoPolicy.VentPhase.IDLE, "tick 59 idles");
        check(ArenaInfernoPolicy.ventPhase(60) == ArenaInfernoPolicy.VentPhase.WARNING, "cadence wraps");
        check(ArenaInfernoPolicy.ventPhase(-1) == ArenaInfernoPolicy.VentPhase.IDLE, "negative ticks stay bounded");
    }

    private static void testWarningPrecedesDamage() {
        int lastWarning = -1;
        int firstDamage = Integer.MAX_VALUE;
        for (int tick = 0; tick < ArenaInfernoPolicy.VENT_PERIOD_TICKS; tick++) {
            ArenaInfernoPolicy.VentPhase phase = ArenaInfernoPolicy.ventPhase(tick);
            if (phase == ArenaInfernoPolicy.VentPhase.WARNING) {
                lastWarning = Math.max(lastWarning, tick);
            } else if (phase == ArenaInfernoPolicy.VentPhase.DAMAGE) {
                firstDamage = Math.min(firstDamage, tick);
            }
        }
        check(lastWarning >= 0, "every cadence warns");
        check(firstDamage > lastWarning, "damage never starts before the warning finished");
        check(lastWarning + 1 == firstDamage, "damage starts on the first tick after the warning");
    }

    private static void testVentStride() {
        check(ArenaInfernoPolicy.ventStride(0) == 1, "empty floor still strides by one");
        check(ArenaInfernoPolicy.ventStride(2) == 1, "tiny floor still strides by one");
        check(ArenaInfernoPolicy.ventStride(90) == 30, "ninety cells give thirty-cell spacing");
        check(ArenaInfernoPolicy.ventStride(-5) == 1, "invalid cell counts stay safe");
    }

    private static void testArmorWearBudget() {
        check(ArenaInfernoPolicy.armorWearAllowed(0), "wear starts allowed");
        check(ArenaInfernoPolicy.armorWearAllowed(ArenaInfernoPolicy.MAX_ARMOR_WEAR - 1), "last point allowed");
        check(!ArenaInfernoPolicy.armorWearAllowed(ArenaInfernoPolicy.MAX_ARMOR_WEAR), "budget is capped");
        check(!ArenaInfernoPolicy.armorWearAllowed(-1), "negative counters are rejected");
    }

    private static void testIgniteCell() {
        check(ArenaInfernoPolicy.igniteCell(false, true, false, false), "plain solid floor burns");
        check(!ArenaInfernoPolicy.igniteCell(true, true, false, false), "protected cells never burn");
        check(!ArenaInfernoPolicy.igniteCell(false, false, false, false), "air does not burn");
        check(!ArenaInfernoPolicy.igniteCell(false, true, true, false), "liquids do not burn");
        check(!ArenaInfernoPolicy.igniteCell(false, true, false, true), "magma is not re-ignited");
        int burning = 0;
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                if (ArenaInfernoPolicy.igniteCell(false, true, false, false)) {
                    burning++;
                }
            }
        }
        check(burning == 41 * 41, "no coordinate-hash safe sectors remain");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}