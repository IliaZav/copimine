package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopiMineClientLoggerTest {
    @Test
    void marksAndRestoresRemoteCallbackContextIncludingNestedCallbacks() {
        assertFalse(CopiMineClientLogger.isInsideServerCallback());

        CopiMineClientLogger.runFromServer(() -> {
            assertTrue(CopiMineClientLogger.isInsideServerCallback());
            CopiMineClientLogger.runFromServer(() -> assertTrue(CopiMineClientLogger.isInsideServerCallback()));
            assertTrue(CopiMineClientLogger.isInsideServerCallback());
        });

        assertFalse(CopiMineClientLogger.isInsideServerCallback());
    }

    @Test
    void scopesExpiryCleanupOnlyWhenItComesFromAServerVisual() {
        CopiMineClientLogger.runFromServerIf(true,
                () -> assertTrue(CopiMineClientLogger.isInsideServerCallback()));
        assertFalse(CopiMineClientLogger.isInsideServerCallback());

        CopiMineClientLogger.runFromServerIf(false,
                () -> assertFalse(CopiMineClientLogger.isInsideServerCallback()));
        assertFalse(CopiMineClientLogger.isInsideServerCallback());
    }
}
