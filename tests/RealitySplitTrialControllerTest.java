import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.Wave7RoomLayoutPolicy;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState;
import me.copimine.endevent.runtime.RealitySplitTrialController;
import me.copimine.endevent.runtime.RealitySplitTrialController.EchoCompletionReceipt;

public final class RealitySplitTrialControllerTest {
    public static void main(String[] args) {
        UUID[] players = {
                player("trial-a"), player("trial-b"), player("trial-c"), player("trial-d"),
                player("trial-e"), player("trial-f")
        };

        RealitySplitTrialController solo = new RealitySplitTrialController();
        solo.begin(41L, ChamberIsolationPolicy.assign(List.of(players[0])));
        check(solo.activeTrialCount() == 1, "a solo roster gets one appropriate trial room");
        check(solo.trial(0).trial() == RealitySplitTrialController.Trial.WARDEN,
                "the first active room hosts the Warden trial");
        check(solo.defeatWarden(41L, 0).complete(), "Warden death completes its room");
        check(solo.allComplete(41L), "a one-room encounter completes after its trial");

        RealitySplitTrialController reflection = new RealitySplitTrialController();
        reflection.begin(42L, ChamberIsolationPolicy.assign(List.of(players[0], players[1])));
        check(reflection.activeTrialCount() == 2, "a duo roster receives two trial rooms");
        check(!reflection.hitReflectionSeal(42L, 1, 0, false).accepted(),
                "melee cannot advance the Reflection trial");
        check(reflection.trial(1).progress() == 0, "rejected melee leaves the active seal unchanged");
        check(!reflection.hitReflectionSeal(42L, 1, 1, true).accepted(),
                "only the active seal accepts a reflected projectile");
        for (int seal = 0; seal < 3; seal++) {
            var result = reflection.hitReflectionSeal(42L, 1, seal, true);
            check(result.accepted(), "the current seal accepts a reflected projectile");
            check(result.complete() == (seal == 2), "exactly three seals complete the trial");
        }
        check(!reflection.hitReflectionSeal(41L, 1, 0, true).accepted(),
                "a previous encounter generation cannot change trial progress");

        RealitySplitTrialController juggernaut = new RealitySplitTrialController();
        juggernaut.begin(43L, ChamberIsolationPolicy.assign(
                List.of(players[0], players[1], players[2])));
        check(juggernaut.activeTrialCount() == 3, "three players use three trial rooms");
        check(juggernaut.trial(2).trial() == RealitySplitTrialController.Trial.JUGGERNAUT,
                "the third room hosts the Juggernaut trial");
        check(!juggernaut.mayDamageJuggernaut(43L, 2),
                "ordinary damage cannot bypass the three armor anchors");
        check(!juggernaut.hitJuggernautAnchor(43L, 2, 1, true).accepted(),
                "anchors must be hit in sequence");
        for (int anchor = 0; anchor < 3; anchor++) {
            var result = juggernaut.hitJuggernautAnchor(43L, 2, anchor, true);
            check(result.accepted(), "a valid charge collision breaks the next armor stage");
            check(result.stage() == (anchor == 2
                    ? RealitySplitTrialController.Stage.EXPOSED
                    : RealitySplitTrialController.Stage.ACTIVE),
                    "the Juggernaut is exposed only after the third anchor");
        }
        check(juggernaut.defeatJuggernaut(43L, 2).complete(),
                "the exposed Juggernaut can be finished to complete the room");

        RealitySplitTrialController fullParty = new RealitySplitTrialController();
        fullParty.begin(44L, ChamberIsolationPolicy.assign(List.of(players)));
        check(fullParty.activeTrialCount() == 4, "four or more players use all four trials");
        check(fullParty.trial(3).trial() == RealitySplitTrialController.Trial.RIFT_HUNTER,
                "the fourth room hosts the Hunter trial");
        var fivePlayerAssignment = ChamberIsolationPolicy.assign(List.of(
                players[0], players[1], players[2], players[3], players[4]));
        check(fivePlayerAssignment.playersIn(0).size() == 2
                        && fivePlayerAssignment.playersIn(1).size() == 1
                        && fivePlayerAssignment.playersIn(2).size() == 1
                        && fivePlayerAssignment.playersIn(3).size() == 1,
                "five players are distributed 2/1/1/1 across the four trials");
        var sixPlayerAssignment = ChamberIsolationPolicy.assign(List.of(
                players[0], players[1], players[2], players[3], players[4], players[5]));
        check(sixPlayerAssignment.playersIn(0).size() == 2
                        && sixPlayerAssignment.playersIn(1).size() == 2
                        && sixPlayerAssignment.playersIn(2).size() == 1
                        && sixPlayerAssignment.playersIn(3).size() == 1,
                "six players are distributed 2/2/1/1 across the four trials");
        check(fullParty.defeatHunter(44L, 3).complete(), "Hunter death completes its room");
        var previousTrials = fullParty.snapshot();
        var invalidTrials = new java.util.LinkedHashMap<>(previousTrials);
        invalidTrials.put(3, new RealitySplitTrialController.TrialState(3,
                RealitySplitTrialController.Trial.WARDEN,
                RealitySplitTrialController.Stage.ACTIVE, 0, 1));
        boolean invalidRestoreRejected = false;
        try {
            fullParty.restore(99L, ChamberIsolationPolicy.assign(List.of(players)), invalidTrials);
        } catch (IllegalArgumentException expected) {
            invalidRestoreRejected = true;
        }
        check(invalidRestoreRejected, "foreign trial identity must be rejected");
        check(fullParty.owns(44L) && !fullParty.owns(99L),
                "rejected trial restore must not publish a new generation");
        check(fullParty.snapshot().equals(previousTrials),
                "rejected trial restore must preserve earlier completed rooms and progress");
        for (var rejected : java.util.Arrays.<java.util.Map<Integer,
                RealitySplitTrialController.TrialState>>asList(null, java.util.Map.of())) {
            try {
                fullParty.restore(100L, ChamberIsolationPolicy.assign(List.of(players)), rejected);
                throw new AssertionError("missing trial states must be rejected");
            } catch (IllegalArgumentException expected) { }
            check(fullParty.owns(44L) && fullParty.snapshot().equals(previousTrials),
                    "incomplete restore must preserve live trial state");
        }
        fullParty.clear();
        check(!fullParty.owns(44L) && fullParty.activeTrialCount() == 0,
                "clear removes all trial state and is safe to repeat");
        fullParty.clear();

        namedTrials();
        System.out.println("RealitySplitTrialControllerTest OK");
    }

    private static UUID player(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void namedTrials() {
        // This tests immutable named room allocation and actor-bound personal Echo completion.
        UUID event = player("named-event");
        for (int size : new int[] {2, 3, 4, 5, 6, 10, 20}) {
            List<UUID> roster = java.util.stream.IntStream.range(0, size)
                    .mapToObj(i -> player("named-owner-" + i)).toList();
            var assignment = ChamberIsolationPolicy.assign(roster);
            var sampled = new java.util.HashSet<RealitySplitTrialController.NamedTrial>();
            for (int seed = 0; seed < 64; seed++) {
                long sampleSeed = seed * 1_000_000_007L;
                var controller = new RealitySplitTrialController();
                var expectedLayout = Wave7RoomLayoutPolicy.select(71L, roster, new java.util.Random(sampleSeed));
                check(expectedLayout.roomByPlayer().equals(assignment.chamberByPlayer()),
                        "shared layout policy preserves the authoritative chamber assignment");
                controller.beginNamed(event, 71L, assignment, new java.util.Random(sampleSeed));
                var data = controller.namedSnapshot();
                var expectedTrials = expectedLayout.rooms().stream().map(room -> switch (room.trial()) {
                    case WARDEN -> RealitySplitTrialController.NamedTrial.WARDEN;
                    case ECHO -> RealitySplitTrialController.NamedTrial.PERSONAL_ECHO;
                    case MARKSMAN -> RealitySplitTrialController.NamedTrial.MOBILE_MARKSMAN;
                    case ARCHMAGE -> RealitySplitTrialController.NamedTrial.RIFT_ARCHMAGE;
                }).toList();
                check(data.selectedLayout().equals(expectedTrials),
                        "production room roles use the shared Wave 7 layout policy");
                check(data.trials().size() == Math.min(4, size), "roster chooses same count of parallel rooms");
                check(new java.util.HashSet<>(data.selectedLayout()).size() == data.selectedLayout().size(),
                        "selection is without replacement");
                sampled.addAll(data.selectedLayout());
                if (size >= 4) check(data.selectedLayout().containsAll(
                        List.of(RealitySplitTrialController.NamedTrial.values())), "four-plus runs all trials");
                var restored = new RealitySplitTrialController();
                restored.restoreNamed(event, 71L, assignment, data);
                check(restored.namedSnapshot().equals(data), "restore keeps sampled layout exactly");
                for (int room = 0; room < data.selectedLayout().size(); room++) {
                    if (data.selectedLayout().get(room) == RealitySplitTrialController.NamedTrial.PERSONAL_ECHO) {
                        var cells = controller.echoDuelCells(room);
                        check(cells.size() == assignment.playersIn(room).size(),
                                "each Echo owner receives one logical cell");
                        check(restored.echoDuelCells(room).equals(cells),
                                "restored Echo derives the same owner cells without reroll");
                        check(cells.stream().map(cell -> cell.owner()).collect(java.util.stream.Collectors.toSet())
                                        .equals(assignment.playersIn(room)),
                                "cell owners match the immutable chamber assignment");
                    } else {
                        check(controller.echoDuelCells(room).isEmpty(),
                                "non-Echo rooms do not expose personal duel cells");
                    }
                }
                check(restored.snapshot().isEmpty() && restored.trial(0) == null,
                        "named identities never appear as legacy room state");
                check(!restored.restoreCompleted(71L, 0), "legacy room-graph receipt cannot complete a named trial");
            }
            check(sampled.size() == 4, "small rosters can sample every approved archetype");
        }

        // Six sorted owners put two owners in room 1. Each gets an independent personal Echo.
        List<UUID> roster = java.util.stream.IntStream.range(1, 7)
                .mapToObj(i -> new UUID(0L, i)).toList();
        var assignment = ChamberIsolationPolicy.assign(roster);
        var controller = new RealitySplitTrialController();
        var layout = List.of(RealitySplitTrialController.NamedTrial.WARDEN,
                RealitySplitTrialController.NamedTrial.PERSONAL_ECHO,
                RealitySplitTrialController.NamedTrial.MOBILE_MARKSMAN,
                RealitySplitTrialController.NamedTrial.RIFT_ARCHMAGE);
        controller.beginNamed(event, 72L, assignment, layout);
        var echoOwners = assignment.playersIn(1).stream().sorted().toList();
        UUID first = echoOwners.get(0), second = echoOwners.get(1);
        check(!controller.completeNamedActor(event, 72L, 1, RealitySplitTrialController.NamedTrial.PERSONAL_ECHO),
                "generic actor death cannot resolve a personal Echo room");
        check(!controller.completePersonalEcho(event, 72L, 1, roster.get(0)), "owner-only completion cannot finish Echo");
        check(!controller.completePersonalEcho(player("wrong-event"), 72L, 1, first), "owner-only completion is always rejected");
        check(!controller.completePersonalEcho(event, 71L, 1, first), "stale owner-only completion cannot finish Echo");
        var firstDuel = activateEcho(controller, event, 72L, 1, first, 301L);
        check(!controller.completePersonalEcho(new EchoCompletionReceipt(player("wrong-event"), 72L, 84L,
                1, firstDuel.duelId(), first, firstDuel.actor().actorUuid(), 1L)),
                "foreign event cannot complete the current actor");
        check(!controller.completePersonalEcho(new EchoCompletionReceipt(event, 71L, 84L,
                1, firstDuel.duelId(), first, firstDuel.actor().actorUuid(), 1L)),
                "stale attempt cannot complete the current actor");
        EchoCompletionReceipt firstReceipt = receipt(firstDuel);
        check(controller.completePersonalEcho(firstReceipt), "first owner's current Echo actor completes");
        check(controller.namedTrial(1).stage() == RealitySplitTrialController.Stage.ACTIVE,
                "other owner keeps shared Echo room unfinished");
        check(!controller.completePersonalEcho(firstReceipt), "actor death receipt is emitted exactly once");
        var checkpoint = controller.namedSnapshot();
        controller.beginNamed(event, 72L, assignment, new java.util.Random(999));
        check(controller.namedSnapshot().equals(checkpoint), "repeated start cannot reroll layout or erase owner receipts");
        var restored = new RealitySplitTrialController();
        restored.restoreNamed(event, 72L, assignment, checkpoint);
        restored.restoreNamed(event, 72L, assignment, checkpoint);
        check(!restored.completePersonalEcho(firstReceipt), "duplicate restore preserves owner receipt");
        var secondDuel = activateEcho(restored, event, 72L, 1, second, 302L);
        check(restored.completePersonalEcho(receipt(secondDuel)), "second owner's Echo completes independently");
        check(restored.namedTrial(1).stage() == RealitySplitTrialController.Stage.COMPLETE,
                "Echo room completes only after every personal Echo");
        check(!restored.allComplete(72L), "one completed room does not finish all selected trials");
        for (int room : new int[] {0, 2, 3}) {
            check(restored.completeNamedActor(event, 72L, room, layout.get(room)), "validated named actor death completes room");
            check(!restored.completeNamedActor(event, 72L, room, layout.get(room)), "named actor completion is exactly once");
        }
        check(restored.allComplete(72L) && !restored.allComplete(73L), "only all selected receipts complete own generation");

        var previous = restored.namedSnapshot();
        var changedLayout = List.of(layout.get(2), layout.get(1), layout.get(0), layout.get(3));
        var changedStates = new java.util.LinkedHashMap<>(previous.trials());
        for (int room : new int[] {0, 2}) {
            var old = previous.trials().get(room);
            changedStates.put(room, new RealitySplitTrialController.NamedTrialState(room, changedLayout.get(room),
                    old.stage(), old.owners(), old.echoReceipts()));
        }
        var changed = new RealitySplitTrialController.NamedData(
                event, 72L, changedLayout, changedStates, previous.personalEchoDuels());
        expectRejected(() -> restored.restoreNamed(event, 72L, assignment, changed), "same attempt cannot replace selected layout");
        check(restored.namedSnapshot().equals(previous), "rejected layout replacement preserves receipt state");
        var swapped = new java.util.LinkedHashMap<>(assignment.chamberByPlayer());
        swapped.put(roster.get(0), 1);
        swapped.put(first, 0);
        expectRejected(() -> restored.restoreNamed(event, 72L,
                new ChamberIsolationPolicy.Assignment(4, swapped), previous), "changed owners must fail restore");
        check(restored.namedSnapshot().equals(previous), "failed restore preserves all published rooms and receipts");
        expectRejected(() -> restored.restoreNamed(event, 73L, assignment, previous), "stale restore is refused");
        check(restored.namedSnapshot().equals(previous) && restored.owns(72L), "stale restore cannot publish new generation");
        restored.clear();
        check(restored.namedTrial(1) == null && !restored.allComplete(72L), "clear removes named attempt state");
    }

    private static RealitySplitEchoDuelState.DuelRecord activateEcho(
            RealitySplitTrialController controller, UUID event, long attempt, int room,
            UUID owner, long identity) {
        var planned = controller.namedSnapshot().personalEchoDuels().get(room).get(owner);
        var items = new ArrayList<EchoLoadoutState.Item>();
        for (int slot = 0; slot < 41; slot++) items.add(EchoLoadoutState.Item.empty());
        items.set(2, new EchoLoadoutState.Item("IRON_SWORD", 1, 250, 0, Map.of(), "", false));
        var loadout = EchoLoadoutState.create(event, attempt, planned.duelId(), owner, items, 2);
        var actor = new RealitySplitEchoDuelState.ActorState(new UUID(1L, identity), 20.0, 20.0,
                RealitySplitEchoDuelState.ActorLifecycle.ALIVE, 1, 20, 30, 40, 0, 0);
        var active = new RealitySplitEchoDuelState.DuelRecord(event, attempt, attempt + 12L,
                room, planned.duelId(), owner, RealitySplitEchoDuelState.Lifecycle.ACTIVE,
                20.0, 20.0, 0, 1, actor, loadout.revision(), loadout.encode(),
                EchoBehaviorStrategy.from(new EchoBehaviorStrategy.ProfileCounts(
                        0, 0, 0, 0, 0, 0, 0, true, 0.0D)));
        check(controller.registerPersonalEchoDuel(active), "planned personal Echo becomes an actor-bound duel");
        return active;
    }

    private static EchoCompletionReceipt receipt(RealitySplitEchoDuelState.DuelRecord duel) {
        return new EchoCompletionReceipt(duel.eventId(), duel.attemptGeneration(), duel.runtimeGeneration(),
                duel.room(), duel.duelId(), duel.ownerUuid(), duel.actor().actorUuid(), 1L);
    }

    private static void expectRejected(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
