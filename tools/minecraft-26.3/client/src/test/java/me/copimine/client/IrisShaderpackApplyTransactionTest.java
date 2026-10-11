package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisShaderpackApplyTransactionTest {
    @Test
    void restoresCallSnapshotAndClearsStateWhenNewApplyCannotBeActivated() {
        IOException switchFailure = new IOException("switch failed");
        AtomicReference<String> restored = new AtomicReference<>();
        AtomicBoolean stateCleared = new AtomicBoolean();

        IOException thrown = assertThrows(IOException.class, () -> IrisShaderpackApplyTransaction.execute(
                true,
                "existing-user-pack",
                () -> { throw switchFailure; },
                restored::set,
                () -> stateCleared.set(true)));

        assertSame(switchFailure, thrown);
        assertEquals("existing-user-pack", restored.get());
        assertTrue(stateCleared.get());
    }

    @Test
    void retainsRecoveryStateAndOriginalFailureWhenRollbackFails() {
        IOException switchFailure = new IOException("switch failed");
        IOException rollbackFailure = new IOException("restore failed");
        AtomicBoolean stateCleared = new AtomicBoolean();

        IOException thrown = assertThrows(IOException.class, () -> IrisShaderpackApplyTransaction.execute(
                true,
                "existing-user-pack",
                () -> { throw switchFailure; },
                previous -> { throw rollbackFailure; },
                () -> stateCleared.set(true)));

        assertSame(switchFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(rollbackFailure, thrown.getSuppressed()[0]);
        assertFalse(stateCleared.get());
    }

    @Test
    void restoresCurrentPackWithoutClearingAnOlderSavedTransaction() {
        IOException switchFailure = new IOException("switch failed");
        AtomicReference<String> restored = new AtomicReference<>();
        AtomicBoolean stateCleared = new AtomicBoolean();

        IOException thrown = assertThrows(IOException.class, () -> IrisShaderpackApplyTransaction.execute(
                false,
                "current-copimine-pack",
                () -> { throw switchFailure; },
                restored::set,
                () -> stateCleared.set(true)));

        assertSame(switchFailure, thrown);
        assertEquals("current-copimine-pack", restored.get());
        assertFalse(stateCleared.get());
    }
}
