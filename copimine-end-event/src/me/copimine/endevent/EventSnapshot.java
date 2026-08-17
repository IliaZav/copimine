package me.copimine.endevent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.EventPhase;

/** Immutable persistence boundary; transient Bukkit objects never cross it. */
public record EventSnapshot(
        int schemaVersion,
        String eventId,
        long generation,
        String phase,
        String worldName,
        int coreX,
        int coreY,
        int coreZ,
        String coreBlockData,
        int requiredPlayers,
        int arenaMinX,
        int arenaMinY,
        int arenaMinZ,
        int arenaMaxX,
        int arenaMaxY,
        int arenaMaxZ,
        Map<String, Integer> resourceRequirements,
        Map<String, Integer> depositedResources,
        List<PadSnapshot> pads,
        Set<UUID> resourceContributors,
        Set<UUID> officialRewardRoster,
        Map<UUID, String> rewardStatuses,
        Map<UUID, Long> shardCooldowns,
        boolean coreCharged,
        boolean halfHealthTriggered,
        boolean controlSpellUnlocked,
        boolean finalDrainTriggered,
        boolean finalDrainApplied,
        boolean endUnlocked,
        boolean officialBossDeathCommitted,
        boolean bossLootCommitted,
        String returnStoneStatus,
        String victoryStep,
        long updatedAt,
        String recoveryReason) {

    public EventSnapshot {
        eventId = eventId == null ? "" : eventId;
        phase = phase == null ? EventPhase.RECOVERY_REQUIRED.name() : phase;
        worldName = worldName == null ? "" : worldName;
        coreBlockData = coreBlockData == null ? "" : coreBlockData;
        resourceRequirements = Map.copyOf(resourceRequirements == null ? Map.of() : resourceRequirements);
        depositedResources = Map.copyOf(depositedResources == null ? Map.of() : depositedResources);
        pads = List.copyOf(pads == null ? List.of() : pads);
        resourceContributors = Set.copyOf(resourceContributors == null ? Set.of() : resourceContributors);
        officialRewardRoster = Set.copyOf(officialRewardRoster == null ? Set.of() : officialRewardRoster);
        rewardStatuses = Map.copyOf(rewardStatuses == null ? Map.of() : rewardStatuses);
        shardCooldowns = Map.copyOf(shardCooldowns == null ? Map.of() : shardCooldowns);
        victoryStep = victoryStep == null ? "NONE" : victoryStep;
        returnStoneStatus = returnStoneStatus == null ? "PENDING" : returnStoneStatus;
        recoveryReason = recoveryReason == null ? "" : recoveryReason;
    }

    public static EventSnapshot empty(int schemaVersion) {
        return new EventSnapshot(
                schemaVersion, "", 0L, EventPhase.UNCONFIGURED.name(), "", 0, 0, 0, "", 0,
                0, 0, 0, 0, 0, 0, Map.of(), Map.of(), List.of(), Set.of(), Set.of(), Map.of(), Map.of(),
                false, false, false, false, false, false, false, false, "PENDING", "NONE", 0L, "");
    }

    public EventPhase eventPhase() {
        try {
            return EventPhase.valueOf(phase);
        } catch (IllegalArgumentException invalid) {
            return EventPhase.RECOVERY_REQUIRED;
        }
    }

    public boolean configured() {
        return !eventId.isBlank() && requiredPlayers > 0 && !worldName.isBlank();
    }

    public record PadSnapshot(int x, int y, int z, double radius, double angleRadians, String originalBlockData) {
        public PadSnapshot {
            originalBlockData = originalBlockData == null ? "" : originalBlockData;
        }
    }
}
