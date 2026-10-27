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
        testVentCountScaling();
        testArmorWearBudget();
        testIgniteCell();
        System.out.println("ArenaInfernoPolicyTest OK");
    }

    private static void testVentCadence() {
        check(ArenaInfernoPolicy.ventPhase(0) == ArenaInfernoPolicy.VentPhase.WARN, "tick 0 warns");
        check(ArenaInfernoPolicy.ventPhase(15) == ArenaInfernoPolicy.VentPhase.WARN, "the last warning tick warns");
        check(ArenaInfernoPolicy.ventPhase(16) == ArenaInfernoPolicy.VentPhase.ERUPT, "tick 16 erupts");
        check(ArenaInfernoPolicy.ventPhase(17) == ArenaInfernoPolicy.VentPhase.RECOVER, "tick 17 recovers");
        check(ArenaInfernoPolicy.ventPhase(26) == ArenaInfernoPolicy.VentPhase.RECOVER, "the last recovery tick recovers");
        check(ArenaInfernoPolicy.ventPhase(27) == ArenaInfernoPolicy.VentPhase.REMOVE, "tick 27 is removed");
        check(ArenaInfernoPolicy.ventPhase(59) == ArenaInfernoPolicy.VentPhase.REMOVE, "tick 59 is removed");
        check(ArenaInfernoPolicy.ventPhase(60) == ArenaInfernoPolicy.VentPhase.WARN, "cadence wraps");
        check(ArenaInfernoPolicy.ventPhase(-1) == ArenaInfernoPolicy.VentPhase.REMOVE, "negative ticks stay bounded");
    }

    private static void testWarningPrecedesDamage() {
        int lastWarning = -1;
        int firstEruption = Integer.MAX_VALUE;
        int eruptions = 0;
        for (int tick = 0; tick < ArenaInfernoPolicy.VENT_PERIOD_TICKS; tick++) {
            ArenaInfernoPolicy.VentPhase phase = ArenaInfernoPolicy.ventPhase(tick);
            if (phase == ArenaInfernoPolicy.VentPhase.WARN) {
                lastWarning = Math.max(lastWarning, tick);
            } else if (ArenaInfernoPolicy.ventDamages(phase)) {
                firstEruption = Math.min(firstEruption, tick);
                eruptions++;
            }
        }
        check(lastWarning >= 0, "every cadence warns");
        check(firstEruption > lastWarning, "damage never starts before the warning finished");
        check(lastWarning + 1 == firstEruption, "eruption happens on the first tick after the warning");
        check(eruptions == 1, "a vent erupts exactly once per cadence, never on every warning tick");
        check(!ArenaInfernoPolicy.ventDamages(ArenaInfernoPolicy.VentPhase.WARN)
                        && !ArenaInfernoPolicy.ventDamages(ArenaInfernoPolicy.VentPhase.RECOVER)
                        && !ArenaInfernoPolicy.ventDamages(ArenaInfernoPolicy.VentPhase.REMOVE),
                "only the eruption tick may damage");
    }

    private static void testVentCountScaling() {
        check(ArenaInfernoPolicy.ventCountForRoster(0) == 1, "an empty roster still gets one warned vent");
        check(ArenaInfernoPolicy.ventCountForRoster(4) == 1, "a small roster gets one vent");
        check(ArenaInfernoPolicy.ventCountForRoster(8) == 2, "eight players get two vents");
        check(ArenaInfernoPolicy.ventCountForRoster(20) == ArenaInfernoPolicy.VENT_COUNT,
                "a full roster gets the authored maximum");
        check(ArenaInfernoPolicy.ventCountForRoster(500) == ArenaInfernoPolicy.VENT_COUNT,
                "the simultaneous vent count is bounded");
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