package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientReadyRetryStateTest {
    @Test
    void retries_are_bounded_and_stop_after_accepted_ack() {
        ClientReadyRetryState state = new ClientReadyRetryState();
        state.start(1_000L);

        assertTrue(state.shouldSend(1_000L));
        state.markSent(1_000L);
        assertFalse(state.shouldSend(1_500L));
        assertTrue(state.shouldSend(2_000L));
        state.markSent(2_000L);
        assertTrue(state.shouldSend(4_000L));
        state.markSent(4_000L);

        state.acknowledge(true, "ACCEPTED");
        assertFalse(state.shouldSend(20_000L));
        assertFalse(state.active());
    }

    @Test
    void retry_state_expires_at_the_finite_admission_deadline() {
        ClientReadyRetryState state = new ClientReadyRetryState();
        state.start(10_000L);
        assertTrue(state.active());
        assertFalse(state.shouldSend(25_000L));
        assertTrue(state.expired(25_000L));
        assertFalse(state.shouldSend(25_001L));
        assertTrue(state.expired(25_001L));
    }
}
