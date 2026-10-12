package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteVisualRuntimeTransitionGateTest {
    @Test
    void limitsServerApplyAndExpiryRestoreThroughTheSameGate() {
        RemoteVisualRuntimeTransitionGate gate = new RemoteVisualRuntimeTransitionGate(2_000L);

        assertTrue(gate.allow(true, true, 1_000L)); // server starts a shaderpack
        assertFalse(gate.allow(true, true, 2_000L)); // expiry tries to restore it
        assertTrue(gate.allow(true, true, 3_000L)); // pending restore retries after the interval
    }

    @Test
    void serverStopAndClearAlsoShareTheTransitionInterval() {
        RemoteVisualRuntimeTransitionGate gate = new RemoteVisualRuntimeTransitionGate(2_000L);

        assertTrue(gate.allow(true, true, 10_000L)); // server-driven profile switch
        assertFalse(gate.allow(true, true, 11_000L)); // server stop
        assertFalse(gate.allow(true, true, 11_999L)); // clear-all cannot bypass stop
        assertTrue(gate.allow(true, true, 12_000L));
    }

    @Test
    void localAndNonIrisTransitionsDoNotConsumeTheRemoteIrisBudget() {
        RemoteVisualRuntimeTransitionGate gate = new RemoteVisualRuntimeTransitionGate(2_000L);

        assertTrue(gate.allow(false, true, 1_000L)); // explicit local restore
        assertTrue(gate.allow(true, false, 1_001L)); // overlay fallback has no Iris reload
        assertTrue(gate.allow(true, true, 1_002L)); // the first remote Iris transition remains available
        assertTrue(gate.allow(false, true, 1_003L));
        assertFalse(gate.allow(true, true, 1_004L));
    }
}
