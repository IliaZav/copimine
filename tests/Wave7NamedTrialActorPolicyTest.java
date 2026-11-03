import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.Wave7NamedTrialActorPolicy;
import me.copimine.endevent.domain.Wave7NamedTrialActorPolicy.Attack;
import me.copimine.endevent.domain.Wave7NamedTrialActorPolicy.Checkpoint;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedTrial;

public final class Wave7NamedTrialActorPolicyTest {
    public static void main(String[] args) {
        providesRoleSpecificPaperActorProfilesAndTelegraphedAttacks();
        keepsTargetAdmissionBoundToTheSelectedRoom();
        roundTripsActorCheckpointAndRejectsForeignOrMalformedState();
        System.out.println("Wave7NamedTrialActorPolicyTest OK");
    }

    private static void providesRoleSpecificPaperActorProfilesAndTelegraphedAttacks() {
        var marksman = Wave7NamedTrialActorPolicy.profile(NamedTrial.MOBILE_MARKSMAN);
        var archmage = Wave7NamedTrialActorPolicy.profile(NamedTrial.RIFT_ARCHMAGE);
        // Superseded: the previous expectation was 120 HP for the Marksman and
        // 200 for the Archmage, which are shared-elite magnitudes. The approved
        // contract requires every Wave 7 miniboss to have at least 1000 REAL HP.
        check(marksman != null && "SKELETON".equals(marksman.entityType())
                        && marksman.maxHealth() >= Wave7NamedTrialActorPolicy.MIN_MINIBOSS_HEALTH
                        && marksman.attacks().size() == 3,
                "Marksman is a real miniboss with three distinct attack patterns");
        check(archmage != null && "EVOKER".equals(archmage.entityType())
                        && archmage.maxHealth() >= Wave7NamedTrialActorPolicy.MIN_MINIBOSS_HEALTH
                        && archmage.attacks().size() == 4,
                "Archmage is a real miniboss with four distinct spells");
        checkMinibossHealthCurve();
        checkMarksmanFanShape();
        check(Wave7NamedTrialActorPolicy.hasActorAdapter(NamedTrial.MOBILE_MARKSMAN)
                        && Wave7NamedTrialActorPolicy.hasActorAdapter(NamedTrial.RIFT_ARCHMAGE)
                        && !Wave7NamedTrialActorPolicy.hasActorAdapter(NamedTrial.PERSONAL_ECHO),
                "only the dedicated role adapters are admitted by this policy");
        check(Wave7NamedTrialActorPolicy.attackFor(NamedTrial.MOBILE_MARKSMAN, 0)
                        == Attack.MARKSMAN_QUICK_SHOT
                        && Wave7NamedTrialActorPolicy.attackFor(NamedTrial.MOBILE_MARKSMAN, 1)
                        == Attack.MARKSMAN_HEAVY_SHOT
                        && Wave7NamedTrialActorPolicy.attackFor(NamedTrial.MOBILE_MARKSMAN, 2)
                        == Attack.MARKSMAN_SPREAD_SHOT
                        && Wave7NamedTrialActorPolicy.attackFor(NamedTrial.MOBILE_MARKSMAN, 3)
                        == Attack.MARKSMAN_QUICK_SHOT,
                "Marksman cycles quick, heavy and spread shots deterministically");
        check(Wave7NamedTrialActorPolicy.attackFor(NamedTrial.RIFT_ARCHMAGE, 0) == Attack.ARCHMAGE_FANGS
                        && Wave7NamedTrialActorPolicy.attackFor(NamedTrial.RIFT_ARCHMAGE, 1) == Attack.ARCHMAGE_SPEAR
                        && Wave7NamedTrialActorPolicy.attackFor(NamedTrial.RIFT_ARCHMAGE, 2) == Attack.ARCHMAGE_STEP
                        && Wave7NamedTrialActorPolicy.attackFor(NamedTrial.RIFT_ARCHMAGE, 3) == Attack.ARCHMAGE_BEAM,
                "Archmage cycles Fangs, Spears, Step and Beam");
        for (Attack attack : List.of(Attack.MARKSMAN_QUICK_SHOT, Attack.MARKSMAN_HEAVY_SHOT,
                Attack.MARKSMAN_SPREAD_SHOT, Attack.ARCHMAGE_FANGS, Attack.ARCHMAGE_SPEAR,
                Attack.ARCHMAGE_STEP, Attack.ARCHMAGE_BEAM)) {
            check(Wave7NamedTrialActorPolicy.telegraphTicks(attack) >= 10,
                    "every damaging action exposes a visible counter window: " + attack);
            check(Wave7NamedTrialActorPolicy.cooldownTicks(attack)
                            > Wave7NamedTrialActorPolicy.telegraphTicks(attack),
                    "every action has a recovery window: " + attack);
        }
    }

    private static void keepsTargetAdmissionBoundToTheSelectedRoom() {
        UUID target = id(21);
        check(Wave7NamedTrialActorPolicy.admitsTarget(id(1), 7, 2,
                        NamedTrial.MOBILE_MARKSMAN, true, target, 2, true),
                "an active room participant is admitted to the matching Marksman");
        check(!Wave7NamedTrialActorPolicy.admitsTarget(id(1), 7, 2,
                        NamedTrial.MOBILE_MARKSMAN, true, target, 1, true),
                "a neighboring room participant cannot be targeted");
        check(!Wave7NamedTrialActorPolicy.admitsTarget(id(1), 7, 2,
                        NamedTrial.RIFT_ARCHMAGE, false, target, 2, true),
                "completed or inactive trials cannot continue spell damage");
        check(!Wave7NamedTrialActorPolicy.admitsTarget(id(1), 7, 2,
                        NamedTrial.WARDEN, true, target, 2, true),
                "named actor projectile admission cannot be used as a Warden bypass");
        check(!Wave7NamedTrialActorPolicy.admitsTarget(id(1), 7, 2,
                        NamedTrial.MOBILE_MARKSMAN, true, target, 2, false),
                "visitors outside the active participant set are rejected");
    }

    private static void roundTripsActorCheckpointAndRejectsForeignOrMalformedState() {
        UUID event = id(31), actor = id(32), target = id(33);
        Checkpoint checkpoint = new Checkpoint(event, 71, 1, NamedTrial.RIFT_ARCHMAGE,
                actor, 9, Attack.ARCHMAGE_BEAM, 2_000L, 3_500L, target, true);
        String encoded = Wave7NamedTrialActorPolicy.encode(checkpoint);
        check(Wave7NamedTrialActorPolicy.decode(encoded, event, 71, 1,
                        NamedTrial.RIFT_ARCHMAGE, actor).equals(checkpoint),
                "actor attack phase, target and one-use support persist exactly");
        expectInvalid(() -> Wave7NamedTrialActorPolicy.decode(encoded, id(99), 71, 1,
                NamedTrial.RIFT_ARCHMAGE, actor));
        expectInvalid(() -> Wave7NamedTrialActorPolicy.decode(encoded, event, 72, 1,
                NamedTrial.RIFT_ARCHMAGE, actor));
        expectInvalid(() -> Wave7NamedTrialActorPolicy.decode(encoded, event, 71, 2,
                NamedTrial.RIFT_ARCHMAGE, actor));
        expectInvalid(() -> Wave7NamedTrialActorPolicy.decode(encoded, event, 71, 1,
                NamedTrial.MOBILE_MARKSMAN, actor));
        expectInvalid(() -> Wave7NamedTrialActorPolicy.decode(encoded, event, 71, 1,
                NamedTrial.RIFT_ARCHMAGE, id(34)));
        expectInvalid(() -> Wave7NamedTrialActorPolicy.decode(encoded + "|", event, 71, 1,
                NamedTrial.RIFT_ARCHMAGE, actor));
        expectInvalid(() -> new Checkpoint(event, 71, 1, NamedTrial.RIFT_ARCHMAGE,
                actor, 9, Attack.MARKSMAN_HEAVY_SHOT, 2_000L, 3_500L, target, false));
    }

    private static void checkMarksmanFanShape() {
        // Contract: three successive fans of eight arrows, not one spray.
        check(Wave7NamedTrialActorPolicy.MARKSMAN_FAN_COUNT == 3,
                "a spread burst is exactly three fans");
        check(Wave7NamedTrialActorPolicy.MARKSMAN_ARROWS_PER_FAN == 8,
                "each fan carries eight arrows");
        check(Wave7NamedTrialActorPolicy.MARKSMAN_ARROWS_PER_BURST == 24,
                "a full burst is 24 arrows, matching the event-wide arrow cap");
        java.util.List<Double> angles = Wave7NamedTrialActorPolicy.fanAngles();
        check(angles.size() == Wave7NamedTrialActorPolicy.MARKSMAN_ARROWS_PER_FAN,
                "one fan produces one angle per arrow");
        check(angles.stream().distinct().count() == angles.size(),
                "no two arrows share an angle, so a fan never double-stacks");
        double spread = Wave7NamedTrialActorPolicy.MARKSMAN_FAN_SPREAD_DEGREES;
        check(Math.abs((angles.get(angles.size() - 1) - angles.get(0))
                        - spread * (Wave7NamedTrialActorPolicy.MARKSMAN_ARROWS_PER_FAN - 1)) < 1.0E-9D,
                "the fan spans a bounded, readable arc");
        // Even arrow counts straddle the axis rather than centring on it.
        check(!angles.contains(0.0D) || angles.size() % 2 == 1,
                "an even fan does not put an arrow exactly on the target line");
        check(Wave7NamedTrialActorPolicy.MARKSMAN_FAN_GAP_TICKS >= 2,
                "fans are separated by a readable gap, not fired in one tick");
    }

    private static void checkMinibossHealthCurve() {
        // SUPERSEDED (2026-11-03): this used to pin the removed frozen-roster
        // ladder base * (1 + 0.25 * (admitted - 2)), which the user rejected —
        // a ten-player party fought the same 1000 HP skeleton as a two-player
        // one. The basis is still the frozen roster (a relog must never heal a
        // living actor); the curve now comes from Wave7MinibossScalingPolicy and
        // is checked across the whole supported band instead of one formula.
        for (int admitted = 2; admitted <= 20; admitted++) {
            for (NamedTrial trial : new NamedTrial[]{
                    NamedTrial.MOBILE_MARKSMAN, NamedTrial.RIFT_ARCHMAGE}) {
                double health = Wave7NamedTrialActorPolicy.minibossHealth(trial, admitted);
                check(health >= Wave7NamedTrialActorPolicy.MIN_MINIBOSS_HEALTH,
                        trial + " at " + admitted + " players must reach the miniboss floor");
                check(health <= me.copimine.endevent.domain.Wave7MinibossScalingPolicy.HEALTH_CAP,
                        trial + " at " + admitted + " players must stay under the miniboss cap");
                if (admitted > 2) {
                    double previous = Wave7NamedTrialActorPolicy.minibossHealth(trial, admitted - 1);
                    check(health > previous,
                            trial + " health must grow at every extra player (" + admitted + ")");
                }
            }
        }
        // The floor is exact at the smallest supported party.
        check(Wave7NamedTrialActorPolicy.minibossHealth(NamedTrial.MOBILE_MARKSMAN, 2)
                        == Wave7NamedTrialActorPolicy.MIN_MINIBOSS_HEALTH,
                "the two-player Marksman sits exactly on the 1000 HP floor");
        check(Wave7NamedTrialActorPolicy.minibossHealth(NamedTrial.RIFT_ARCHMAGE, 20)
                        > Wave7NamedTrialActorPolicy.minibossHealth(NamedTrial.RIFT_ARCHMAGE, 2),
                "health grows with the roster");
        // Damage has to move with the party too, not just health.
        check(Wave7NamedTrialActorPolicy.profile(NamedTrial.MOBILE_MARKSMAN, 2).contactDamage()
                        < Wave7NamedTrialActorPolicy.profile(NamedTrial.MOBILE_MARKSMAN, 10).contactDamage(),
                "miniboss damage must grow with the party, not stay at the solo value");
        // An out-of-range roster is a configuration error, never a silent clamp.
        expectInvalid(() -> Wave7NamedTrialActorPolicy.minibossHealth(NamedTrial.RIFT_ARCHMAGE, 1));
        expectInvalid(() -> Wave7NamedTrialActorPolicy.minibossHealth(NamedTrial.RIFT_ARCHMAGE, 21));
        expectInvalid(() -> Wave7NamedTrialActorPolicy.minibossHealth(null, 5));
    }

    private static UUID id(long value) { return new UUID(0L, value); }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected malformed or foreign actor state to be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
