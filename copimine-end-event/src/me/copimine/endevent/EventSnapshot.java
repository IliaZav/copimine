package me.copimine.endevent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.EventPhase;

/**
 * Schema-4 persistence boundary for one End Rift attempt.
 *
 * <p>Only durable current-flow facts belong here. Bukkit entities, AI
 * decisions, projectile ids and temporary animation state are deliberately
 * absent. Older files are decoded by the migration package before they reach
 * this type.</p>
 */
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
        Map<UUID, Long> abyssAnchorCooldowns,
        boolean coreCharged,
        boolean endUnlocked,
        boolean officialBossDeathCommitted,
        boolean bossLootCommitted,
        String bossRewardStatus,
        UUID bossRewardRecipient,
        String returnStoneStatus,
        String victoryStep,
        long updatedAt,
        long phaseDeadlineMillis,
        String recoveryReason,
        Set<UUID> participants,
        Set<Integer> waveRewardsIssued,
        String bossPhase,
        String activeBossAbility,
        long bossAbilityDeadlineMillis,
        String bossDefeatSaga,
        Map<String, String> objectiveProgress,
        Map<UUID, String> nightCloakRolls) {

    public static final int CURRENT_SCHEMA = 4;

    public EventSnapshot {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("snapshot schema must be positive");
        }
        eventId = eventId == null ? "" : eventId.trim();
        phase = phase == null ? EventPhase.RECOVERY_REQUIRED.name() : phase.trim();
        worldName = worldName == null ? "" : worldName.trim();
        coreBlockData = coreBlockData == null ? "" : coreBlockData.trim();
        resourceRequirements = Map.copyOf(resourceRequirements == null ? Map.of() : resourceRequirements);
        depositedResources = Map.copyOf(depositedResources == null ? Map.of() : depositedResources);
        pads = List.copyOf(pads == null ? List.of() : pads);
        resourceContributors = Set.copyOf(resourceContributors == null ? Set.of() : resourceContributors);
        officialRewardRoster = Set.copyOf(officialRewardRoster == null ? Set.of() : officialRewardRoster);
        rewardStatuses = Map.copyOf(rewardStatuses == null ? Map.of() : rewardStatuses);
        shardCooldowns = Map.copyOf(shardCooldowns == null ? Map.of() : shardCooldowns);
        abyssAnchorCooldowns = Map.copyOf(abyssAnchorCooldowns == null ? Map.of() : abyssAnchorCooldowns);
        participants = Set.copyOf(participants == null ? Set.of() : participants);
        waveRewardsIssued = Set.copyOf(waveRewardsIssued == null ? Set.of() : waveRewardsIssued);
        bossPhase = bossPhase == null || bossPhase.isBlank()
                ? BossPhase.AWAKENING.name() : bossPhase.trim();
        activeBossAbility = activeBossAbility == null || activeBossAbility.isBlank()
                ? "NONE" : activeBossAbility.trim();
        bossAbilityDeadlineMillis = Math.max(0L, bossAbilityDeadlineMillis);
        bossDefeatSaga = bossDefeatSaga == null || bossDefeatSaga.isBlank()
                ? "NONE" : bossDefeatSaga.trim();
        objectiveProgress = Map.copyOf(objectiveProgress == null ? Map.of() : objectiveProgress);
        nightCloakRolls = Map.copyOf(nightCloakRolls == null ? Map.of() : nightCloakRolls);
        phaseDeadlineMillis = Math.max(0L, phaseDeadlineMillis);
        updatedAt = Math.max(0L, updatedAt);
        recoveryReason = recoveryReason == null ? "" : recoveryReason.trim();
    }

    public static EventSnapshot empty(int schemaVersion) {
        return new EventSnapshot(
                Math.max(1, schemaVersion), "", 0L, EventPhase.UNCONFIGURED.name(),
                "", 0, 0, 0, "", 0,
                0, 0, 0, 0, 0, 0,
                Map.of(), Map.of(), List.of(), Set.of(), Set.of(), Map.of(), Map.of(), Map.of(),
                false, false, false, false, "PENDING", null, "PENDING", "NONE",
                0L, 0L, "", Set.of(), Set.of(), BossPhase.AWAKENING.name(), "NONE", 0L,
                "NONE", Map.of(), Map.of());
    }

    public EventPhase eventPhase() {
        try {
            return EventPhase.valueOf(phase);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("unknown current event phase: " + phase, invalid);
        }
    }

    public BossPhase currentBossPhase() {
        try {
            return BossPhase.valueOf(bossPhase);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("unknown current boss phase: " + bossPhase, invalid);
        }
    }

    public boolean configured() {
        return !eventId.isBlank() && requiredPlayers > 0 && !worldName.isBlank();
    }

    public EventSnapshot withParticipants(Set<UUID> updatedParticipants) {
        return new EventSnapshot(schemaVersion, eventId, generation, phase, worldName,
                coreX, coreY, coreZ, coreBlockData, requiredPlayers,
                arenaMinX, arenaMinY, arenaMinZ, arenaMaxX, arenaMaxY, arenaMaxZ,
                resourceRequirements, depositedResources, pads, resourceContributors,
                officialRewardRoster, rewardStatuses, shardCooldowns, abyssAnchorCooldowns,
                coreCharged, endUnlocked, officialBossDeathCommitted, bossLootCommitted,
                bossRewardStatus, bossRewardRecipient, returnStoneStatus, victoryStep,
                updatedAt, phaseDeadlineMillis, recoveryReason, updatedParticipants,
                waveRewardsIssued, bossPhase, activeBossAbility, bossAbilityDeadlineMillis,
                bossDefeatSaga, objectiveProgress, nightCloakRolls);
    }

    public EventSnapshot withSchemaAndPhase(int updatedSchemaVersion, EventPhase updatedPhase) {
        EventPhase safe = updatedPhase == null ? EventPhase.RECOVERY_REQUIRED : updatedPhase;
        return new EventSnapshot(Math.max(1, updatedSchemaVersion), eventId, generation,
                safe.name(), worldName, coreX, coreY, coreZ, coreBlockData, requiredPlayers,
                arenaMinX, arenaMinY, arenaMinZ, arenaMaxX, arenaMaxY, arenaMaxZ,
                resourceRequirements, depositedResources, pads, resourceContributors,
                officialRewardRoster, rewardStatuses, shardCooldowns, abyssAnchorCooldowns,
                coreCharged, endUnlocked, officialBossDeathCommitted, bossLootCommitted,
                bossRewardStatus, bossRewardRecipient, returnStoneStatus, victoryStep,
                updatedAt, phaseDeadlineMillis, recoveryReason, participants, waveRewardsIssued,
                bossPhase, activeBossAbility, bossAbilityDeadlineMillis, bossDefeatSaga,
                objectiveProgress, nightCloakRolls);
    }

    public record PadSnapshot(int x, int y, int z, double radius,
                              double angleRadians, String originalBlockData) {
        public PadSnapshot {
            originalBlockData = originalBlockData == null ? "" : originalBlockData;
        }
    }
}
