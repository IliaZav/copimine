import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.DuelRecord;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.Lifecycle;
import me.copimine.endevent.runtime.RealitySplitTrialController;
import me.copimine.endevent.runtime.RealitySplitTrialController.EchoCompletionReceipt;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedData;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedTrial;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedTrialState;
import me.copimine.endevent.runtime.RealitySplitTrialController.Stage;
import me.copimine.endevent.runtime.RealitySplitTrialSnapshot;

/** Schema-4 personal Echo records are strictly owner-, actor- and generation-bound. */
public final class RealitySplitEchoDuelSnapshotTest {
    private static final UUID EVENT = id(100);
    private static final long ATTEMPT = 81;
    private static final long RUNTIME = 84;

    public static void main(String[] args) {
        validActiveAndCompletedDuelRoundTrip();
        restartRebindPreservesDuelAndRetiresOldActor();
        ownerOfflineEpochDeadlineRoundTrips();
        rejectsMismatchedIdentityAndMissingOrDuplicateOwners();
        rejectsDuplicateDuelAndActorIdentities();
        rejectsInvalidHealthAndReplicaRevision();
        rejectsUnequalInitialOwnerAndEchoHealth();
        rejectsStaleOrUnboundReceipts();
        schemaTwoActiveEchoFailsClosed();
        System.out.println("RealitySplitEchoDuelSnapshotTest OK");
    }

    private static void validActiveAndCompletedDuelRoundTrip() {
        var source = beginEchoAttempt();
        int room = 1;
        UUID owner = source.trials().get(room).owners().iterator().next();
        DuelRecord active = activeDuel(source, room, owner, id(301), id(401), 2);
        source = withDuel(source, room, owner, active);

        Map<String, String> activeEncoded = RealitySplitTrialSnapshot.encodeNamed(source);
        check("4".equals(activeEncoded.get("reality-split-trial.schema")),
                "named checkpoint uses strategy-bound actor schema 4");
        NamedData activeDecoded = RealitySplitTrialSnapshot.decodeNamed(
                activeEncoded, EVENT, ATTEMPT, RUNTIME);
        check(activeDecoded.equals(source), "active Echo actor and allowlisted supplies round trip exactly");
        check(activeDecoded.personalEchoDuels().get(room).get(owner).behaviorStrategy()
                        .equals(active.behaviorStrategy()),
                "owner's bounded Echo strategy survives durable snapshot restore");
        NamedData restarted = RealitySplitTrialSnapshot.decodeNamed(activeEncoded, EVENT, ATTEMPT);
        check(restarted.equals(source),
                "trusted persisted duel keeps its exact actor identity across a Paper restart");
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(activeEncoded, EVENT, ATTEMPT, RUNTIME + 1),
                "active Echo recovery rejects a foreign runtime generation");

        var controller = new RealitySplitTrialController();
        controller.restoreNamed(EVENT, ATTEMPT, ChamberIsolationPolicy.assign(roster()), activeDecoded);
        check(controller.receiptForConfirmedActorDeath(EVENT, ATTEMPT, room, owner, id(999)) == null,
                "a foreign actor cannot mint an Echo death receipt");
        var receipt = controller.receiptForConfirmedActorDeath(EVENT, ATTEMPT, room, owner,
                active.actor().actorUuid());
        check(receipt != null && receipt.deathSerial() == 1,
                "the current persisted actor mints its next death serial");
        check(controller.completePersonalEcho(receipt), "current actor death emits a personal receipt");
        NamedData completed = controller.namedSnapshot();
        check(completed.trials().get(room).echoReceipts().get(owner).equals(receipt),
                "receipt stores duel, current actor, runtime and death serial");
        check(completed.personalEchoDuels().get(room).get(owner).lifecycle() == Lifecycle.COMPLETE,
                "accepted death atomically completes its duel record");
        var completedDecoded = RealitySplitTrialSnapshot.decodeNamed(
                RealitySplitTrialSnapshot.encodeNamed(completed), EVENT, ATTEMPT, RUNTIME);
        check(completedDecoded.equals(completed), "actor-bound completion survives snapshot round trip");
        check(!controller.completePersonalEcho(receipt), "duplicate death receipt cannot complete twice");
        check(controller.receiptForConfirmedActorDeath(EVENT, ATTEMPT, room, owner,
                active.actor().actorUuid()) == null, "completed Echo cannot mint a second receipt");
        check(!controller.completePersonalEcho(EVENT, ATTEMPT, room, owner),
                "legacy owner-only completion remains fail closed");
    }

    private static void restartRebindPreservesDuelAndRetiresOldActor() {
        NamedData source = activeNamedData();
        int room = 1;
        UUID owner = source.personalEchoDuels().get(room).keySet().iterator().next();
        DuelRecord persisted = source.personalEchoDuels().get(room).get(owner);
        var controller = new RealitySplitTrialController();
        controller.restoreNamed(EVENT, ATTEMPT, ChamberIsolationPolicy.assign(roster()), source);

        ActorState replacementActor = new ActorState(id(405), persisted.actor().currentHealth(),
                persisted.actor().maximumHealth(), ActorLifecycle.ALIVE,
                persisted.actor().revision() + 1L, persisted.actor().nextActionTick(),
                persisted.actor().meleeCooldownUntilTick(), persisted.actor().rangedCooldownUntilTick(),
                persisted.actor().pendingUseUntilTick(), persisted.actor().deathSerial());
        DuelRecord rebound = persisted.withActor(persisted.runtimeGeneration() + 1L,
                persisted.ownerStartingHealth(), persisted.ownerMaximumHealth(),
                persisted.returnDeadlineMillis(), replacementActor, persisted.revision() + 1L,
                persisted.replicaRevision(), persisted.replicaState(), persisted.behaviorStrategy());

        check(controller.registerPersonalEchoDuel(rebound),
                "cold restart may bind a replacement carrier under a strictly newer runtime generation");
        DuelRecord restored = controller.namedSnapshot().personalEchoDuels().get(room).get(owner);
        check(restored.actor().actorUuid().equals(replacementActor.actorUuid())
                        && restored.runtimeGeneration() == persisted.runtimeGeneration() + 1L,
                "restart rebind durably points at the replacement actor and runtime");
        check(restored.duelId().equals(persisted.duelId())
                        && restored.ownerStartingHealth() == persisted.ownerStartingHealth()
                        && restored.ownerMaximumHealth() == persisted.ownerMaximumHealth()
                        && restored.actor().currentHealth() == persisted.actor().currentHealth()
                        && restored.replicaState().equals(persisted.replicaState())
                        && restored.behaviorStrategy().equals(persisted.behaviorStrategy()),
                "restart rebind preserves duel, owner health, current Echo health, equipment and strategy");
        check(controller.receiptForConfirmedActorDeath(EVENT, ATTEMPT, room, owner,
                persisted.actor().actorUuid()) == null,
                "pre-restart actor cannot complete a rebound duel");
        check(controller.receiptForConfirmedActorDeath(EVENT, ATTEMPT, room, owner,
                replacementActor.actorUuid()) != null,
                "only replacement actor can produce the resumed duel receipt");

        NamedData reboundSnapshot = RealitySplitTrialSnapshot.decodeNamed(
                RealitySplitTrialSnapshot.encodeNamed(controller.namedSnapshot()), EVENT, ATTEMPT);
        check(reboundSnapshot.equals(controller.namedSnapshot()),
                "replacement actor binding survives another process restart");
    }

    private static void ownerOfflineEpochDeadlineRoundTrips() {
        NamedData source = beginEchoAttempt();
        int room = 1;
        UUID owner = source.trials().get(room).owners().iterator().next();
        DuelRecord active = activeDuel(source, room, owner, id(302), id(402), 2);
        long epochDeadlineMillis = 1_800_000_000_000L;
        long firstDisconnectAt = epochDeadlineMillis - 120_000L;
        long initializedDeadline = RealitySplitEchoDuelState.ownerOfflineDeadlineMillis(
                active.returnDeadlineMillis(), firstDisconnectAt, 120_000L).orElseThrow();
        check(initializedDeadline == epochDeadlineMillis,
                "first disconnect initializes the expected grace deadline");
        DuelRecord offline = active.ownerOffline(initializedDeadline, active.revision() + 1);
        source = withDuel(source, room, owner, offline);

        Map<String, String> encoded = RealitySplitTrialSnapshot.encodeNamed(source);
        NamedData decoded = RealitySplitTrialSnapshot.decodeNamed(encoded, EVENT, ATTEMPT, RUNTIME);
        check(decoded.equals(source), "epoch-millis owner return deadline round trips without counter truncation");
        check(RealitySplitTrialSnapshot.decodeNamed(encoded, EVENT, ATTEMPT).equals(source),
                "offline owner return deadline survives a Paper restart unchanged");

        ActorState resumedActor = new ActorState(id(403), offline.actor().currentHealth(),
                offline.actor().maximumHealth(), ActorLifecycle.ALIVE, offline.actor().revision() + 1L,
                offline.actor().nextActionTick(), offline.actor().meleeCooldownUntilTick(),
                offline.actor().rangedCooldownUntilTick(), offline.actor().pendingUseUntilTick(),
                offline.actor().deathSerial());
        DuelRecord resumed = offline.withActor(offline.runtimeGeneration() + 1L,
                offline.ownerStartingHealth(), offline.ownerMaximumHealth(), offline.returnDeadlineMillis(),
                resumedActor, offline.revision() + 1L, offline.replicaRevision(), offline.replicaState(),
                offline.behaviorStrategy());
        long secondDisconnectAt = firstDisconnectAt + 30_000L;
        long preservedDeadline = RealitySplitEchoDuelState.ownerOfflineDeadlineMillis(
                resumed.returnDeadlineMillis(), secondDisconnectAt, 120_000L).orElseThrow();
        DuelRecord offlineAgain = resumed.ownerOffline(preservedDeadline, resumed.revision() + 1L);
        check(offlineAgain.returnDeadlineMillis() == epochDeadlineMillis,
                "reconnect and repeated disconnect preserve the original persisted deadline");
        expectRejected(() -> resumed.ownerOffline(secondDisconnectAt + 120_000L,
                resumed.revision() + 1L),
                "a restarted grace window is rejected instead of extending the original deadline");
        check(RealitySplitTrialSnapshot.decodeNamed(
                        RealitySplitTrialSnapshot.encodeNamed(withDuel(source, room, owner, offlineAgain)),
                        EVENT, ATTEMPT).personalEchoDuels().get(room).get(owner)
                        .returnDeadlineMillis() == epochDeadlineMillis,
                "the repeated-disconnect deadline remains stable after snapshot persistence");

        expectRejected(() -> new DuelRecord(offline.eventId(), offline.attemptGeneration(),
                offline.runtimeGeneration(), offline.room(), offline.duelId(), owner, Lifecycle.OWNER_OFFLINE,
                offline.ownerStartingHealth(), offline.ownerMaximumHealth(), 253_402_300_800_000L,
                offline.revision(), offline.actor(), offline.replicaRevision(), offline.replicaState(),
                offline.behaviorStrategy()),
                "owner return deadline beyond supported epoch-millis range is rejected");
        expectRejected(() -> new DuelRecord(offline.eventId(), offline.attemptGeneration(),
                offline.runtimeGeneration(), offline.room(), offline.duelId(), owner, Lifecycle.OWNER_OFFLINE,
                offline.ownerStartingHealth(), offline.ownerMaximumHealth(), 90_000L,
                offline.revision(), offline.actor(), offline.replicaRevision(), offline.replicaState(),
                offline.behaviorStrategy()),
                "tick-like owner return deadline is rejected instead of being treated as epoch milliseconds");
    }

    private static void rejectsMismatchedIdentityAndMissingOrDuplicateOwners() {
        NamedData data = activeNamedData();
        var encoded = new LinkedHashMap<>(RealitySplitTrialSnapshot.encodeNamed(data));
        encoded.put("reality-split-trial.event", id(999).toString());
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(encoded, EVENT, ATTEMPT, RUNTIME),
                "foreign event fails closed");

        int room = 1;
        Map<UUID, DuelRecord> original = data.personalEchoDuels().get(room);
        UUID owner = original.keySet().iterator().next();
        var missingOwner = new LinkedHashMap<>(data.personalEchoDuels());
        missingOwner.put(room, Map.of());
        expectRejected(() -> namedWithDuels(data, missingOwner), "missing owner duel fails closed");

        UUID foreignOwner = id(888);
        DuelRecord record = original.get(owner);
        var duplicateOwner = new LinkedHashMap<>(original);
        duplicateOwner.put(foreignOwner, new DuelRecord(record.eventId(), record.attemptGeneration(),
                record.runtimeGeneration(), record.room(), record.duelId(), owner, record.lifecycle(),
                record.ownerStartingHealth(), record.ownerMaximumHealth(), record.returnDeadlineMillis(),
                record.revision(), record.actor(), record.replicaRevision(), record.replicaState(),
                record.behaviorStrategy()));
        var duplicateOwners = new LinkedHashMap<>(data.personalEchoDuels());
        duplicateOwners.put(room, duplicateOwner);
        expectRejected(() -> namedWithDuels(data, duplicateOwners),
                "two records for one owner fail closed");

        expectRejected(() -> new DuelRecord(record.eventId(), record.attemptGeneration(),
                record.runtimeGeneration(), record.room(), record.duelId(), owner, Lifecycle.ACTIVE,
                record.ownerStartingHealth(), record.ownerMaximumHealth(), record.returnDeadlineMillis(),
                record.revision(), null, record.replicaRevision(), record.replicaState(),
                record.behaviorStrategy()),
                "active duel without its actor fails closed");
    }

    private static void rejectsDuplicateDuelAndActorIdentities() {
        final NamedData data = activeNamedData();
        int room = 1;
        var owners = new ArrayList<>(data.trials().get(room).owners());
        check(owners.size() == 2, "fixture has two owners in its Echo room");
        UUID firstOwner = owners.get(0);
        UUID secondOwner = owners.get(1);
        DuelRecord first = data.personalEchoDuels().get(room).get(firstOwner);
        DuelRecord second = data.personalEchoDuels().get(room).get(secondOwner);
        var roomDuels = new LinkedHashMap<>(data.personalEchoDuels().get(room));
        roomDuels.put(secondOwner, activeDuel(data, room, secondOwner, first.duelId(),
                second.actor().actorUuid(), 3));
        var duplicateDuel = new LinkedHashMap<>(data.personalEchoDuels());
        duplicateDuel.put(room, roomDuels);
        expectRejected(() -> namedWithDuels(data, duplicateDuel), "duplicate duel ID fails closed");

        var duplicateActor = new LinkedHashMap<>(data.personalEchoDuels());
        var actorDuels = new LinkedHashMap<>(data.personalEchoDuels().get(room));
        ActorState clonedActor = new ActorState(first.actor().actorUuid(), second.actor().currentHealth(),
                second.actor().maximumHealth(), ActorLifecycle.ALIVE, second.actor().revision(),
                second.actor().nextActionTick(), second.actor().meleeCooldownUntilTick(),
                second.actor().rangedCooldownUntilTick(), second.actor().pendingUseUntilTick(), 0);
        actorDuels.put(secondOwner, new DuelRecord(second.eventId(), second.attemptGeneration(),
                second.runtimeGeneration(), room, second.duelId(), secondOwner, second.lifecycle(),
                second.ownerStartingHealth(), second.ownerMaximumHealth(), second.returnDeadlineMillis(),
                second.revision(), clonedActor, second.replicaRevision(), second.replicaState(),
                second.behaviorStrategy()));
        duplicateActor.put(room, actorDuels);
        expectRejected(() -> namedWithDuels(data, duplicateActor), "duplicate actor UUID fails closed");
    }

    private static void rejectsInvalidHealthAndReplicaRevision() {
        NamedData data = activeNamedData();
        int room = 1;
        UUID owner = data.personalEchoDuels().get(room).keySet().iterator().next();
        DuelRecord record = data.personalEchoDuels().get(room).get(owner);
        expectRejected(() -> new ActorState(record.actor().actorUuid(), Double.NaN, 20,
                ActorLifecycle.ALIVE, 0, 0, 0, 0, 0, 0), "NaN actor health rejected");
        expectRejected(() -> new ActorState(record.actor().actorUuid(), 21, 20,
                ActorLifecycle.ALIVE, 0, 0, 0, 0, 0, 0), "actor health above maximum rejected");
        expectRejected(() -> new ActorState(record.actor().actorUuid(), -1, 20,
                ActorLifecycle.ALIVE, 0, 0, 0, 0, 0, 0), "negative actor health rejected");
        expectRejected(() -> new ActorState(record.actor().actorUuid(), 10, 20,
                ActorLifecycle.ALIVE, -1, 0, 0, 0, 0, 0), "negative actor revision rejected");
        expectRejected(() -> new DuelRecord(record.eventId(), record.attemptGeneration(),
                record.runtimeGeneration(), record.room(), record.duelId(), owner, record.lifecycle(),
                record.ownerStartingHealth(), record.ownerMaximumHealth(), record.returnDeadlineMillis(),
                record.revision(), record.actor(), record.replicaRevision() + 1, record.replicaState(),
                record.behaviorStrategy()),
                "replica revision mismatch rejected");
        expectRejected(() -> new DuelRecord(record.eventId(), record.attemptGeneration(),
                record.runtimeGeneration(), record.room(), record.duelId(), owner, record.lifecycle(),
                record.ownerStartingHealth(), record.ownerMaximumHealth() + 1.0D, record.returnDeadlineMillis(),
                record.revision(), record.actor(), record.replicaRevision(), record.replicaState(),
                record.behaviorStrategy()),
                "Echo maximum health must match its owner's persisted maximum health");
        expectRejected(() -> new DuelRecord(record.eventId(), record.attemptGeneration(),
                record.runtimeGeneration(), record.room(), record.duelId(), owner, record.lifecycle(),
                record.ownerStartingHealth(), record.ownerMaximumHealth(), record.returnDeadlineMillis(),
                record.revision(), record.actor(), record.replicaRevision(), record.replicaState(), null),
                "active Echo cannot start without a captured owner profile strategy");
    }

    private static void rejectsUnequalInitialOwnerAndEchoHealth() {
        NamedData planned = beginEchoAttempt();
        var controller = new RealitySplitTrialController();
        controller.restoreNamed(EVENT, ATTEMPT, ChamberIsolationPolicy.assign(roster()), planned);
        int room = 1;
        UUID owner = planned.trials().get(room).owners().iterator().next();
        UUID plannedDuelId = planned.personalEchoDuels().get(room).get(owner).duelId();
        DuelRecord valid = activeDuel(planned, room, owner, plannedDuelId, id(403), 2);
        ActorState underfilledActor = new ActorState(valid.actor().actorUuid(),
                valid.actor().currentHealth() - 1.0D, valid.actor().maximumHealth(),
                ActorLifecycle.ALIVE, valid.actor().revision(), valid.actor().nextActionTick(),
                valid.actor().meleeCooldownUntilTick(), valid.actor().rangedCooldownUntilTick(),
                valid.actor().pendingUseUntilTick(), valid.actor().deathSerial());
        DuelRecord mismatched = new DuelRecord(valid.eventId(), valid.attemptGeneration(),
                valid.runtimeGeneration(), valid.room(), valid.duelId(), owner, valid.lifecycle(),
                valid.ownerStartingHealth(), valid.ownerMaximumHealth(), valid.returnDeadlineMillis(),
                valid.revision(), underfilledActor, valid.replicaRevision(), valid.replicaState(),
                valid.behaviorStrategy());

        check(!controller.registerPersonalEchoDuel(mismatched),
                "the initial Echo actor must start at its owner's captured health");
        check(controller.namedSnapshot().personalEchoDuels().get(room).get(owner).lifecycle() == Lifecycle.PLANNED,
                "a health-mismatched actor cannot replace the planned duel");

        var active = activeDuel(planned, room, owner, plannedDuelId, id(404), 3);
        check(controller.registerPersonalEchoDuel(active), "valid owner and Echo health profile starts once");
        var damagedActor = new ActorState(active.actor().actorUuid(), 19.0D,
                active.actor().maximumHealth(), ActorLifecycle.ALIVE, active.actor().revision() + 1,
                active.actor().nextActionTick(), active.actor().meleeCooldownUntilTick(),
                active.actor().rangedCooldownUntilTick(), active.actor().pendingUseUntilTick(), 0);
        var damaged = new DuelRecord(active.eventId(), active.attemptGeneration(),
                active.runtimeGeneration(), active.room(), active.duelId(), owner, active.lifecycle(),
                active.ownerStartingHealth(), active.ownerMaximumHealth(), active.returnDeadlineMillis(),
                active.revision() + 1, damagedActor, active.replicaRevision(), active.replicaState(),
                active.behaviorStrategy());
        check(controller.registerPersonalEchoDuel(damaged),
                "combat may change Echo current health while preserving owner baseline and maximum-health parity");
        var changedCurrentHealthActor = new ActorState(damaged.actor().actorUuid(), 19.0D,
                damaged.actor().maximumHealth(), ActorLifecycle.ALIVE, damaged.actor().revision() + 1,
                damaged.actor().nextActionTick(), damaged.actor().meleeCooldownUntilTick(),
                damaged.actor().rangedCooldownUntilTick(), damaged.actor().pendingUseUntilTick(), 0);
        var changedOwnerStartingHealth = new DuelRecord(damaged.eventId(), damaged.attemptGeneration(),
                damaged.runtimeGeneration(), damaged.room(), damaged.duelId(), owner, damaged.lifecycle(),
                19.0D, damaged.ownerMaximumHealth(), damaged.returnDeadlineMillis(), damaged.revision() + 1,
                changedCurrentHealthActor, damaged.replicaRevision(), damaged.replicaState(), damaged.behaviorStrategy());
        check(!controller.registerPersonalEchoDuel(changedOwnerStartingHealth),
                "later checkpoint cannot rewrite the owner's captured starting health to match a damaged Echo");

        var changedMaximumActor = new ActorState(damaged.actor().actorUuid(), damaged.actor().currentHealth(),
                40.0D, ActorLifecycle.ALIVE, damaged.actor().revision() + 1,
                active.actor().nextActionTick(), active.actor().meleeCooldownUntilTick(),
                active.actor().rangedCooldownUntilTick(), active.actor().pendingUseUntilTick(), 0);
        var changedMaximumHealth = new DuelRecord(damaged.eventId(), damaged.attemptGeneration(),
                damaged.runtimeGeneration(), damaged.room(), damaged.duelId(), owner, damaged.lifecycle(),
                damaged.ownerStartingHealth(), 40.0D, damaged.returnDeadlineMillis(), damaged.revision() + 1,
                changedMaximumActor, damaged.replicaRevision(), damaged.replicaState(), damaged.behaviorStrategy());
        check(!controller.registerPersonalEchoDuel(changedMaximumHealth),
                "later checkpoint cannot change owner and Echo maximum-health parity together");
        check(controller.namedSnapshot().personalEchoDuels().get(room).get(owner).equals(damaged),
                "rejected owner/Echo health rewrites preserve the accepted duel checkpoint");
    }

    private static void rejectsStaleOrUnboundReceipts() {
        NamedData completed = completedNamedData();
        int room = 1;
        NamedTrialState state = completed.trials().get(room);
        UUID owner = state.echoReceipts().keySet().iterator().next();
        EchoCompletionReceipt receipt = state.echoReceipts().get(owner);
        var staleStates = new LinkedHashMap<>(completed.trials());
        var badReceipts = new LinkedHashMap<>(state.echoReceipts());
        badReceipts.put(owner, new EchoCompletionReceipt(EVENT, ATTEMPT, RUNTIME - 1,
                room, receipt.duelId(), owner, receipt.actorUuid(), receipt.deathSerial()));
        staleStates.put(room, new NamedTrialState(room, NamedTrial.PERSONAL_ECHO, Stage.COMPLETE,
                state.owners(), badReceipts));
        expectRejected(() -> namedWithTrials(completed, staleStates), "stale runtime receipt rejected");
        badReceipts = new LinkedHashMap<>(state.echoReceipts());
        badReceipts.put(owner, new EchoCompletionReceipt(EVENT, ATTEMPT, RUNTIME,
                room, receipt.duelId(), owner, id(777), receipt.deathSerial()));
        staleStates.put(room, new NamedTrialState(room, NamedTrial.PERSONAL_ECHO, Stage.COMPLETE,
                state.owners(), badReceipts));
        expectRejected(() -> namedWithTrials(completed, staleStates), "receipt for another actor rejected");
        badReceipts = new LinkedHashMap<>(state.echoReceipts());
        badReceipts.put(owner, new EchoCompletionReceipt(EVENT, ATTEMPT, RUNTIME,
                room, receipt.duelId(), owner, receipt.actorUuid(), receipt.deathSerial() + 1));
        staleStates.put(room, new NamedTrialState(room, NamedTrial.PERSONAL_ECHO, Stage.COMPLETE,
                state.owners(), badReceipts));
        expectRejected(() -> namedWithTrials(completed, staleStates), "wrong death serial rejected");
    }

    private static void schemaTwoActiveEchoFailsClosed() {
        var encoded = new LinkedHashMap<>(RealitySplitTrialSnapshot.encodeNamed(activeNamedData()));
        encoded.put("reality-split-trial.schema", "2");
        encoded.keySet().removeIf(key -> key.startsWith("reality-split-trial.echo."));
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(encoded, EVENT, ATTEMPT, RUNTIME),
                "schema-2 active Echo snapshot cannot synthesize actor identity or completion");
    }

    private static NamedData activeNamedData() {
        NamedData source = beginEchoAttempt();
        int room = 1;
        long identity = 300;
        for (UUID owner : source.trials().get(room).owners().stream().sorted().toList()) {
            source = withDuel(source, room, owner,
                    activeDuel(source, room, owner, id(identity), id(identity + 100), 2));
            identity++;
        }
        return source;
    }

    private static NamedData completedNamedData() {
        NamedData source = activeNamedData();
        var controller = new RealitySplitTrialController();
        controller.restoreNamed(EVENT, ATTEMPT, ChamberIsolationPolicy.assign(roster()), source);
        int room = 1;
        for (DuelRecord duel : source.personalEchoDuels().get(room).values()) {
            controller.completePersonalEcho(new EchoCompletionReceipt(EVENT, ATTEMPT, RUNTIME, room,
                    duel.duelId(), duel.ownerUuid(), duel.actor().actorUuid(), 1));
        }
        return controller.namedSnapshot();
    }

    private static NamedData beginEchoAttempt() {
        var controller = new RealitySplitTrialController();
        controller.beginNamed(EVENT, ATTEMPT, ChamberIsolationPolicy.assign(roster()), List.of(
                NamedTrial.WARDEN, NamedTrial.PERSONAL_ECHO,
                NamedTrial.MOBILE_MARKSMAN, NamedTrial.RIFT_ARCHMAGE));
        return controller.namedSnapshot();
    }

    private static DuelRecord activeDuel(NamedData source, int room, UUID owner,
                                          UUID duelId, UUID actorId, int heldMaterialIndex) {
        var items = new ArrayList<EchoLoadoutState.Item>();
        for (int slot = 0; slot < 41; slot++) items.add(EchoLoadoutState.Item.empty());
        items.set(heldMaterialIndex, new EchoLoadoutState.Item(
                "IRON_SWORD", 1, 250, 0, Map.of(), "", false));
        var loadout = EchoLoadoutState.create(EVENT, ATTEMPT, duelId, owner, items, heldMaterialIndex);
        ActorState actor = new ActorState(actorId, 18.0, 20.0, ActorLifecycle.ALIVE,
                3, 20, 30, 50, 0, 0);
        return new DuelRecord(EVENT, ATTEMPT, RUNTIME, room, duelId, owner, Lifecycle.ACTIVE,
                18.0, 20.0, 0, 3, actor, loadout.revision(), loadout.encode(), profile());
    }

    private static EchoBehaviorStrategy profile() {
        return EchoBehaviorStrategy.from(new EchoBehaviorStrategy.ProfileCounts(
                0, 0, 0, 0, 0, 0, 0, true, 0.0D));
    }

    private static NamedData withDuel(NamedData data, int room, UUID owner, DuelRecord duel) {
        var rooms = new LinkedHashMap<>(data.personalEchoDuels());
        var duels = new LinkedHashMap<>(rooms.get(room));
        duels.put(owner, duel);
        rooms.put(room, duels);
        return namedWithDuels(data, rooms);
    }

    private static NamedData namedWithDuels(NamedData data,
            Map<Integer, Map<UUID, DuelRecord>> duels) {
        return new NamedData(data.eventId(), data.generation(), data.selectedLayout(), data.trials(), duels);
    }

    private static NamedData namedWithTrials(NamedData data, Map<Integer, NamedTrialState> trials) {
        return new NamedData(data.eventId(), data.generation(), data.selectedLayout(), trials,
                data.personalEchoDuels());
    }

    private static List<UUID> roster() {
        return List.of(id(1), id(2), id(3), id(4), id(5), id(6), id(7), id(8));
    }

    private static UUID id(long value) { return new UUID(0, value); }

    private static void expectRejected(Runnable operation, String message) {
        try { operation.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
