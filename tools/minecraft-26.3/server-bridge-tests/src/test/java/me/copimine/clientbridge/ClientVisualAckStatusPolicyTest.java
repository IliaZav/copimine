package me.copimine.clientbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientVisualAckStatusPolicyTest {
    @Test
    void plainStartedAckIsNotMisclassifiedAsRouteFallback() {
        assertFalse(ClientVisualAckStatusPolicy.shouldReportNonIrisFallback("STARTED"));
    }

    @Test
    void nonIrisClientRouteIsReportedAsFallback() {
        assertTrue(ClientVisualAckStatusPolicy.shouldReportNonIrisFallback("STARTED_POST_PROCESS"));
    }

    @Test
    void irisAndDeferredRoutesAreNotReportedAsFallback() {
        assertFalse(ClientVisualAckStatusPolicy.shouldReportNonIrisFallback("STARTED_IRIS_SHADERPACK"));
        assertFalse(ClientVisualAckStatusPolicy.shouldReportNonIrisFallback("STARTED_TRANSITION_DEFERRED"));
    }
}
