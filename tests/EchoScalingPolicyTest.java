import me.copimine.endevent.domain.EchoScalingPolicy;
import me.copimine.endevent.domain.Wave7NamedTrialActorPolicy;

/**
 * ECHO-011 / ECHO-012: a copy is not a 1000 HP monster and not a reskinned
 * parrot. It has 2.5x its owner's maximum health and hits 1.25x harder with the
 * same weapon concept, applied exactly once.
 */
public final class EchoScalingPolicyTest {
    public static void main(String[] args) {
        healthDearesTwoAndAHalfTimesTheOwner();
        healthStaysInsideTheApprovedBand();
        damageBonusAppliesOnceAfterLegitimateDamage();
        refusesMalformedInput();
        System.out.println("EchoScalingPolicyTest OK");
    }

    private static void healthDearesTwoAndAHalfTimesTheOwner() {
        check(EchoScalingPolicy.HEALTH_MULTIPLIER == 2.5D, "the default health multiple is 2.5");
        check(EchoScalingPolicy.copyMaxHealth(20.0D) == 50.0D, "20 HP becomes 50 HP");
        check(EchoScalingPolicy.copyMaxHealth(40.0D) == 100.0D, "armoured owners scale too");
        // A copy is explicitly NOT a Wave 7 miniboss's health.
        check(EchoScalingPolicy.copyMaxHealth(20.0D) < Wave7NamedTrialActorPolicy.MIN_MINIBOSS_HEALTH,
                "a copy must not inherit miniboss-scale health");
    }

    private static void healthStaysInsideTheApprovedBand() {
        // The contract allows 2-3x; the default sits inside it with room to configure.
        check(EchoScalingPolicy.HEALTH_MULTIPLIER >= 2.0D
                        && EchoScalingPolicy.HEALTH_MULTIPLIER <= 3.0D,
                "the health multiple stays inside the approved 2-3x band");
        check(EchoScalingPolicy.copyMaxHealth(20.0D) == EchoScalingPolicy.copyMaxHealth(20.0D),
                "the same owner always computes the same maximum health");
    }

    private static void damageBonusAppliesOnceAfterLegitimateDamage() {
        check(EchoScalingPolicy.DAMAGE_MULTIPLIER == 1.25D, "the default damage bonus is 1.25x");
        check(EchoScalingPolicy.copyDamage(8.0D) == 10.0D, "8 damage becomes 10");
        // Applied once: a second pass would multiply again and break the contract.
        double once = EchoScalingPolicy.copyDamage(8.0D);
        check(once != EchoScalingPolicy.copyDamage(once),
                "the bonus is not idempotent, so it must be applied exactly once");
        check(EchoScalingPolicy.isLegalSource(true, false, false),
                "a legitimate melee hit from the owner is a legal source");
        check(!EchoScalingPolicy.isLegalSource(false, false, false),
                "an external source never triggers the bonus");
        // A scaled arrow keeps its provenance once it is already scaled.
        check(!EchoScalingPolicy.isLegalSource(true, true, false),
                "an already scaled projectile is not scaled a second time");
    }

    private static void refusesMalformedInput() {
        check(EchoScalingPolicy.copyMaxHealth(0.0D) == 0.0D, "zero health stays zero");
        check(EchoScalingPolicy.copyMaxHealth(-5.0D) == 0.0D, "negative health is clamped, not flipped");
        check(EchoScalingPolicy.copyMaxHealth(Double.NaN) == 0.0D, "NaN cannot produce a copy health");
        check(EchoScalingPolicy.copyDamage(-1.0D) == 0.0D, "a negative hit cannot be healed");
        check(EchoScalingPolicy.copyDamage(Double.NaN) == 0.0D, "NaN damage is refused");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}