package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderRuntimeCleanupPolicyTest {
    @Test
    void clearsRuntimeAfterSuccessfulApplication() {
        assertTrue(ShaderRuntimeCleanupPolicy.shouldClear(true, false));
    }

    @Test
    void clearsRuntimeAfterFailedApplicationMayHaveMutatedIt() {
        assertTrue(ShaderRuntimeCleanupPolicy.shouldClear(false, true));
    }

    @Test
    void skipsRestoreWhenNoRuntimeWasTouched() {
        assertFalse(ShaderRuntimeCleanupPolicy.shouldClear(false, false));
    }
}
