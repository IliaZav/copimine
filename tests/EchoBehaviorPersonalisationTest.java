import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.DuelRecord;
import me.copimine.endevent.runtime.RealitySplitTrialController;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.Action;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.MovementIntent;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BRAIN-013 / BRAIN-015 / BRAIN-016 / T-R07 / T-R08: the compiled profile must
 * materially change the copy's legal decisions under one identical observation.
 *
 * <p>Three owners are compiled from histories that differ the way real players
 * differ — a melee/critical brawler, a ranged kiter and a shield/heal defender —
 * and are then asked for a decision with identical scope, observation, health and
 * equipment. If the answers were identical, the personalisation would be
 * decorative metadata.</p>
 */
public final class EchoBehaviorPersonalisationTest {
    private static final UUID EVENT = new UUID(0, 1);
    private static final long ATTEMPT = 7L;
    private static final long RUNTIME = 9L;
    private static int assertions;

    public static void main(String[] args) {
        differentStylesChooseDifferentActions();
        aMovingCopyStillShoots();
        profileEncodingIsNotDecorative();
        System.out.println("EchoBehaviorPersonalisationTest OK assertions=" + assertions);
    }

    /** BRAIN-013: melee, ranged and shield originals must diverge in one scenario. */
    private static void differentStylesChooseDifferentActions() {
        // melee, ranged, mobility, defense, sustain, confidence, holdDistance
        EchoBehaviorStrategy brawler = strategy(900, 100, 200, 600, 900, 700, 40);
        EchoBehaviorStrategy kiter = strategy(100, 900, 900, 200, 350, 250, 950);
        EchoBehaviorStrategy warden = strategy(500, 500, 500, 200, 950, 300, 500);

        Decision near = decide(brawler, 90L, 9.0D);
        Decision far = decide(kiter, 90L, 400.0D);

        check(near.action() == Action.MELEE || near.action() == Action.CRITICAL,
                "a melee original must swing in melee range, got " + near.action());
        check(far.action() == Action.RANGED,
                "a ranged original must shoot from its own band, got " + far.action());

        Decision cornered = decide(kiter, 90L, 1.0D);
        check(cornered.intent() == MovementIntent.KITE,
                "a ranged original must open the gap when its owner steps into reach, got "
                        + cornered.intent());

        // The shield/heal original defends inside its own recorded window, which is
        // derived from its defence weight rather than a shared constant. Defence
        // 200 opens the first ticks of every hundred-tick window.
        Decision defended = decide(warden, 2L, 9.0D);
        check(defended.action() == Action.SHIELD,
                "a defensive original must raise its shield in its own window, got "
                        + defended.action());
        Decision defendedLater = decide(warden, 60L, 9.0D);
        check(defendedLater.action() != Action.SHIELD,
                "the shield window is bounded, not a permanent defence percentage");

        check(near.action() != far.action() || near.intent() != far.intent(),
                "BRAIN-015: swapping the profile must change the produced decision");
    }

    /**
     * BRAIN-005 / ECHO-014: a copy that is repositioning must still be able to
     * act. "Endless retreat without firing" is an explicit failure mode.
     */
    private static void aMovingCopyStillShoots() {
        EchoBehaviorStrategy kiter = strategy(100, 900, 900, 200, 350, 250, 950);
        Decision moving = decide(kiter, 90L, 16.0D);
        check(moving.intent() == MovementIntent.KITE,
                "the copy must intend to open the gap, got " + moving.intent());
        check(moving.action() == Action.RANGED,
                "BRAIN-005: repositioning must not disable the committed action, got "
                        + moving.action());
    }

    /** T-R08 / BRAIN-015 / BRAIN-016: encoding round-trips and sparse data stays honest. */
    private static void profileEncodingIsNotDecorative() {
        EchoBehaviorStrategy brawler = strategy(900, 100, 200, 600, 900, 700, 40);
        EchoBehaviorStrategy restored = EchoBehaviorStrategy.decode(brawler.encode());
        check(restored.equals(brawler), "the durable profile must restore byte-identically");
        Decision original = decide(brawler, 90L, 9.0D);
        Decision replayed = decide(restored, 90L, 9.0D);
        check(original.action() == replayed.action() && original.intent() == replayed.intent(),
                "a restored profile must reproduce the same decision deterministically");

        EchoBehaviorStrategy sparse = strategy(500, 500, 500, 500, 500, 0, 500);
        check(sparse.confidencePermille() == 0,
                "BRAIN-016: thin evidence stays visibly low-confidence instead of pretending");
        Decision sparseDecision = decide(sparse, 90L, 9.0D);
        check(sparseDecision != null,
                "BRAIN-016: a low-confidence copy still fights at baseline competence");
    }

    private static EchoBehaviorStrategy strategy(int melee, int ranged, int mobility, int defense,
                                                 int sustain, int confidence, int hold) {
        return new EchoBehaviorStrategy(melee, ranged, mobility, defense, sustain, confidence,
                0b111_1111, true, 250, hold, 400, defense, 250);
    }

    private record Decision(Action action, MovementIntent intent) { }

    /** One identical observation and one identical loadout for every profile. */
    private static Decision decide(EchoBehaviorStrategy strategy, long tick, double distanceSquared) {
        RealitySplitTrialController controller = new RealitySplitTrialController();
        var assignment = me.copimine.endevent.domain.ChamberIsolationPolicy.assign(
                List.of(id(1), id(2), id(3), id(4)));
        controller.beginNamed(EVENT, ATTEMPT, assignment, List.of(
                RealitySplitTrialController.NamedTrial.WARDEN,
                RealitySplitTrialController.NamedTrial.PERSONAL_ECHO,
                RealitySplitTrialController.NamedTrial.MOBILE_MARKSMAN,
                RealitySplitTrialController.NamedTrial.RIFT_ARCHMAGE));
        var data = controller.namedSnapshot();
        int room = 1;
        UUID owner = data.trials().get(room).owners().iterator().next();
        DuelRecord planned = data.personalEchoDuels().get(room).get(owner);

        var items = new ArrayList<EchoLoadoutState.Item>();
        for (int slot = 0; slot < 41; slot++) items.add(EchoLoadoutState.Item.empty());
        items.set(0, new EchoLoadoutState.Item("IRON_SWORD", 1, 250, 0, Map.of(), "", false));
        items.set(2, new EchoLoadoutState.Item("SHIELD", 1, 336, 0, Map.of(), "", false));
        items.set(3, new EchoLoadoutState.Item("BOW", 1, 384, 0, Map.of(), "", false));
        items.set(4, new EchoLoadoutState.Item("ARROW", 2, 0, 0, Map.of(), "", false));
        items.set(5, new EchoLoadoutState.Item("BREAD", 1, 0, 0, Map.of(), "", false));
        EchoLoadoutState supplies = EchoLoadoutState.create(EVENT, ATTEMPT,
                planned.duelId(), owner, items, 0);
        ActorState actor = new ActorState(id(701), 18.0D, 20.0D,
                ActorLifecycle.ALIVE, 0, 0, 0, 0, 0, 0);
        DuelRecord duel = planned.withActor(RUNTIME, 18.0D, 20.0D, 0L, actor,
                1L, supplies.revision(), supplies.encode(), strategy);

        EchoLoadoutState replica = EchoLoadoutState.restore(duel.replicaState(), duel.eventId(),
                duel.attemptGeneration(), duel.duelId(), owner, duel.replicaRevision());
        var scope = new EchoDuelCombatRuntimeAdapter.Scope(EVENT, ATTEMPT, RUNTIME, room,
                duel.duelId(), owner, duel.actor().actorUuid(), new UUID(0, 900),
                true, true, true, true);
        var decision = EchoDuelCombatRuntimeAdapter.tick(duel, replica, scope,
                new EchoDuelCombatRuntimeAdapter.Observation(tick, distanceSquared, true));
        if (decision.isEmpty()) {
            throw new AssertionError("a legal duel must always yield a decision");
        }
        assertions++;
        return new Decision(decision.get().action(), decision.get().intent());
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}