package me.copimine.client;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRiftGuardianShieldCandidatePolicyTest {
    @Test
    void capsShieldCarriersAtFour() {
        assertEquals(4, EndRiftGuardianShieldCandidatePolicy.MAX_CANDIDATES);
        Set<UUID> admitted = new LinkedHashSet<>();
        for (int index = 0; index < EndRiftGuardianShieldCandidatePolicy.MAX_CANDIDATES; index++) {
            assertTrue(EndRiftGuardianShieldCandidatePolicy.admit(
                    admitted, UUID.randomUUID(), true, false));
        }
        assertFalse(EndRiftGuardianShieldCandidatePolicy.admit(
                admitted, UUID.randomUUID(), false, true));
        assertEquals(EndRiftGuardianShieldCandidatePolicy.MAX_CANDIDATES, admitted.size());
    }

    @Test
    void boundsEntityInspectionCount() {
        int inspectionLimit = EndRiftGuardianShieldCandidatePolicy.MAX_ENTITY_INSPECTIONS;
        assertTrue(EndRiftGuardianShieldCandidatePolicy.canInspectEntity(inspectionLimit - 1));
        assertFalse(EndRiftGuardianShieldCandidatePolicy.canInspectEntity(inspectionLimit));
        assertFalse(EndRiftGuardianShieldCandidatePolicy.canInspectEntity(-1));
    }

    @Test
    void duplicateSourcesReuseTheCarrierSlot() {
        Set<UUID> admitted = new LinkedHashSet<>();
        UUID carrier = UUID.randomUUID();

        assertTrue(EndRiftGuardianShieldCandidatePolicy.admit(admitted, carrier, true, false));
        assertTrue(EndRiftGuardianShieldCandidatePolicy.admit(admitted, carrier, false, true));
        assertEquals(1, admitted.size());
    }

    @Test
    void rejectsUnmarkedAndNullCarriers() {
        Set<UUID> admitted = new LinkedHashSet<>();

        assertFalse(EndRiftGuardianShieldCandidatePolicy.admit(admitted, UUID.randomUUID(), false, false));
        assertFalse(EndRiftGuardianShieldCandidatePolicy.admit(admitted, null, true, true));
        assertTrue(admitted.isEmpty());
    }
}
