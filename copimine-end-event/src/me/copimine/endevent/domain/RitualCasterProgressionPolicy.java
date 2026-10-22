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

    /** Advance one bounded scheduler slot after either an accepted or rejected start. */
    public static int advanceSpellCursor(int cursor, int availableSpellCount) {
        if (availableSpellCount < 1 || availableSpellCount > MajorSpell.values().length - 1) {
            throw new IllegalArgumentException("available spell count must be in [1, 4]");
        }
        return (Math.floorMod(cursor, availableSpellCount) + 1) % availableSpellCount;
    }

    /** An invalid landing grid skips one unsafe cast; other blockers retain the cursor. */
    public static boolean shouldSkipSpellAfterBlocker(StartBlocker blocker) {
        return resolutionFor(blocker) == StartBlockerResolution.SKIP_AND_RETRY;
    }

    /** Unexpected failures after validated admission require operator-visible recovery. */
    public static boolean requiresRecovery(StartBlocker blocker) {
        return resolutionFor(blocker) == StartBlockerResolution.RECOVERY_REQUIRED;
    }

    public static StartBlockerResolution resolutionFor(StartBlocker blocker) {
        if (blocker == null) throw new IllegalArgumentException("spell blocker is required");
        return switch (blocker) {
            case INVALID_GEOMETRY -> StartBlockerResolution.SKIP_AND_RETRY;
            case NO_ELIGIBLE_PARTICIPANTS, CLEANUP_PENDING -> StartBlockerResolution.WAIT;
            case ENCOUNTER_FAILURE -> StartBlockerResolution.RECOVERY_REQUIRED;
        };
    }

    /** Official caster combat is locked until the prisoner is captured; only the isolated combat sandbox bypasses it. */
    public static boolean mayTakeCasterDamage(boolean prisonerCaptured, boolean combatSandbox) {
        return prisonerCaptured || combatSandbox;
    }

    /** Capture is mandatory for an official Wave 6 completion; sandbox combat is explicitly exempt. */
    public static boolean mayComplete(int deathCount, boolean prisonerCaptured,
                                      boolean prisonBroken, boolean runtimeCleaned) {
        return deathCount == TOTAL_CASTERS && prisonerCaptured && prisonBroken && runtimeCleaned;
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

    public enum StartBlocker {
        INVALID_GEOMETRY,
        NO_ELIGIBLE_PARTICIPANTS,
        CLEANUP_PENDING,
        ENCOUNTER_FAILURE
    }

    public enum StartBlockerResolution {
        WAIT,
        SKIP_AND_RETRY,
        RECOVERY_REQUIRED
    }
}
