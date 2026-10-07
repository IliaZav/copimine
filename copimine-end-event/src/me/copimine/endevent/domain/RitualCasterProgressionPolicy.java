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
        MajorSpell addedSpell = switch (deaths) {
            case 1 -> MajorSpell.RIFT_BARRAGE;
            case 2 -> MajorSpell.GRAVITY_WELL;
            case 3 -> MajorSpell.SOUL_BRAND;
            case 4 -> MajorSpell.RIFT_CHAINS;
            default -> MajorSpell.NONE;
        };
        PrisonerAbility unlockedAbility = switch (deaths) {
            case 1 -> PrisonerAbility.Q_HEAL;
            case 2 -> PrisonerAbility.W_BATTLE_SURGE;
            case 3 -> PrisonerAbility.E_GUARDIAN_LINK;
            case 4 -> PrisonerAbility.R_TURNCOAT;
            default -> PrisonerAbility.NONE;
        };
        return new Transition(deaths, addedSpell, unlockedAbility,
                deaths == TOTAL_CASTERS);
    }

    /** Deaths add sphere spells; only the final prison break retires them. */
    public static boolean isSpellEnabled(int deathCount, MajorSpell spell) {
        if (deathCount < 0 || deathCount > TOTAL_CASTERS || spell == null) {
            throw new IllegalArgumentException("death count and major spell are required");
        }
        return switch (spell) {
            case RIFT_BARRAGE -> deathCount >= 1 && deathCount < TOTAL_CASTERS;
            case GRAVITY_WELL -> deathCount >= 2 && deathCount < TOTAL_CASTERS;
            case SOUL_BRAND -> deathCount >= 3 && deathCount < TOTAL_CASTERS;
            case RIFT_CHAINS -> deathCount >= 4 && deathCount < TOTAL_CASTERS;
            case NONE -> false;
        };
    }

    public static java.util.List<MajorSpell> availableSpells(int deathCount) {
        if (deathCount < 0 || deathCount > TOTAL_CASTERS)
            throw new IllegalArgumentException("death count must be in [0, 5]");
        return java.util.Arrays.stream(MajorSpell.values())
                .filter(spell -> isSpellEnabled(deathCount, spell)).toList();
    }

    /** Cleanup is part of the objective and must finish before W6 is complete. */
    public static boolean mayComplete(int deathCount, boolean prisonBroken,
                                      boolean runtimeCleaned) {
        return deathCount == TOTAL_CASTERS && prisonBroken && runtimeCleaned;
    }

    public record Transition(int deathCount,
                             MajorSpell addedSpell,
                             PrisonerAbility unlockedAbility,
                             boolean prisonBroken) {
        public Transition {
            if (deathCount < 1 || deathCount > TOTAL_CASTERS) {
                throw new IllegalArgumentException("death count must be in [1, 5]");
            }
            if (addedSpell == null || unlockedAbility == null) {
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
        Q_HEAL,
        W_BATTLE_SURGE,
        E_GUARDIAN_LINK,
        R_TURNCOAT,
        NONE
    }
}
