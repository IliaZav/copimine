package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteVisualSequencePolicyTest {
    @Test
    void rejectsZeroAndNegativeServerSequencesButAcceptsPositiveIds() {
        assertFalse(RemoteVisualSequencePolicy.isValidServerSequence(-1L));
        assertFalse(RemoteVisualSequencePolicy.isValidServerSequence(0L));
        assertTrue(RemoteVisualSequencePolicy.isValidServerSequence(1L));
        assertTrue(RemoteVisualSequencePolicy.isValidServerSequence(Long.MAX_VALUE));
    }
}
