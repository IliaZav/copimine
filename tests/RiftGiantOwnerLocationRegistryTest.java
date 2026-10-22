import me.copimine.endevent.runtime.RiftGiantOwnerLocationRegistry;
import me.copimine.endevent.runtime.WaveMobLifecycleRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Comparator;
import java.util.UUID;

public final class RiftGiantOwnerLocationRegistryTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("rift-giant-owner-ledger-");
        Path ledgerFile = directory.resolve("rift-giant-owner-locations.properties");
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        UUID staleSummonChunkWorldId = worldId;
        String eventId = "event-2026-10-22";
        long generation = 17L;

        var beforeRestart = new RiftGiantOwnerLocationRegistry(ledgerFile);
        beforeRestart.recordUnloadedOwner(ownerId, eventId, generation, 3,
                worldId, 40, -12);

        // The summon was saved in chunk A before its owner moved to chunk B.
        // Reloading A first must not treat A as evidence that the owner died.
        var afterRestart = new RiftGiantOwnerLocationRegistry(ledgerFile);
        check(!afterRestart.mayRetireAbsentOwner(ownerId, eventId, generation,
                        false, staleSummonChunkWorldId, 3, -12, true),
                "a stale summon chunk cannot reconcile the owner's newer unloaded chunk");
        check(!afterRestart.mayRetireAbsentOwner(ownerId, eventId, generation,
                        false, worldId, 40, -12, false),
                "a loaded world position is insufficient without the actual chunk-load event");
        check(afterRestart.mayRetireAbsentOwner(ownerId, eventId, generation,
                        false, worldId, 40, -12, true),
                "the exact last-known owner chunk can reconcile an absent owner after restart");

        check(!afterRestart.mayRetireAbsentOwner(ownerId, eventId, generation,
                        true, worldId, 40, -12, true),
                "a loaded owner always preserves its summons");
        afterRestart.clear(ownerId, eventId, generation);
        var afterOwnerReload = new RiftGiantOwnerLocationRegistry(ledgerFile);
        check(!afterOwnerReload.mayRetireAbsentOwner(ownerId, eventId, generation,
                        false, worldId, 40, -12, true),
                "reloading a live owner clears its old unload location durably");

        afterOwnerReload.recordDeadOwner(ownerId, eventId, generation, 3);
        var afterDeathRestart = new RiftGiantOwnerLocationRegistry(ledgerFile);
        check(afterDeathRestart.mayRetireAbsentOwner(ownerId, eventId, generation,
                        false, staleSummonChunkWorldId, 3, -12, true),
                "a confirmed owner death remains authoritative across restart");
        check(!afterDeathRestart.mayRetireAbsentOwner(ownerId, "another-event", generation,
                        false, worldId, 40, -12, true),
                "owner evidence cannot cross event ownership");
        check(!afterDeathRestart.mayRetireAbsentOwner(ownerId, eventId, generation + 1,
                        false, worldId, 40, -12, true),
                "owner evidence cannot cross event generations");
        UUID oldSessionOwner = UUID.randomUUID();
        afterDeathRestart.recordDeadOwner(oldSessionOwner, "old-event", 3L, 3);
        afterDeathRestart.retainSession(eventId, generation);
        var afterSessionRetention = new RiftGiantOwnerLocationRegistry(ledgerFile);
        check(afterSessionRetention.find(oldSessionOwner, "old-event", 3L).isEmpty(),
                "new valid event snapshots prune obsolete owner evidence");
        check(afterSessionRetention.find(ownerId, eventId, generation).isPresent(),
                "session pruning preserves the current event evidence");

        UUID movedOwner = UUID.randomUUID();
        var movedOwnerBeforeRestart = new RiftGiantOwnerLocationRegistry(ledgerFile);
        movedOwnerBeforeRestart.recordUnloadedOwner(movedOwner, eventId, generation, 3,
                worldId, 40, -12);
        var originalWaveRoster = new WaveMobLifecycleRegistry();
        originalWaveRoster.onUnload(movedOwner, eventId, generation, 3);
        var movedOwnerAfterRestart = new RiftGiantOwnerLocationRegistry(ledgerFile);
        var restoredWaveRoster = new WaveMobLifecycleRegistry();
        for (var unloadedOwner : movedOwnerAfterRestart.unloadedOwners(eventId, generation)) {
            restoredWaveRoster.onUnload(unloadedOwner.ownerId(), eventId,
                    unloadedOwner.generation(), unloadedOwner.wave());
        }
        check(restoredWaveRoster.count(eventId, generation, 3) == 1,
                "a moved unloaded giant remains in the outstanding-wave count after restart");
        check(!movedOwnerAfterRestart.mayRetireAbsentOwner(movedOwner, eventId, generation,
                        false, worldId, 3, -12, true),
                "loading summon chunk A first does not treat stale A as the owner's chunk B");
        check(restoredWaveRoster.count(eventId, generation, 3) == 1,
                "loading stale summon chunk A does not release the owner's wave counter");
        check(!movedOwnerAfterRestart.mayRetireAbsentOwner(movedOwner, eventId, generation,
                        true, worldId, 40, -12, true),
                "loading chunk B with the moved owner first keeps the summon alive");
        movedOwnerAfterRestart.clear(movedOwner, eventId, generation);
        restoredWaveRoster.onLoad(movedOwner, eventId, generation, 3, true,
                eventId, generation, 3);
        check(restoredWaveRoster.count(eventId, generation, 3) == 0,
                "owner reload moves its identity from the unload fence to the live index");

        UUID deadOwner = UUID.randomUUID();
        movedOwnerAfterRestart.recordUnloadedOwner(deadOwner, eventId, generation, 3,
                worldId, 40, -12);
        var deathRecovery = new WaveMobLifecycleRegistry();
        deathRecovery.onUnload(deadOwner, eventId, generation, 3);
        movedOwnerAfterRestart.recordDeadOwner(deadOwner, eventId, generation, 3);
        var afterDeathRestartLedger = new RiftGiantOwnerLocationRegistry(ledgerFile);
        check(afterDeathRestartLedger.mayRetireAbsentOwner(deadOwner, eventId, generation,
                        false, worldId, 3, -12, false),
                "confirmed owner death reconciles its summon after another restart");
        deathRecovery.onDeath(deadOwner);
        check(deathRecovery.count(eventId, generation, 3) == 0,
                "confirmed owner death releases the unloaded wave counter exactly once");

        Path budgetFile = directory.resolve("rift-giant-summon-budget.properties");
        UUID firstGiant = UUID.randomUUID();
        UUID secondGiant = UUID.randomUUID();
        UUID thirdGiant = UUID.randomUUID();
        var activeBudget = new RiftGiantOwnerLocationRegistry(budgetFile);
        activeBudget.recordLoadedOwner(firstGiant, eventId, generation, 4,
                worldId, 10, 20, 3);
        activeBudget.recordLoadedOwner(secondGiant, eventId, generation, 4,
                worldId, 11, 20, 3);
        activeBudget.recordDeadOwner(firstGiant, eventId, generation, 4, 3);
        activeBudget.recordUnloadedOwner(secondGiant, eventId, generation, 4,
                worldId, 11, 20, 3);
        var budgetAfterRestart = new RiftGiantOwnerLocationRegistry(budgetFile);
        check(budgetAfterRestart.maxSummonsUsed(eventId, generation, 4) == 3,
                "cumulative summon usage survives one owner's death and another owner's unload");
        budgetAfterRestart.recordLoadedOwner(thirdGiant, eventId, generation, 4,
                worldId, 12, 20, 0);
        var budgetAfterNewGiant = new RiftGiantOwnerLocationRegistry(budgetFile);
        check(budgetAfterNewGiant.maxSummonsUsed(eventId, generation, 4) == 3,
                "a newly spawned giant cannot reset the wave-wide summon budget");
        budgetAfterNewGiant.recordWaveSummonsUsed(eventId, generation, 4, 5);
        budgetAfterNewGiant.recordWaveSummonsUsed(eventId, generation, 4, 2);
        budgetAfterNewGiant.resumeLoadedOwnersAfterRestart(eventId, generation);
        var budgetAfterSecondRestart = new RiftGiantOwnerLocationRegistry(budgetFile);
        check(budgetAfterSecondRestart.maxSummonsUsed(eventId, generation, 4) == 5
                        && budgetAfterSecondRestart.unloadedOwners(eventId, generation).size() == 2,
                "the monotonic wave budget and live-owner checkpoints survive another restart");
        budgetAfterSecondRestart.retainSession("next-event", 18L);
        var budgetForNextEvent = new RiftGiantOwnerLocationRegistry(budgetFile);
        check(budgetForNextEvent.maxSummonsUsed(eventId, generation, 4) == 0,
                "a validated new event prunes the prior event's cumulative budget");

        Path malformedLedger = directory.resolve("malformed.properties");
        Files.writeString(malformedLedger, "owner.not-a-uuid.status=UNLOADED\n");
        boolean rejectedMalformedLedger = false;
        try {
            new RiftGiantOwnerLocationRegistry(malformedLedger);
        } catch (IOException expected) {
            rejectedMalformedLedger = true;
        }
        check(rejectedMalformedLedger,
                "malformed owner evidence fails closed instead of silently discarding recovery state");

        System.out.println("RiftGiantOwnerLocationRegistryTest OK");
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
