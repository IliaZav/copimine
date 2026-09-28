package me.copimine.endevent.domain;

/** Deterministic one-time progression after each of the five Wave 6 Casters dies. */
public final class RitualCasterProgressionPolicy {
    public static final int TOTAL_CASTERS = 5;

    private RitualCasterProgressionPolicy() {
    }

    /**
     * Resolve the next progression transition from the number already
     * recorded. Call once for each newly committed Caster death.
     */
    public static Transition afterCasterDeath(int previousDeathCount) {
        if (previousDeathCount < 0 || previousDeathCount >= TOTAL_CASTERS) {
            throw new IllegalArgumentException("previous Caster death count must be in [0, 4]");
        }
        int deaths = previousDeathCount + 1;
        MajorSpell disabledSpell = switch (deaths) {
            case 1 -> MajorSpell.RIFT_BARRAGE;
            case 2 -> MajorSpell.GRAVITY_WELL;
            case 3 -> MajorSpell.SOUL_BRAND;
            case 4 -> MajorSpell.RIFT_CHAINS;
            default -> MajorSpell.NONE;
        };
        PrisonerAbility unlockedAbility = switch (deaths) {
            case 1 -> PrisonerAbility.A_HEAL;
            case 2 -> PrisonerAbility.S_BATTLE_SURGE;
            case 3 -> PrisonerAbility.D_GUARDIAN_LINK;
            case 4 -> PrisonerAbility.F_TURNCOAT;
            default -> PrisonerAbility.NONE;
        };
        return new Transition(deaths, disabledSpell, unlockedAbility,
                deaths == TOTAL_CASTERS);
    }

    public record Transition(int deathCount,
                             MajorSpell disabledSpell,
                             PrisonerAbility unlockedAbility,
                             boolean prisonBroken) {
        public Transition {
            if (deathCount < 1 || deathCount > TOTAL_CASTERS) {
                throw new IllegalArgumentException("death count must be in [1, 5]");
            }
            if (disabledSpell == null || unlockedAbility == null) {
                throw new IllegalArgumentException("progression results are required");
            }
            if (prisonBroken != (deathCount == TOTAL_CASTERS)) {
                throw new IllegalArgumentException("only the fifth Caster death breaks the prison");
            }
        }
    }

    public enum MajorSpell {
        RIFT_BARRAGE,
        GRAVITY_WELL,
        SOUL_BRAND,
        RIFT_CHAINS,
        NONE
    }

    public enum PrisonerAbility {
        A_HEAL,
        S_BATTLE_SURGE,
        D_GUARDIAN_LINK,
        F_TURNCOAT,
        NONE
    }
}
