package me.copimine.endevent.domain;

import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

/** Validates death and actor receipts before the Paper adapter builds anything. */
public final class RiftRamRecoveryPolicy {
    private RiftRamRecoveryPolicy() { }

    public record Actors(boolean ramDefeated, boolean skeletonSpawned,
                         boolean skeletonDefeated, boolean skeletonFallen, UUID skeletonId) { }

    public static Actors decodeActors(Map<String, String> data, int impacts) {
        boolean current = "3".equals(data.get("ram.schema"));
        if (!current && !"2".equals(data.get("ram.schema"))) {
            throw new IllegalArgumentException("legacy-schema");
        }
        String raw = data.get("ram.skeleton");
        UUID skeletonId = raw == null ? null : UUID.fromString(raw);
        boolean spawned = current ? flag(data, "ram.skeleton-spawned") : skeletonId != null;
        boolean defeated = current && flag(data, "ram.skeleton-defeated");
        boolean fallen = current ? flag(data, "ram.skeleton-fallen")
                : spawned && impacts >= RiftRamPolicy.REQUIRED_IMPACTS;
        boolean ramDefeated = current && flag(data, "ram.defeated");
        if (spawned != (skeletonId != null) || defeated && !spawned || fallen && !spawned
                || spawned && impacts < RiftRamPolicy.MINOR_PILLARS
                || fallen && impacts < RiftRamPolicy.REQUIRED_IMPACTS
                || ramDefeated && impacts != RiftRamPolicy.REQUIRED_IMPACTS) {
            throw new IllegalArgumentException("inconsistent-actor-receipts");
        }
        boolean finalPillarStanding = impacts >= RiftRamPolicy.MINOR_PILLARS
                && data.containsKey("ram.pillar-id");
        if ((finalPillarStanding || impacts == RiftRamPolicy.REQUIRED_IMPACTS) && !spawned) {
            throw new IllegalArgumentException("missing-final-skeleton-receipt");
        }
        if ("true".equals(data.get("ram.complete"))
                && (!current || !ramDefeated || !defeated)) {
            throw new IllegalArgumentException("unproven-completion-receipt");
        }
        return new Actors(ramDefeated, spawned, defeated, fallen, skeletonId);
    }

    /** Validate persistent identities before deleting any restart presentation. */
    public static Set<UUID> persistentActorIds(Map<String, String> data) {
        int impacts = Integer.parseInt(data.get("ram.impacts"));
        if (impacts < 0 || impacts > RiftRamPolicy.REQUIRED_IMPACTS)
            throw new IllegalArgumentException("impact-range");
        Actors actors = decodeActors(data, impacts);
        pillarImpacts(data, impacts);
        Set<UUID> ids = new HashSet<>();
        ids.add(UUID.fromString(data.get("ram.uuid")));
        if (actors.skeletonId() != null && !ids.add(actors.skeletonId()))
            throw new IllegalArgumentException("shared-actor-identity");
        return Set.copyOf(ids);
    }

    /** Standing final geometry and its local damage count must agree. */
    public static int pillarImpacts(Map<String, String> data, int impacts) {
        int local = Integer.parseInt(data.get("ram.pillar-impacts"));
        boolean standing = data.containsKey("ram.pillar-id");
        int expected = standing ? Math.max(0, impacts - RiftRamPolicy.MINOR_PILLARS) : 0;
        if (local != expected || local < 0 || local >= RiftRamPolicy.FINAL_PILLAR_IMPACTS
                || !standing && impacts > RiftRamPolicy.MINOR_PILLARS
                    && impacts < RiftRamPolicy.REQUIRED_IMPACTS
                || standing && impacts >= RiftRamPolicy.REQUIRED_IMPACTS)
            throw new IllegalArgumentException("inconsistent-pillar-impacts");
        return local;
    }

    private static boolean flag(Map<String, String> data, String key) {
        String value = data.get(key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("invalid-" + key);
        }
        return Boolean.parseBoolean(value);
    }
}
