import me.copimine.endevent.domain.RitualCasterProgressionPolicy;
import me.copimine.endevent.domain.RitualCasterProgressionPolicy.PrisonerAbility;
import me.copimine.endevent.domain.RitualCasterProgressionPolicy.Transition;
import me.copimine.endevent.domain.RitualCasterProgressionPolicy.MajorSpell;

public final class RitualCasterProgressionPolicyTest {
    public static void main(String[] args) {
        Transition first = RitualCasterProgressionPolicy.afterCasterDeath(0);
        check(first.deathCount() == 1, "the first caster death advances progress exactly once");
        check(first.addedSpell() == MajorSpell.RIFT_BARRAGE,
                "the first death adds Rift Barrage");
        check(first.unlockedAbility() == PrisonerAbility.Q_HEAL,
                "the first death unlocks Q / Heal");
        check(!first.prisonBroken(), "the first death does not break the prison");
        check(RitualCasterProgressionPolicy.isSpellEnabled(1, MajorSpell.RIFT_BARRAGE),
                "the first death enables Rift Barrage");
        check(!RitualCasterProgressionPolicy.isSpellEnabled(1, MajorSpell.GRAVITY_WELL),
                "Gravity Well is added by the second death");
        check(!RitualCasterProgressionPolicy.mayComplete(1, false, true),
                "Wave 6 cannot complete after one Caster death");

        Transition second = RitualCasterProgressionPolicy.afterCasterDeath(first.deathCount());
        check(second.addedSpell() == MajorSpell.GRAVITY_WELL,
                "the second death adds Gravity Well without removing Barrage");
        check(second.unlockedAbility() == PrisonerAbility.W_BATTLE_SURGE,
                "the second death unlocks W / Battle Surge");

        Transition third = RitualCasterProgressionPolicy.afterCasterDeath(second.deathCount());
        check(third.addedSpell() == MajorSpell.SOUL_BRAND,
                "the third death adds Soul Brand");
        check(third.unlockedAbility() == PrisonerAbility.E_GUARDIAN_LINK,
                "the third death unlocks E / Guardian Link");

        Transition fourth = RitualCasterProgressionPolicy.afterCasterDeath(third.deathCount());
        check(fourth.addedSpell() == MajorSpell.RIFT_CHAINS,
                "the fourth death adds Rift Chains");
        check(fourth.unlockedAbility() == PrisonerAbility.R_TURNCOAT,
                "the fourth death unlocks R / Turncoat");
        check(!fourth.prisonBroken(), "the fourth death leaves the final anchor active");

        Transition fifth = RitualCasterProgressionPolicy.afterCasterDeath(fourth.deathCount());
        check(fifth.deathCount() == 5, "the fifth death reaches the terminal count");
        check(fifth.addedSpell() == MajorSpell.NONE,
                "the final anchor owns no major spell");
        check(fifth.unlockedAbility() == PrisonerAbility.NONE,
                "the final anchor unlocks no additional prisoner ability");
        check(fifth.prisonBroken(), "the fifth death breaks the prison");
        check(!RitualCasterProgressionPolicy.isSpellEnabled(5, MajorSpell.SOUL_BRAND),
                "the final death leaves no earlier major spell enabled");
        check(!RitualCasterProgressionPolicy.mayComplete(5, true, false),
                "Wave 6 waits for required cleanup after the prison breaks");
        check(RitualCasterProgressionPolicy.mayComplete(5, true, true),
                "Wave 6 completes only after all steps and cleanup finish");

        for (int deaths = 0; deaths <= 5; deaths++) {
            int enabled = 0;
            for (MajorSpell spell : MajorSpell.values()) {
                boolean expected = spell != MajorSpell.NONE && deaths < 5
                        && deaths >= spell.ordinal() + 1;
                check(RitualCasterProgressionPolicy.isSpellEnabled(deaths, spell) == expected,
                        "every previous spell stays available until the fifth death: " + deaths + "/" + spell);
                if (expected) enabled++;
            }
            check(enabled == (deaths == 5 ? 0 : deaths), "zero to four cumulative spells, then terminal cleanup");
        }

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
