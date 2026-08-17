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
        String bossRewardStatus,
        UUID bossRewardRecipient,
        String returnStoneStatus,
        String victoryStep,
        long updatedAt,
        String recoveryReason,
        Set<UUID> participants,
        Map<UUID, Double> finalDrainTargets,
        Set<UUID> finalDrainAppliedPlayers) {

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
        participants = Set.copyOf(participants == null ? Set.of() : participants);
        finalDrainTargets = Map.copyOf(finalDrainTargets == null ? Map.of() : finalDrainTargets);
        finalDrainAppliedPlayers = Set.copyOf(finalDrainAppliedPlayers == null ? Set.of() : finalDrainAppliedPlayers);
        bossRewardStatus = bossRewardStatus == null || bossRewardStatus.isBlank()
                ? "PENDING" : bossRewardStatus;
        victoryStep = victoryStep == null ? "NONE" : victoryStep;
        returnStoneStatus = returnStoneStatus == null ? "PENDING" : returnStoneStatus;
        recoveryReason = recoveryReason == null ? "" : recoveryReason;
    }

    public static EventSnapshot empty(int schemaVersion) {
        return new EventSnapshot(
                schemaVersion, "", 0L, EventPhase.UNCONFIGURED.name(), "", 0, 0, 0, "", 0,
                0, 0, 0, 0, 0, 0, Map.of(), Map.of(), List.of(), Set.of(), Set.of(), Map.of(), Map.of(),
                false, false, false, false, false, false, false, false, "PENDING", null,
                "PENDING", "NONE", 0L, "", Set.of(), Map.of(), Set.of());
    }

    public EventSnapshot withParticipants(Set<UUID> updatedParticipants) {
        return new EventSnapshot(
                schemaVersion, eventId, generation, phase, worldName,
                coreX, coreY, coreZ, coreBlockData, requiredPlayers,
                arenaMinX, arenaMinY, arenaMinZ, arenaMaxX, arenaMaxY, arenaMaxZ,
                resourceRequirements, depositedResources, pads, resourceContributors,
                officialRewardRoster, rewardStatuses, shardCooldowns, coreCharged,
                halfHealthTriggered, controlSpellUnlocked, finalDrainTriggered, finalDrainApplied,
                endUnlocked, officialBossDeathCommitted, bossLootCommitted,
                bossRewardStatus, bossRewardRecipient, returnStoneStatus, victoryStep, updatedAt, recoveryReason,
                updatedParticipants, finalDrainTargets,
                finalDrainAppliedPlayers);
    }

    public EventPhase eventPhase() {
        try {
            String compatible = switch (phase) {
                // States written by the first local prototypes used these
                // names.  Read them into the canonical state machine without
                // losing the rest of the durable snapshot.
                case "FINAL_RITUAL" -> "FINAL_DRAIN";
                case "VICTORY" -> "VICTORY_PROCESSING";
                default -> phase;
            };
            return EventPhase.valueOf(compatible);
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
