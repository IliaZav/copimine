import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState;
import me.copimine.endevent.runtime.RealitySplitTrialController;
import me.copimine.endevent.runtime.RealitySplitTrialSnapshot;

public final class RealitySplitTrialSnapshotTest {
    public static void main(String[] args) {
        List<UUID> players = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"));
        RealitySplitTrialController source = new RealitySplitTrialController();
        source.begin(73L, ChamberIsolationPolicy.assign(players));
        source.hitReflectionSeal(73L, 1, 0, true);
        source.hitJuggernautAnchor(73L, 2, 0, true);

        Map<String, String> encoded = RealitySplitTrialSnapshot.encode(73L, source.snapshot());
        RealitySplitTrialSnapshot.Data decoded = RealitySplitTrialSnapshot.decode(encoded, 73L);
        check(decoded.trials().get(1).trial() == RealitySplitTrialController.Trial.RIFT_REFLECTION,
                "snapshot keeps the assigned trial identity");
        check(decoded.trials().get(1).progress() == 1,
                "snapshot keeps the active Reflection seal index");
        check(decoded.trials().get(2).progress() == 1,
                "snapshot keeps the active Juggernaut armor index");

        RealitySplitTrialController restored = new RealitySplitTrialController();
        restored.restore(73L, ChamberIsolationPolicy.assign(players), decoded.trials());
        check(restored.trial(1).progress() == 1 && restored.trial(2).progress() == 1,
                "restore rehydrates in-progress trials for the same generation");

        boolean staleRejected = false;
        try {
            RealitySplitTrialSnapshot.decode(encoded, 74L);
        } catch (IllegalArgumentException expected) {
            staleRejected = true;
        }
        check(staleRejected, "a stale snapshot cannot be restored into a later generation");

        boolean malformedRejected = false;
        try {
            RealitySplitTrialSnapshot.decode(Map.of(
                    "reality-split-trial.generation", "73",
                    "reality-split-trial.0", "RIFT_HUNTER:ACTIVE:99"), 73L);
        } catch (IllegalArgumentException expected) {
            malformedRejected = true;
        }
        check(malformedRejected, "invalid trial progress is rejected instead of clamped");
        var wrongLegacyIdentity = new java.util.LinkedHashMap<>(encoded);
        wrongLegacyIdentity.put("reality-split-trial.room.1", "WARDEN:ACTIVE:0:1");
        expectRejected(() -> RealitySplitTrialSnapshot.decode(wrongLegacyIdentity, 73L),
                "legacy room identity must be validated before publishing decoded data");
        var sparseLegacy = new java.util.LinkedHashMap<>(encoded);
        sparseLegacy.remove("reality-split-trial.room.1");
        expectRejected(() -> RealitySplitTrialSnapshot.decode(sparseLegacy, 73L),
                "legacy snapshots cannot skip an assigned room");
        namedSnapshots(players, encoded);
        System.out.println("RealitySplitTrialSnapshotTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void namedSnapshots(List<UUID> players, Map<String, String> legacy) {
        UUID event = UUID.fromString("00000000-0000-0000-0000-000000000099");
        var assignment = ChamberIsolationPolicy.assign(players);
        var source = new RealitySplitTrialController();
        var layout = List.of(RealitySplitTrialController.NamedTrial.RIFT_ARCHMAGE,
                RealitySplitTrialController.NamedTrial.PERSONAL_ECHO,
                RealitySplitTrialController.NamedTrial.MOBILE_MARKSMAN);
        source.beginNamed(event, 81L, assignment, layout);
        UUID owner = assignment.playersIn(1).iterator().next();
        var planned = source.namedSnapshot().personalEchoDuels().get(1).get(owner);
        var items = new ArrayList<EchoLoadoutState.Item>();
        for (int slot = 0; slot < 41; slot++) items.add(EchoLoadoutState.Item.empty());
        items.set(2, new EchoLoadoutState.Item("IRON_SWORD", 1, 250, 0, Map.of(), "", false));
        var loadout = EchoLoadoutState.create(event, 81L, planned.duelId(), owner, items, 2);
        var actor = new RealitySplitEchoDuelState.ActorState(UUID.fromString(
                "00000000-0000-0000-0000-000000000404"), 20.0, 20.0,
                RealitySplitEchoDuelState.ActorLifecycle.ALIVE, 1, 20, 30, 40, 0, 0);
        var duel = new RealitySplitEchoDuelState.DuelRecord(event, 81L, 93L, 1,
                planned.duelId(), owner, RealitySplitEchoDuelState.Lifecycle.ACTIVE,
                20.0, 20.0, 0, 1, actor, loadout.revision(), loadout.encode(),
                EchoBehaviorStrategy.from(new EchoBehaviorStrategy.ProfileCounts(
                        0, 0, 0, 0, 0, 0, 0, true, 0.0D)));
        check(source.registerPersonalEchoDuel(duel), "owner's active Echo actor is persisted");
        var receipt = new RealitySplitTrialController.EchoCompletionReceipt(event, 81L, 93L, 1,
                duel.duelId(), owner, actor.actorUuid(), 1);
        check(source.completePersonalEcho(receipt), "current Echo actor death completes its owner duel");
        Map<String, String> encoded = RealitySplitTrialSnapshot.encodeNamed(source.namedSnapshot());
        var decoded = RealitySplitTrialSnapshot.decodeNamed(encoded, event, 81L);
        check(decoded.equals(source.namedSnapshot()), "named codec preserves exact layout, owners, stages and receipts");
        check("5".equals(encoded.get("reality-split-trial.schema")), "Echo action deadlines use named schema 5");
        check(decoded.selectedLayout().equals(layout), "restore never returns default room order or rerolls");
        check(decoded.trials().get(1).echoReceipts().get(owner).eventId().equals(event), "receipt retains owning event");
        check(decoded.trials().get(1).echoReceipts().get(owner).generation() == 81L, "receipt retains generation");
        check(decoded.trials().get(1).echoReceipts().get(owner).actorUuid().equals(actor.actorUuid()),
                "receipt retains the exact personal Echo actor");
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(legacy, event, 73L),
                "schema-1 never migrates historical actors into new approved actors");
        var unversioned = new java.util.LinkedHashMap<>(legacy);
        unversioned.remove("reality-split-trial.schema");
        unversioned.remove("reality-split-trial.layout");
        check(RealitySplitTrialSnapshot.decode(unversioned, 73L).trials().get(1).trial()
                == RealitySplitTrialController.Trial.RIFT_REFLECTION, "unversioned history retains true legacy identity");
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(unversioned, event, 73L), "unversioned legacy fails closed");
        expectRejected(() -> RealitySplitTrialSnapshot.decode(encoded, 81L), "legacy decoder cannot read named schema");
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(encoded, event, 82L), "stale named generation fails closed");
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(encoded, players.get(0), 81L), "foreign event fails closed");

        Map<String, String> mutations = Map.of(
                "reality-split-trial.schema", "1",
                "reality-split-trial.layout", "legacy-four-trials",
                "reality-split-trial.selected", "RIFT_ARCHMAGE,PERSONAL_ECHO,RIFT_ARCHMAGE",
                "reality-split-trial.room.2", "JUGGERNAUT:ACTIVE:" + players.get(2) + ":",
                "reality-split-trial.room.1", "PERSONAL_ECHO:COMPLETE:" + owner + ":");
        mutations.forEach((key, value) -> {
            var invalid = new java.util.LinkedHashMap<>(encoded);
            invalid.put(key, value);
            expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(invalid, event, 81L), "malformed named field " + key);
        });
        var missing = new java.util.LinkedHashMap<>(encoded);
        missing.remove("reality-split-trial.room.2");
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(missing, event, 81L), "missing final room fails closed");
        var extra = new java.util.LinkedHashMap<>(encoded);
        extra.put("reality-split-trial.room.03", "RIFT_ARCHMAGE:ACTIVE:" + owner + ":");
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(extra, event, 81L), "unknown and aliased room keys fail closed");
        var foreignOwner = new java.util.LinkedHashMap<>(encoded);
        foreignOwner.put("reality-split-trial.room.1", "PERSONAL_ECHO:COMPLETE:" + owner + ":" + players.get(0));
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(foreignOwner, event, 81L), "foreign owner receipt fails closed");
        var duplicateOwner = new java.util.LinkedHashMap<>(encoded);
        duplicateOwner.put("reality-split-trial.room.2", "MOBILE_MARKSMAN:ACTIVE:" + owner + ":");
        expectRejected(() -> RealitySplitTrialSnapshot.decodeNamed(duplicateOwner, event, 81L), "duplicate ownership fails closed");
        var staleReceipt = new RealitySplitTrialController.EchoCompletionReceipt(event, 80L, 81L, 1,
                duel.duelId(), owner, actor.actorUuid(), 1L);
        var staleStates = new java.util.LinkedHashMap<>(decoded.trials());
        staleStates.put(1, new RealitySplitTrialController.NamedTrialState(1,
                RealitySplitTrialController.NamedTrial.PERSONAL_ECHO, RealitySplitTrialController.Stage.COMPLETE,
                java.util.Set.of(owner), Map.of(owner, staleReceipt)));
        expectRejected(() -> new RealitySplitTrialController.NamedData(event, 81L, layout, staleStates,
                        decoded.personalEchoDuels()),
                "whole snapshot rejects stale receipt before controller publication");
    }

    private static void expectRejected(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
}
