import me.copimine.endevent.domain.Wave5EncounterPolicy;

public final class Wave5EncounterPolicyTest {
    public static void main(String[] args) {
        long generation = 7L;
        Wave5EncounterPolicy.State state = Wave5EncounterPolicy.initial(generation);
        state = Wave5EncounterPolicy.beginRingTwo(state, generation, 100L);
        check(state.phase() == Wave5EncounterPolicy.Phase.RING_TWO_GUARDS,
                "ring two starts after ring one");
        check(!Wave5EncounterPolicy.reconstructionComplete(state, generation, 299L),
                "guard reconstruction is ten seconds");
        check(Wave5EncounterPolicy.reconstructionComplete(state, generation, 300L),
                "guard reconstruction completes at ten seconds");
        state = Wave5EncounterPolicy.beginRingThree(state, generation, 300L);
        check(state.phase() == Wave5EncounterPolicy.Phase.RING_THREE_PRISONER,
                "ring three opens after reconstruction");
        state = Wave5EncounterPolicy.beginFinal(state, generation, 400L);
        check(Wave5EncounterPolicy.guardShieldActive(state),
                "elite is shielded while guards live");
        check(!Wave5EncounterPolicy.eliteCanTakeDamage(state),
                "elite cannot be damaged before guards die");
        check(Wave5EncounterPolicy.attackingGuardIndex(2, 400L) == 2,
                "duo attack slot is deterministic");
        check(Wave5EncounterPolicy.attackingGuardIndex(3, 400L) == -1,
                "duo-only sequential guard rule is bounded");
        state = Wave5EncounterPolicy.guardDefeated(state, generation, 0);
        state = Wave5EncounterPolicy.guardDefeated(state, generation, 1);
        state = Wave5EncounterPolicy.guardDefeated(state, generation, 2);
        check(Wave5EncounterPolicy.eliteCanTakeDamage(state),
                "elite becomes vulnerable after all three guards");
        check(!Wave5EncounterPolicy.drainPrisoner(state, generation, 1399L).drained(),
                "prisoner does not drain before fifty seconds");
        Wave5EncounterPolicy.DrainResult drain =
                Wave5EncounterPolicy.drainPrisoner(state, generation, 1400L);
        check(drain.drained() && drain.amount() == 3.0D,
                "prisoner loses three HP each drain");
        state = drain.state();
        check(state.prisonerStrengthStacks() == 1,
                "successful drain adds one bounded strength stack");
        check(!Wave5EncounterPolicy.drainPrisoner(state, generation, 2399L).drained(),
                "prisoner drain respects the interval");
        for (long tick = 2400L; tick <= 8400L; tick += 1000L) {
            state = Wave5EncounterPolicy.drainPrisoner(state, generation, tick).state();
        }
        check(state.prisonerHealth() == Wave5EncounterPolicy.PRISONER_MIN_HP,
                "prisoner health never crosses its floor");
        check(state.prisonerStrengthStacks() == Wave5EncounterPolicy.MAX_PRISONER_STRENGTH_STACKS,
                "prisoner strength stacks are capped");
        check(!Wave5EncounterPolicy.drainPrisoner(state, generation, 9400L).drained(),
                "a floor-held prisoner does not emit empty drains");
        state = Wave5EncounterPolicy.prisonerReleased(state, generation);
        check(!Wave5EncounterPolicy.drainPrisoner(state, generation, 3000L).drained(),
                "released prisoner cannot be drained");
        state = Wave5EncounterPolicy.eliteDefeated(state, generation);
        check(Wave5EncounterPolicy.isComplete(state), "encounter completes after elite death");
        check(Wave5EncounterPolicy.guardDefeated(state, generation, 0) == state,
                "post-completion guard hit is ignored");
        check(Wave5EncounterPolicy.guardHealth(100.0D) == 60.0D,
                "guard health profile is sixty percent");
        check(Wave5EncounterPolicy.eliteHealth(100.0D) == 72.0D,
                "elite health profile is seventy-two percent");
        check(Wave5EncounterPolicy.initial(8L).phase()
                != Wave5EncounterPolicy.beginRingTwo(state, 8L, 1L).phase(),
                "stale generation cannot mutate encounter");
        System.out.println("Wave5EncounterPolicyTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
