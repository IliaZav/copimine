package me.copimine.visualruntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VisualSessionCompletionPolicyTest {
    @Test
    void completionFromAnOlderClientCommandCannotFinishTheCurrentVisualSession() {
        UUID firstCommand = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID refreshedCommand = UUID.fromString("00000000-0000-0000-0000-000000000102");

        assertTrue(VisualSessionCompletionPolicy.isCurrent(firstCommand, firstCommand));
        assertFalse(VisualSessionCompletionPolicy.isCurrent(refreshedCommand, firstCommand));
        assertFalse(VisualSessionCompletionPolicy.isCurrent(null, firstCommand));
    }

    @Test
    void fallbackUsesTheExistingAbsoluteSessionExpiry() {
        UUID command = UUID.fromString("00000000-0000-0000-0000-000000000103");
        long expiresAtMillis = 700_000L;

        assertTrue(VisualSessionCompletionPolicy.canApplyFallback(command, command, expiresAtMillis, 100_000L));
        assertEquals(570, VisualSessionCompletionPolicy.remainingSecondsUntil(expiresAtMillis, 130_000L));
    }

    @Test
    void fallbackCannotReviveAnExpiredSessionOrAnOlderCommand() {
        UUID currentCommand = UUID.fromString("00000000-0000-0000-0000-000000000104");
        UUID oldCommand = UUID.fromString("00000000-0000-0000-0000-000000000105");
        long expiresAtMillis = 700_000L;

        assertFalse(VisualSessionCompletionPolicy.canApplyFallback(
                currentCommand, currentCommand, expiresAtMillis, expiresAtMillis));
        assertFalse(VisualSessionCompletionPolicy.canApplyFallback(
                currentCommand, oldCommand, expiresAtMillis, 699_999L));
        assertEquals(0, VisualSessionCompletionPolicy.remainingSecondsUntil(expiresAtMillis, expiresAtMillis + 1));
    }
}
