import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;

public final class EchoBehaviorStrategyTest {
    public static void main(String[] args) {
        derivesOnlyBoundedWeights();
        complementaryAttackSharesAlwaysSumToOneThousand();
        emptyEvidenceUsesNeutralPerFeatureWeights();
        codecIsCanonicalAndRejectsInvalidProfiles();
        System.out.println("EchoBehaviorStrategyTest OK");
    }

    private static void complementaryAttackSharesAlwaysSumToOneThousand() {
        var strategy = EchoBehaviorStrategy.from(new EchoBehaviorStrategy.ProfileCounts(
                1, 1999, 0, 0, 0, 0, 0, true, 0.5D));
        check(strategy.meleePermille() == 1 && strategy.rangedPermille() == 999,
                "complementary attack evidence rounds once and preserves the total weight");
        check(strategy.meleePermille() + strategy.rangedPermille() == 1_000,
                "melee and ranged weights always sum to 1000");
    }

    private static void derivesOnlyBoundedWeights() {
        var strategy = EchoBehaviorStrategy.from(new EchoBehaviorStrategy.ProfileCounts(
                75, 25, 80, 60, 20, 4, 0b001111, true, 0.75D));
        check(strategy.meleePermille() == 750 && strategy.rangedPermille() == 250,
                "accepted hit mix becomes bounded melee/ranged preference");
        check(strategy.mobilityPermille() == 750 && strategy.defensePermille() == 250,
                "observed sprint and shield rates become bounded preferences");
        check(strategy.sustainPermille() == 38 && strategy.confidencePermille() == 750,
                "healing and sample confidence are quantized into bounded weights");
        check(strategy.observedWaveMask() == 0b001111 && strategy.fromEventStart(),
                "strategy retains only bounded profile provenance");
        check(strategy.encode().equals("1,750,250,750,250,38,750,15,1"),
                "strategy encoding persists only derived weights, not raw combat counts");
    }

    private static void emptyEvidenceUsesNeutralPerFeatureWeights() {
        var strategy = EchoBehaviorStrategy.from(new EchoBehaviorStrategy.ProfileCounts(
                0, 0, 0, 0, 0, 0, 0, true, 0.0D));
        check(strategy.meleePermille() == 500 && strategy.rangedPermille() == 500
                        && strategy.mobilityPermille() == 500 && strategy.defensePermille() == 500
                        && strategy.sustainPermille() == 500,
                "unobserved features use neutral values without inventing a profile");
        check(strategy.confidencePermille() == 0 && strategy.observedWaveMask() == 0,
                "no evidence remains visibly low-confidence");
    }

    private static void codecIsCanonicalAndRejectsInvalidProfiles() {
        var strategy = EchoBehaviorStrategy.from(new EchoBehaviorStrategy.ProfileCounts(
                1, 3, 4, 2, 1, 1, 1, false, 0.5D));
        check(EchoBehaviorStrategy.decode(strategy.encode()).equals(strategy),
                "strategy restores exactly from its compact durable representation");
        expectRejected(() -> EchoBehaviorStrategy.decode(strategy.encode() + ",unknown"),
                "unknown strategy field rejected");
        expectRejected(() -> EchoBehaviorStrategy.decode("1,1001,0,500,500,500,0,0,0"),
                "out-of-range strategy weight rejected");
        expectRejected(() -> new EchoBehaviorStrategy.ProfileCounts(
                0, 0, 10, 11, 0, 0, 1, false, 0.0D),
                "sample count greater than observations rejected");
        expectRejected(() -> new EchoBehaviorStrategy.ProfileCounts(
                0, 0, 0, 0, 0, 0, 64, false, 0.0D),
                "invalid wave mask rejected");
        expectRejected(() -> new EchoBehaviorStrategy.ProfileCounts(
                0, 0, 0, 0, 0, 0, 1, false, Double.NaN),
                "non-finite profile confidence rejected");
    }

    private static void expectRejected(Runnable action, String reason) {
        try { action.run(); throw new AssertionError(reason); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
