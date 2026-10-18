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
        check(marksman != null && "SKELETON".equals(marksman.entityType())
                        && marksman.maxHealth() == 120.0D && marksman.attacks().size() == 3,
                "Marksman uses a persistent ranged carrier with three distinct attack patterns");
        check(archmage != null && "EVOKER".equals(archmage.entityType())
                        && archmage.maxHealth() >= 200.0D && archmage.attacks().size() == 4,
                "Archmage uses a distinct persistent carrier with four distinct spells");
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
