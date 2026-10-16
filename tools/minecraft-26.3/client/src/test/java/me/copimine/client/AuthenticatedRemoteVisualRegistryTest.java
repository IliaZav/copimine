package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AuthenticatedRemoteVisualRegistryTest {
    @Test
    void clearFenceUsesSignedStreamCounterAndRemainsIsolatedByStreamInstance() {
        AuthenticatedRemoteVisualRegistry registry = new AuthenticatedRemoteVisualRegistry();
        UUID instance = UUID.fromString("00000000-0000-0000-0000-000000000077");

        assertNull(registry.put("stream-a", instance, 20, 2));
        assertNull(registry.put("stream-b", instance, 2, 1));

        assertTrue(registry.clearThrough("stream-a", 19).isEmpty());
        assertTrue(registry.contains("stream-a", instance));
        assertTrue(registry.contains("stream-b", instance));

        assertEquals(List.of(2L), registry.clearThrough("stream-a", 20));
        assertFalse(registry.contains("stream-a", instance));
        assertTrue(registry.contains("stream-b", instance));
        assertNull(registry.remove("stream-a", instance));
        assertTrue(registry.contains("stream-b", instance));
    }

    @Test
    void replacementFencePreservesTheCurrentVisualInstanceUntilReplacement() {
        AuthenticatedRemoteVisualRegistry registry = new AuthenticatedRemoteVisualRegistry();
        UUID currentInstance = UUID.fromString("00000000-0000-0000-0000-000000000078");
        UUID priorInstance = UUID.fromString("00000000-0000-0000-0000-000000000079");
        registry.put("stream-a", currentInstance, 4, 40);
        registry.put("stream-a", priorInstance, 3, 30);

        assertEquals(List.of(30L), registry.clearThrough("stream-a", 4, currentInstance));
        assertTrue(registry.contains("stream-a", currentInstance));
        assertFalse(registry.contains("stream-a", priorInstance));
    }
}
