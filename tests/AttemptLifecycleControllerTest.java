import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.runtime.AttemptLifecycleController;

public final class AttemptLifecycleControllerTest {
    public static void main(String[] args) {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var controller = new AttemptLifecycleController();
        controller.begin(10L, Set.of(a, b));
        check(controller.markDead(a, 10L), "first death is recorded");
        check(controller.performAttemptWipe(10L, "one dead").status()
                == AttemptLifecycleController.WipeStatus.NO_LIVING_PLAYERS,
                "one dead player must keep the attempt alive");
        check(controller.markDead(b, 10L), "second death is recorded");
        var wiped = controller.performAttemptWipe(10L, "all dead");
        check(wiped.status() == AttemptLifecycleController.WipeStatus.ACCEPTED,
                "all-dead wipe must be accepted");
        check(wiped.nextGeneration() == 11L, "wipe increments generation");
        check(controller.wiping(), "accepted wipe must remain frozen until cleanup commits");
        check(controller.living().isEmpty(), "roster must not be revived before cleanup commits");
        check(!controller.markAlive(a, 10L), "stale callbacks are blocked during cleanup");
        check(controller.commitWipe(10L), "cleanup transaction must commit the next generation");
        check(controller.generation() == 11L, "committed wipe must publish the next generation");
        check(controller.living().size() == 2, "commit restores roster to alive start state");
        check(controller.performAttemptWipe(10L, "stale").status()
                == AttemptLifecycleController.WipeStatus.STALE_GENERATION,
                "old callbacks cannot wipe a new generation");
        check(controller.performAttemptWipe(11L, "not dead").status()
                == AttemptLifecycleController.WipeStatus.NO_LIVING_PLAYERS,
                "new attempt with living roster is protected");
        check(!controller.abortWipe(11L), "an idle generation cannot abort a wipe");

        controller.begin(12L, Set.of(a, b));
        AttemptLifecycleController.ParticipantStatus started = controller.status(a);
        check(started != null && started.registered() && started.active()
                        && started.online() && started.alive() && started.eligibleForObjective(),
                "the authoritative roster records all required participant states");
        check(controller.isRegistered(a, 12L) && !controller.isRegistered(UUID.randomUUID(), 12L),
                "only frozen roster members are registered for the current generation");
        check(controller.markOffline(a, 12L), "disconnect updates roster presence");
        AttemptLifecycleController.ParticipantStatus disconnected = controller.status(a);
        check(disconnected.registered() && disconnected.active() && !disconnected.online()
                        && disconnected.alive() && !disconnected.eligibleForObjective(),
                "disconnect preserves grace-period life while excluding objective eligibility");
        check(!controller.markObjectiveEligible(a, 12L, true),
                "offline roster members cannot become objective eligible");
        check(controller.markOnline(a, 12L), "reconnect updates roster presence");
        check(!controller.status(a).eligibleForObjective(),
                "reconnected participants must be revalidated before using objectives");
        check(controller.markObjectiveEligible(a, 12L, true),
                "a validated living roster member can become objective eligible");
        check(controller.isObjectiveEligible(a, 12L),
                "objective eligibility is read from the authoritative roster state");
        check(controller.markDead(a, 12L), "death updates the authoritative roster");
        AttemptLifecycleController.ParticipantStatus dead = controller.status(a);
        check(dead.registered() && dead.active() && dead.online() && !dead.alive()
                        && !dead.eligibleForObjective(),
                "dead participants remain registered but cannot satisfy objectives");
        check(controller.markAlive(a, 12L), "respawn restores living roster state");
        check(!controller.status(a).eligibleForObjective(),
                "respawn eligibility is recalculated from current arena conditions");
        check(controller.setActive(a, 12L, false), "expired members can be retired from the attempt");
        check(!controller.markAlive(a, 12L) && !controller.status(a).active(),
                "a retired member cannot rejoin after its reconnect grace expires");
        check(controller.isRegistered(a, 12L),
                "retiring a member preserves registration history for the generation");
        check(!controller.roster().contains(a),
                "retired members must not remain in the active encounter roster");
        check(!controller.living().contains(a),
                "retired members cannot keep an otherwise wiped attempt alive");

        controller.begin(20L, Set.of(a, b));
        controller.markDead(a, 20L);
        controller.markDead(b, 20L);
        check(controller.performAttemptWipe(20L, "cleanup failure").status()
                == AttemptLifecycleController.WipeStatus.ACCEPTED,
                "failed-cleanup scenario must open a wipe transaction");
        check(controller.abortWipe(20L), "failed cleanup must be reportable");
        check(controller.wiping(), "failed cleanup must keep the old attempt frozen");
        check(controller.pendingNextGeneration() == 21L,
                "failed cleanup must retain the pending generation for retry");
        check(!controller.markAlive(a, 20L),
                "callbacks must remain blocked after failed cleanup");
        check(controller.performAttemptWipe(20L, "retry").status()
                == AttemptLifecycleController.WipeStatus.ALREADY_IN_PROGRESS,
                "retry must not publish a new wipe while cleanup is still incomplete");
        check(controller.commitWipe(20L), "retry cleanup must be able to commit");
        check(controller.generation() == 21L, "retry must publish the next generation once");
        System.out.println("AttemptLifecycleControllerTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
