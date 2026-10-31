import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy.ProfileCounts;

/**
 * The Echo profile compiler had a normalization defect: sustain was divided by
 * total attacks, so a participant who lands many hits but heals the same number
 * of times as everyone else was scored as barely healing. Sustain must be a
 * share of that participant's own sustained actions, not of their attacks.
 */
public final class EchoBehaviorStrategyNormalizationTest {
    public static void main(String[] args) {
        healingIsNormalizedByItsOwnActionsNotByAttacks();
        moreAttacksDoNotSuppressAParticipantsHealing();
        anAggressiveNonHealerStillHasLowSustain();
        neutralValuesHoldWhenThereIsNoEvidence();
        System.out.println("EchoBehaviorStrategyNormalizationTest OK");
    }

    private static void healingIsNormalizedByItsOwnActionsNotByAttacks() {
        // Three heals and no attacks: sustain is unambiguous.
        var healerOnly = EchoBehaviorStrategy.from(counts(0, 0, 0, 0, 0, 3, 0.5D));
        check(healerOnly.sustainPermille() == 1_000,
                "a participant who only heals has full sustain weight");
        // Three heals plus three attacks: healing is half of their sustained actions.
        var balanced = EchoBehaviorStrategy.from(counts(3, 0, 0, 0, 0, 3, 0.5D));
        check(balanced.sustainPermille() == 500,
                "three heals and three hits is half sustain, got " + balanced.sustainPermille());
    }

    private static void moreAttacksDoNotSuppressAParticipantsHealing() {
        // Sustained behaviour is measured against the participant's own actions,
        // not against somebody else's volume. A healer who also fights keeps a
        // proportional weight, and the weight is always healing / (healing + attacks).
        var fewAttacks = EchoBehaviorStrategy.from(counts(3, 0, 0, 0, 0, 3, 0.5D));
        var manyAttacks = EchoBehaviorStrategy.from(counts(300, 0, 0, 0, 0, 3, 0.5D));
        check(fewAttacks.sustainPermille() == 500,
                "3 heals out of 6 sustained actions is half, got " + fewAttacks.sustainPermille());
        check(manyAttacks.sustainPermille() == 10,
                "3 heals out of 303 sustained actions is 1%, got " + manyAttacks.sustainPermille());
        // The defect this replaced divided healing by attacks alone, so pure
        // healing (no attacks at all) collapsed to the neutral fallback instead
        // of reading as full sustain.
        var healerOnly = EchoBehaviorStrategy.from(counts(0, 0, 0, 0, 0, 7, 0.5D));
        check(healerOnly.sustainPermille() == 1_000,
                "healing without attacks is the maximum sustain weight");
    }

    private static void anAggressiveNonHealerStillHasLowSustain() {
        var attacker = EchoBehaviorStrategy.from(counts(40, 0, 0, 0, 0, 0, 0.5D));
        check(attacker.sustainPermille() == 0,
                "a participant who never heals has zero sustain weight");
    }

    private static void neutralValuesHoldWhenThereIsNoEvidence() {
        var empty = EchoBehaviorStrategy.from(counts(0, 0, 0, 0, 0, 0, 0.0D));
        check(empty.meleePermille() == 500 && empty.rangedPermille() == 500,
                "with no attacks the melee/ranged split is neutral");
        check(empty.sustainPermille() == 500,
                "with no sustained actions the sustain weight is neutral");
        check(empty.mobilityPermille() == 500 && empty.defensePermille() == 500,
                "with no movement samples mobility and defense are neutral");
        check(empty.confidencePermille() == 0,
                "no observations means zero confidence, not a fabricated guess");
    }

    private static ProfileCounts counts(long melee, long ranged, long movement, long sprint,
                                        long shield, long heals, double confidence) {
        return new ProfileCounts(melee, ranged, movement, sprint, shield, heals, 0, true, confidence);
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}