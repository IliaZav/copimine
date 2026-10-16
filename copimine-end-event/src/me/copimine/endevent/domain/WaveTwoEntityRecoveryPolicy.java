package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Durable identity and lifecycle rules for Wave 2's active mob group. */
public final class WaveTwoEntityRecoveryPolicy {
    public static final int MAX_OUTSTANDING_UUIDS = PressureBudgetController.HARD_CAP;
    public static final int MAX_CLEANUP_UUIDS = 512;
    public static final String CLEANUP_FENCE_REQUIRED_REASON = "WAVE_TWO_CLEANUP_FENCE_REQUIRED";

    public enum LoadDisposition { IGNORE, REHYDRATE, REMOVE, RECOVERY_REQUIRED }

    private WaveTwoEntityRecoveryPolicy() { }

    /** Recovery-fenced fighters cannot regain native AI until valid rehydration. */
    public static boolean shouldSuspendRuntimeAi(boolean waveTwoEntity,
                                                 boolean recoveryRequired,
                                                 boolean explicitlyFenced) {
        return explicitlyFenced || waveTwoEntity && recoveryRequired;
    }

    /** Persist the recovery boundary in the same checkpoint that commits the cleanup fence. */
    public static String phaseForCleanupFenceSnapshot(String phaseName, String recoveryReason) {
        if ("WAVE_2".equals(phaseName) && CLEANUP_FENCE_REQUIRED_REASON.equals(recoveryReason)) {
            return "RECOVERY_REQUIRED";
        }
        return phaseName;
    }

    /** Parse a bounded, canonical UUID list before callers mutate live state. */
    public static Set<UUID> decode(String encoded, int maximumEntries) {
        if (maximumEntries < 0 || maximumEntries > MAX_CLEANUP_UUIDS) {
            throw new IllegalArgumentException("UUID roster bound is invalid");
        }
        if (encoded == null) {
            throw new IllegalArgumentException("UUID roster is missing");
        }
        if (encoded.isEmpty()) return Set.of();
        long maximumCharacters = (long) maximumEntries * 36L + Math.max(0, maximumEntries - 1);
        if (encoded.length() > maximumCharacters) {
            throw new IllegalArgumentException("UUID roster exceeds its encoded size bound");
        }
        String[] entries = encoded.split(",", -1);
        if (entries.length > maximumEntries) {
            throw new IllegalArgumentException("UUID roster exceeds its entry bound");
        }
        LinkedHashSet<UUID> parsed = new LinkedHashSet<>();
        for (String entry : entries) {
            UUID uuid;
            try {
                uuid = UUID.fromString(entry);
            } catch (IllegalArgumentException malformed) {
                throw new IllegalArgumentException("UUID roster contains a malformed UUID", malformed);
            }
            if (!uuid.toString().equals(entry)) {
                throw new IllegalArgumentException("UUID roster UUID is not canonical");
            }
            if (!parsed.add(uuid)) {
                throw new IllegalArgumentException("UUID roster contains a duplicate UUID");
            }
        }
        return Set.copyOf(parsed);
    }

    public static String encode(Set<UUID> uuids, int maximumEntries) {
        if (uuids == null || maximumEntries < 0 || uuids.size() > maximumEntries) {
            throw new IllegalArgumentException("UUID roster exceeds its entry bound");
        }
        List<String> encoded = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            if (uuid == null) throw new IllegalArgumentException("UUID roster contains null");
            encoded.add(uuid.toString());
        }
        encoded.sort(String::compareTo);
        return String.join(",", encoded);
    }

    /** A death is the only entity lifecycle event that retires an outstanding fighter. */
    public static Set<UUID> afterConfirmedDeath(Set<UUID> outstanding, UUID deadUuid) {
        if (outstanding == null || deadUuid == null) {
            throw new IllegalArgumentException("outstanding roster and dead UUID are required");
        }
        if (!outstanding.contains(deadUuid)) return Set.copyOf(outstanding);
        LinkedHashSet<UUID> remaining = new LinkedHashSet<>(outstanding);
        remaining.remove(deadUuid);
        return Set.copyOf(remaining);
    }

    public static Set<UUID> addSpawned(Set<UUID> outstanding, UUID spawnedUuid) {
        if (outstanding == null || spawnedUuid == null) {
            throw new IllegalArgumentException("outstanding roster and spawned UUID are required");
        }
        if (outstanding.contains(spawnedUuid)) return Set.copyOf(outstanding);
        if (outstanding.size() >= MAX_OUTSTANDING_UUIDS) {
            throw new IllegalArgumentException("Wave 2 outstanding roster exceeds the active mob cap");
        }
        LinkedHashSet<UUID> updated = new LinkedHashSet<>(outstanding);
        updated.add(spawnedUuid);
        return Set.copyOf(updated);
    }

    /** Keep unloaded outstanding fighters in the same coordinator roster. */
    public static List<UUID> coordinatorRoster(Collection<UUID> loaded,
                                               Set<UUID> outstanding) {
        LinkedHashSet<UUID> combined = new LinkedHashSet<>();
        if (loaded != null) combined.addAll(loaded);
        if (outstanding != null) combined.addAll(outstanding);
        combined.remove(null);
        return combined.stream().sorted().toList();
    }

    public static Set<UUID> archiveForCleanup(Set<UUID> cleanup, Set<UUID> outstanding) {
        if (cleanup == null || outstanding == null) {
            throw new IllegalArgumentException("cleanup and outstanding rosters are required");
        }
        LinkedHashSet<UUID> archived = new LinkedHashSet<>(cleanup);
        archived.addAll(outstanding);
        if (archived.size() > MAX_CLEANUP_UUIDS) {
            throw new IllegalArgumentException("Wave 2 cleanup roster exceeds its durable bound");
        }
        return Set.copyOf(archived);
    }

    /** Advance the durable Wave 2 cleanup fence before releasing any UUID roster. */
    public static long nextCleanupEpoch(long currentEpoch) {
        if (currentEpoch < 0L || currentEpoch == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Wave 2 cleanup epoch cannot advance");
        }
        return currentEpoch + 1L;
    }

    public static boolean groupDefeated(Set<UUID> outstanding) {
        return outstanding != null && outstanding.isEmpty();
    }

    /**
     * Decide whether a chunk-loaded Wave 2 entity belongs to the active
     * durable group. Earlier groups are stale and removed; an unexpected
     * member of the current/future group fences the attempt for recovery.
     */
    public static LoadDisposition classifyLoad(boolean belongsToEvent, boolean cleanupTombstone,
                                               long taggedGeneration, long expectedGeneration,
                                               int taggedGroup, int activeGroup,
                                               boolean waveTwoActive, boolean groupCleared,
                                               boolean recoveryRequired, boolean outstandingMember) {
        return classifyLoad(belongsToEvent, cleanupTombstone, 0L, 0L,
                taggedGeneration, expectedGeneration, taggedGroup, activeGroup,
                waveTwoActive, groupCleared, recoveryRequired, outstandingMember);
    }

    /**
     * Permit a one-time migration for pre-epoch final elites saved by older builds.
     * The caller must also verify the exact final-elite PDC marker and that the epoch
     * key is absent; every ownership, generation, active-group, and roster boundary
     * is repeated here so a stale entity cannot borrow the current durable epoch.
     */
    public static boolean canMigrateLegacyFinalEliteEpoch(
            boolean legacyFinalElite,
            boolean cleanupEpochMissing,
            boolean belongsToEvent,
            boolean cleanupTombstone,
            long taggedGeneration,
            long expectedGeneration,
            int taggedGroup,
            int activeGroup,
            long expectedCleanupEpoch,
            boolean waveTwoActive,
            boolean groupCleared,
            boolean recoveryRequired,
            boolean outstandingMember
    ) {
        return legacyFinalElite
                && cleanupEpochMissing
                && belongsToEvent
                && !cleanupTombstone
                && expectedGeneration > 0L
                && taggedGeneration == expectedGeneration
                && taggedGroup == WaveTwoStagePolicy.STAGE_COUNT
                && activeGroup == WaveTwoStagePolicy.STAGE_COUNT
                && expectedCleanupEpoch > 0L
                && waveTwoActive
                && !groupCleared
                && !recoveryRequired
                && outstandingMember;
    }

    public static LoadDisposition classifyLoad(boolean belongsToEvent, boolean cleanupTombstone,
                                               long taggedCleanupEpoch, long expectedCleanupEpoch,
                                               long taggedGeneration, long expectedGeneration,
                                               int taggedGroup, int activeGroup,
                                               boolean waveTwoActive, boolean groupCleared,
                                               boolean recoveryRequired, boolean outstandingMember) {
        if (cleanupTombstone) return LoadDisposition.REMOVE;
        if (taggedCleanupEpoch < expectedCleanupEpoch) {
            return LoadDisposition.REMOVE;
        }
        if (taggedCleanupEpoch > expectedCleanupEpoch) {
            return LoadDisposition.RECOVERY_REQUIRED;
        }
        if (!belongsToEvent) return LoadDisposition.IGNORE;
        if (taggedGeneration != expectedGeneration) return LoadDisposition.REMOVE;
        if (!waveTwoActive) {
            if (!recoveryRequired || activeGroup > 0 && taggedGroup < activeGroup) {
                return LoadDisposition.REMOVE;
            }
            return LoadDisposition.RECOVERY_REQUIRED;
        }
        if (groupCleared || activeGroup < 1 || taggedGroup < activeGroup) {
            return LoadDisposition.REMOVE;
        }
        if (taggedGroup > activeGroup || !outstandingMember) {
            return LoadDisposition.RECOVERY_REQUIRED;
        }
        return LoadDisposition.REHYDRATE;
    }
}
