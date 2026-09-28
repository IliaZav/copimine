import me.copimine.endevent.domain.RitualCasterProgressionPolicy;
import me.copimine.endevent.domain.RitualCasterProgressionPolicy.PrisonerAbility;
import me.copimine.endevent.domain.RitualCasterProgressionPolicy.Transition;
import me.copimine.endevent.domain.RitualCasterProgressionPolicy.MajorSpell;

public final class RitualCasterProgressionPolicyTest {
    public static void main(String[] args) {
        Transition first = RitualCasterProgressionPolicy.afterCasterDeath(0);
        check(first.deathCount() == 1, "the first caster death advances progress exactly once");
        check(first.disabledSpell() == MajorSpell.RIFT_BARRAGE,
                "the first death disables Rift Barrage");
        check(first.unlockedAbility() == PrisonerAbility.A_HEAL,
                "the first death unlocks A / Heal");
        check(!first.prisonBroken(), "the first death does not break the prison");
        check(!RitualCasterProgressionPolicy.isSpellEnabled(1, MajorSpell.RIFT_BARRAGE),
                "the first death keeps Rift Barrage disabled");
        check(RitualCasterProgressionPolicy.isSpellEnabled(1, MajorSpell.GRAVITY_WELL),
                "the first death leaves Gravity Well enabled");

        Transition second = RitualCasterProgressionPolicy.afterCasterDeath(first.deathCount());
        check(second.disabledSpell() == MajorSpell.GRAVITY_WELL,
                "the second death disables Gravity Well");
        check(second.unlockedAbility() == PrisonerAbility.S_BATTLE_SURGE,
                "the second death unlocks S / Battle Surge");

        Transition third = RitualCasterProgressionPolicy.afterCasterDeath(second.deathCount());
        check(third.disabledSpell() == MajorSpell.SOUL_BRAND,
                "the third death disables Soul Brand");
        check(third.unlockedAbility() == PrisonerAbility.D_GUARDIAN_LINK,
                "the third death unlocks D / Guardian Link");

        Transition fourth = RitualCasterProgressionPolicy.afterCasterDeath(third.deathCount());
        check(fourth.disabledSpell() == MajorSpell.RIFT_CHAINS,
                "the fourth death disables Rift Chains");
        check(fourth.unlockedAbility() == PrisonerAbility.F_TURNCOAT,
                "the fourth death unlocks F / Turncoat");
        check(!fourth.prisonBroken(), "the fourth death leaves the final anchor active");

        Transition fifth = RitualCasterProgressionPolicy.afterCasterDeath(fourth.deathCount());
        check(fifth.deathCount() == 5, "the fifth death reaches the terminal count");
        check(fifth.disabledSpell() == MajorSpell.NONE,
                "the final anchor owns no major spell");
        check(fifth.unlockedAbility() == PrisonerAbility.NONE,
                "the final anchor unlocks no additional prisoner ability");
        check(fifth.prisonBroken(), "the fifth death breaks the prison");
        check(!RitualCasterProgressionPolicy.isSpellEnabled(5, MajorSpell.SOUL_BRAND),
                "the final death leaves no earlier major spell enabled");

        expectIllegalArgument(() -> RitualCasterProgressionPolicy.afterCasterDeath(-1));
        expectIllegalArgument(() -> RitualCasterProgressionPolicy.afterCasterDeath(5));
        System.out.println("RitualCasterProgressionPolicyTest OK");
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            throw new AssertionError("invalid death count must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
