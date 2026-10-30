package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict durable checkpoint for the single live Wave 1 charge across a restart. */
public final class RiftCarrierRecoverySnapshot {
    public static final String PREFIX = "wave1.carrier.";
    private static final Set<String> FIXED_KEYS = Set.of(
            PREFIX + "version", PREFIX + "event", PREFIX + "generation", PREFIX + "saved-millis",
            PREFIX + "delivered", PREFIX + "phase", PREFIX + "carrier", PREFIX + "charge",
            PREFIX + "holder", PREFIX + "inventory-token", PREFIX + "remaining-ticks");
    private static final int MAX_ROSTER_PROGRESS = RiftCarrierPolicy.REQUIRED_DELIVERIES;

    private RiftCarrierRecoverySnapshot() { }

    /**
     * Keep the version-1 deadline field as zero for compatibility; charges do not expire.
     */
    public static Map<String, String> encode(String eventId, long generation,
                                              RiftCarrierPolicy.State state, UUID inventoryToken,
                                              long eventTick, long savedMillis) {
        requireContext(eventId, generation, savedMillis);
        if (state == null || state.generation() != generation) {
            throw new IllegalArgumentException("Wave 1 carrier state generation mismatch");
        }
        long remainingTicks = 0L;
        validateState(state.delivered(), state.phase(), state.carrier(), state.charge(), state.holder(),
                inventoryToken, remainingTicks);
        Map<String, String> result = new LinkedHashMap<>();
        result.put(PREFIX + "version", "1");
        result.put(PREFIX + "event", eventId);
        result.put(PREFIX + "generation", Long.toString(generation));
        result.put(PREFIX + "saved-millis", Long.toString(savedMillis));
        result.put(PREFIX + "delivered", Integer.toString(state.delivered()));
        result.put(PREFIX + "phase", state.phase().name());
        result.put(PREFIX + "carrier", text(state.carrier()));
        result.put(PREFIX + "charge", text(state.charge()));
        result.put(PREFIX + "holder", text(state.holder()));
        result.put(PREFIX + "inventory-token", text(inventoryToken));
        result.put(PREFIX + "remaining-ticks", Long.toString(remainingTicks));
        return Map.copyOf(result);
    }

    /** Returns null when no Wave 1 checkpoint exists; malformed or mismatched data is rejected. */
    public static Data decode(Map<String, String> encoded, String expectedEventId,
                              long expectedGeneration, long nowMillis) {
        if (encoded == null) throw new IllegalArgumentException("Wave 1 progress is required");
        boolean found = encoded.keySet().stream().anyMatch(key -> key.startsWith(PREFIX));
        if (!found) return null;
        requireContext(expectedEventId, expectedGeneration, nowMillis);
        Set<String> actualKeys = new LinkedHashSet<>();
        encoded.keySet().stream().filter(key -> key.startsWith(PREFIX)).forEach(actualKeys::add);
        if (!actualKeys.equals(FIXED_KEYS)) {
            throw new IllegalArgumentException("Wave 1 carrier checkpoint keys are incomplete or unknown");
        }
        if (!"1".equals(encoded.get(PREFIX + "version"))
                || !expectedEventId.equals(encoded.get(PREFIX + "event"))
                || parseLong(encoded.get(PREFIX + "generation")) != expectedGeneration) {
            throw new IllegalArgumentException("Wave 1 carrier checkpoint identity mismatch");
        }
        long savedMillis = parseLong(encoded.get(PREFIX + "saved-millis"));
        if (savedMillis <= 0L || nowMillis < savedMillis) {
            throw new IllegalArgumentException("Wave 1 carrier checkpoint timestamp is invalid");
        }
        int delivered = parseInt(encoded.get(PREFIX + "delivered"));
        RiftCarrierPolicy.Phase phase;
        try {
            phase = RiftCarrierPolicy.Phase.valueOf(encoded.get(PREFIX + "phase"));
        } catch (RuntimeException invalidPhase) {
            throw new IllegalArgumentException("Wave 1 carrier phase is invalid", invalidPhase);
        }
        UUID carrier = parseUuid(encoded.get(PREFIX + "carrier"));
        UUID charge = parseUuid(encoded.get(PREFIX + "charge"));
        UUID holder = parseUuid(encoded.get(PREFIX + "holder"));
        UUID inventoryToken = parseUuid(encoded.get(PREFIX + "inventory-token"));
        long remainingTicks = parseLong(encoded.get(PREFIX + "remaining-ticks"));
        validateState(delivered, phase, carrier, charge, holder, inventoryToken, remainingTicks);
        RiftCarrierPolicy.State state = new RiftCarrierPolicy.State(expectedGeneration, delivered,
                carrier, charge, holder, 0L, phase);
        return new Data(expectedEventId, expectedGeneration, state, inventoryToken, savedMillis);
    }

    private static void validateState(int delivered, RiftCarrierPolicy.Phase phase,
                                      UUID carrier, UUID charge, UUID holder, UUID inventoryToken,
                                      long remainingTicks) {
        if (delivered < 0 || delivered > MAX_ROSTER_PROGRESS || phase == null
                || remainingTicks < 0L || remainingTicks > RiftCarrierPolicy.CHARGE_TIMEOUT_TICKS) {
            throw new IllegalArgumentException("Wave 1 carrier checkpoint is outside its bounds");
        }
        boolean valid = switch (phase) {
            case WAITING_FOR_CARRIER, READY_FOR_NEXT_CARRIER -> delivered < RiftCarrierPolicy.REQUIRED_DELIVERIES
                    && carrier == null && charge == null && holder == null && inventoryToken == null
                    && remainingTicks == 0L;
            case CARRIER_ACTIVE -> delivered < RiftCarrierPolicy.REQUIRED_DELIVERIES
                    && carrier != null && charge == null && holder == null && inventoryToken == null
                    && remainingTicks == 0L;
            case CHARGE_DROPPED -> delivered < RiftCarrierPolicy.REQUIRED_DELIVERIES
                    && carrier == null && charge != null && holder == null && inventoryToken == null;
            case CHARGE_CARRIED -> delivered < RiftCarrierPolicy.REQUIRED_DELIVERIES
                    && carrier == null && charge != null && holder != null && inventoryToken != null;
            case COMPLETE -> delivered == RiftCarrierPolicy.REQUIRED_DELIVERIES
                    && carrier == null && charge == null && holder == null && inventoryToken == null
                    && remainingTicks == 0L;
        };
        if (!valid) throw new IllegalArgumentException("Wave 1 carrier checkpoint state is inconsistent");
    }

    private static void requireContext(String eventId, long generation, long timestamp) {
        if (eventId == null || eventId.isBlank() || generation <= 0L || timestamp <= 0L) {
            throw new IllegalArgumentException("Invalid Wave 1 carrier checkpoint context");
        }
    }

    private static String text(UUID value) { return value == null ? "" : value.toString(); }

    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Wave 1 carrier checkpoint UUID is invalid", invalid);
        }
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); }
        catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Wave 1 carrier checkpoint number is invalid", invalid);
        }
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); }
        catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Wave 1 carrier checkpoint count is invalid", invalid);
        }
    }

    public record Data(String eventId, long generation, RiftCarrierPolicy.State state,
                       UUID inventoryToken, long savedMillis) { }
}
