package me.copimine.client;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRiftTentacleCandidatePolicyTest {
    @Test
    void markerOnlyCarrierIsAdmittedWithoutBridgeBinding() {
        Set<UUID> admitted = new LinkedHashSet<>();
        UUID markerOnly = UUID.randomUUID();

        assertTrue(EndRiftTentacleCandidatePolicy.admit(admitted, markerOnly, false, true));
        assertTrue(admitted.contains(markerOnly));
    }

    @Test
    void duplicateBridgeAndMarkerSourcesConsumeOneSlot() {
        Set<UUID> admitted = new LinkedHashSet<>();
        UUID carrier = UUID.randomUUID();

        assertTrue(EndRiftTentacleCandidatePolicy.admit(admitted, carrier, true, false));
        assertTrue(EndRiftTentacleCandidatePolicy.admit(admitted, carrier, false, true));

        assertEquals(1, admitted.size());
        assertTrue(admitted.contains(carrier));
    }

    @Test
    void rejectsCarriersBeyondTheSharedRenderAndSuppressionBudget() {
        Set<UUID> admitted = new LinkedHashSet<>();
        for (int i = 0; i < EndRiftTentacleCandidatePolicy.MAX_CANDIDATES; i++) {
            assertTrue(EndRiftTentacleCandidatePolicy.admit(admitted, UUID.randomUUID(), true, false));
        }
        UUID overflow = UUID.randomUUID();

        assertFalse(EndRiftTentacleCandidatePolicy.admit(admitted, overflow, false, true));
        assertFalse(admitted.contains(overflow));
        assertEquals(EndRiftTentacleCandidatePolicy.MAX_CANDIDATES, admitted.size());
    }

    @Test
    void doesNotAdmitUnmarkedUnboundDisplays() {
        Set<UUID> admitted = new LinkedHashSet<>();

        assertFalse(EndRiftTentacleCandidatePolicy.admit(admitted, UUID.randomUUID(), false, false));
        assertFalse(EndRiftTentacleCandidatePolicy.admit(admitted, null, true, true));
        assertTrue(admitted.isEmpty());
    }

    @Test
    void boundsTheNumberOfWorldEntitiesExaminedForMarkerOnlyCarriers() {
        int limit = EndRiftTentacleCandidatePolicy.MAX_ENTITY_INSPECTIONS;

        assertTrue(EndRiftTentacleCandidatePolicy.canInspectEntity(limit - 1));
        assertFalse(EndRiftTentacleCandidatePolicy.canInspectEntity(limit));
        assertFalse(EndRiftTentacleCandidatePolicy.canInspectEntity(-1));
    }
}
