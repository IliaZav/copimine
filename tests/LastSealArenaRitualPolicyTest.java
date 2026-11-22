import me.copimine.endevent.domain.LastSealArenaRitualPolicy;

/**
 * The LAST_SEAL arena ritual promised a much larger, still bounded
 * transformation. This probe proves the geometry is finite, deterministic and
 * cannot grow with time or with the number of players.
 */
public final class LastSealArenaRitualPolicyTest {
    private static int checks;

    private LastSealArenaRitualPolicyTest() {
    }

    public static void main(String[] args) {
        theCadenceIsFixedAndCheapEnough();
        theSealStaysFiniteAndBounded();
        theSpikesNeverRunAway();
        theAnchorsAreEvenlySpacedAndDistinct();
        System.out.println("LastSealArenaRitualPolicyTest: PASS (" + checks + " checks)");
    }

    private static void theCadenceIsFixedAndCheapEnough() {
        check("a due tick is due", LastSealArenaRitualPolicy.ritualDue(0));
        check("a due tick is periodic", LastSealArenaRitualPolicy.ritualDue(
                LastSealArenaRitualPolicy.RITUAL_PERIOD_TICKS * 1000));
        check("the off tick between dues is not due",
                !LastSealArenaRitualPolicy.ritualDue(1));
        int points = LastSealArenaRitualPolicy.pointsPerDueTick();
        // The per-layer figures must add up to the advertised total.
        check("the advertised total equals its layers",
                points == LastSealArenaRitualPolicy.sealPoints()
                        + LastSealArenaRitualPolicy.sigilPoints()
                        + LastSealArenaRitualPolicy.spikePoints()
                        + LastSealArenaRitualPolicy.pillarPoints());
        // The authored silhouette ceiling is 96; the whole ritual must fit.
        check("the ritual fits the authored point ceiling (found " + points + ")",
                points <= LastSealArenaRitualPolicy.MAX_POINTS_PER_DUE_TICK);
        check("the ritual still draws a real shape (found " + points + ")", points >= 32);
        // Amortised over its cadence it must not outdraw the halo it supplements.
        check("the ritual is not more expensive per tick than the legacy halo (found "
                        + LastSealArenaRitualPolicy.averagePointsPerTick() + ")",
                LastSealArenaRitualPolicy.averagePointsPerTick() <= 22.5D);
    }

    private static void theSealStaysFiniteAndBounded() {
        for (int tick = -500; tick <= 5000; tick++) {
            double seal = LastSealArenaRitualPolicy.sealRadius(tick);
            check("seal finite at " + tick, Double.isFinite(seal));
            check("seal within [min, max] at " + tick,
                    seal >= LastSealArenaRitualPolicy.SEAL_MIN_RADIUS - 1e-9
                            && seal <= LastSealArenaRitualPolicy.SEAL_MAX_RADIUS + 1e-9);
            double sigil = LastSealArenaRitualPolicy.sigilRadius(tick);
            check("sigil finite at " + tick, Double.isFinite(sigil));
            check("sigil stays near its base radius at " + tick,
                    sigil > 0.0D && sigil <= LastSealArenaRitualPolicy.SIGIL_RADIUS + 1.0D);
            check("seal phase in range at " + tick,
                    phase(LastSealArenaRitualPolicy.sealPhase(tick)));
            check("sigil phase in range at " + tick,
                    phase(LastSealArenaRitualPolicy.sigilPhase(tick)));
        }
    }

    private static void theSpikesNeverRunAway() {
        for (int tick = -500; tick <= 5000; tick++) {
            for (int index = 0; index < LastSealArenaRitualPolicy.SPIKE_COUNT + 3; index++) {
                double height = LastSealArenaRitualPolicy.spikeHeight(tick, index);
                check("spike finite at " + tick + "/" + index, Double.isFinite(height));
                check("spike within [0, max] at " + tick + "/" + index,
                        height >= 0.0D && height <= LastSealArenaRitualPolicy.SPIKE_MAX_HEIGHT + 1e-9);
            }
        }
        // A pulse that actually moves, rather than a constant spike.
        check("spike height varies over a cycle",
                Math.abs(LastSealArenaRitualPolicy.spikeHeight(0, 0)
                        - LastSealArenaRitualPolicy.spikeHeight(30, 0)) > 0.5D);
    }

    private static void theAnchorsAreEvenlySpacedAndDistinct() {
        java.util.Set<String> spikes = new java.util.HashSet<>();
        for (int i = 0; i < LastSealArenaRitualPolicy.SPIKE_COUNT; i++) {
            double angle = LastSealArenaRitualPolicy.spikeAngle(i);
            check("spike angle finite at " + i, Double.isFinite(angle) && phase(angle));
            spikes.add(String.format(java.util.Locale.ROOT, "%.4f", angle));
        }
        check("every spike anchor is distinct", spikes.size() == LastSealArenaRitualPolicy.SPIKE_COUNT);
        java.util.Set<String> pillars = new java.util.HashSet<>();
        for (int i = 0; i < LastSealArenaRitualPolicy.PILLAR_COUNT; i++) {
            pillars.add(String.format(java.util.Locale.ROOT, "%.4f",
                    LastSealArenaRitualPolicy.pillarAngle(i)));
        }
        check("every pillar anchor is distinct",
                pillars.size() == LastSealArenaRitualPolicy.PILLAR_COUNT);
        // Determinism: the same tick and index always yields the same value.
        check("spike is deterministic",
                LastSealArenaRitualPolicy.spikeHeight(77, 3) == LastSealArenaRitualPolicy.spikeHeight(77, 3));
        check("seal is deterministic",
                LastSealArenaRitualPolicy.sealRadius(77) == LastSealArenaRitualPolicy.sealRadius(77));
    }

    private static boolean phase(double angle) {
        return angle >= 0.0D && angle < Math.PI * 2.0D + 1e-9;
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
    }
}
