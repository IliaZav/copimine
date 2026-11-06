package me.copimine.endevent.domain;

import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;

/** Stable server-owned assignment of Wave 2 combat bodies to marked players. */
public final class WaveTwoOwnershipPolicy {
    private WaveTwoOwnershipPolicy() { }

    /** Choose once from a bounded, canonical roster; callers persist this UUID on the entity. */
    public static UUID initialOwner(UUID eventId, long generation, UUID entityId,
                                    Collection<UUID> roster) {
        if (eventId == null || entityId == null || generation <= 0L) {
            throw new IllegalArgumentException("Wave 2 owner identity is invalid");
        }
        List<UUID> owners = canonicalRoster(roster);
        long identity = entityId.getMostSignificantBits()
                ^ Long.rotateLeft(entityId.getLeastSignificantBits(), 29)
                ^ Long.rotateLeft(eventId.getMostSignificantBits(), 11)
                ^ Long.rotateLeft(eventId.getLeastSignificantBits(), 41)
                ^ generation;
        return owners.get((int) Math.floorMod(identity, owners.size()));
    }

    /** Reassign one departed owner's bodies deterministically, preserving other squads. */
    public static UUID replacementOwner(UUID eventId, long generation, UUID entityId,
                                        UUID currentOwner, Collection<UUID> eligibleOwners) {
        if (currentOwner == null) {
            return initialOwner(eventId, generation, entityId, eligibleOwners);
        }
        if (eligibleOwners == null || eligibleOwners.isEmpty()) return currentOwner;
        List<UUID> replacements = canonicalRoster(eligibleOwners).stream()
                .filter(owner -> !owner.equals(currentOwner))
                .toList();
        if (replacements.isEmpty()) return currentOwner;
        return initialOwner(eventId, generation, entityId, replacements);
    }

    /** A Wave 2 combat action is legal only for its persisted owner in this generation. */
    public static boolean allowsTarget(UUID owner, UUID target,
                                       long entityGeneration, long currentGeneration) {
        return owner != null && target != null && currentGeneration > 0L
                && entityGeneration == currentGeneration && owner.equals(target);
    }

    /** Encode the frozen roster in a bounded, deterministic checkpoint value. */
    public static String encodeRoster(Collection<UUID> roster) {
        return String.join(",", canonicalRoster(roster).stream().map(UUID::toString).toList());
    }

    /** Decode a bounded checkpoint roster, rejecting duplicates and malformed UUIDs. */
    public static List<UUID> decodeRoster(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("Wave 2 owner roster is missing");
        }
        String[] parts = encoded.split(",", -1);
        if (parts.length == 0 || parts.length > PressureBudgetController.MAX_PLAYERS) {
            throw new IllegalArgumentException("Wave 2 owner roster is outside its bound");
        }
        LinkedHashSet<UUID> decoded = new LinkedHashSet<>();
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) throw new IllegalArgumentException("Wave 2 owner roster has an empty UUID");
            UUID owner;
            try {
                owner = UUID.fromString(token);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Wave 2 owner roster has an invalid UUID", invalid);
            }
            if (!owner.toString().equals(token.toLowerCase(Locale.ROOT)) || !decoded.add(owner)) {
                throw new IllegalArgumentException("Wave 2 owner roster is non-canonical or duplicated");
            }
        }
        return List.copyOf(canonicalRoster(decoded));
    }

    private static List<UUID> canonicalRoster(Collection<UUID> roster) {
        if (roster == null || roster.isEmpty() || roster.size() > PressureBudgetController.MAX_PLAYERS) {
            throw new IllegalArgumentException("Wave 2 owner roster is outside its bound");
        }
        TreeSet<UUID> sorted = new TreeSet<>();
        for (UUID owner : roster) {
            if (owner == null) throw new IllegalArgumentException("Wave 2 owner roster contains null");
            sorted.add(owner);
        }
        if (sorted.isEmpty() || sorted.size() > PressureBudgetController.MAX_PLAYERS) {
            throw new IllegalArgumentException("Wave 2 owner roster is outside its bound");
        }
        return List.copyOf(sorted);
    }
}
