package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventWorldVfxManagerTest {
    @Test
    void acceptsBoundedBeamAndExpiresIt() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        BridgePayload beam = beam("event-1", 1L, "instance-1", "overworld|absorption|1,64,2",
                "4,65,6|4EE6FF", 0.12F, 500);

        assertTrue(manager.applyBeam(beam, 1_000L));
        assertEquals(1, manager.activeBeamCount());
        assertEquals(0x4EE6FF, manager.snapshots().get(0).color());
        assertEquals(0.12F, manager.snapshots().get(0).width(), 0.0001F);
        manager.tick(2_000L);
        assertEquals(0, manager.activeBeamCount());
    }

    @Test
    void rejectsStaleGenerationAndInvalidCoordinates() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        assertTrue(manager.applyBeam(beam("event-1", 2L, "instance-1", "overworld|absorption|1,64,2",
                "4,65,6|4EE6FF", 0.12F, 500), 100L));
        assertFalse(manager.applyBeam(beam("event-1", 1L, "instance-old", "overworld|absorption|NaN,64,2",
                "4,65,6|4EE6FF", 0.12F, 500), 200L));
        assertEquals(1, manager.activeBeamCount());
    }

    @Test
    void enforcesActiveBeamCapButAllowsRefreshOfExistingInstance() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        for (int index = 0; index < EndEventWorldVfxManager.MAX_ACTIVE_BEAMS; index++) {
            assertTrue(manager.applyBeam(beam("event-1", 1L, "beam-" + index,
                    "overworld|key-" + index + "|1,64,2", "4,65,6|4EE6FF", 0.12F, 1_000), 100L));
        }
        assertFalse(manager.applyBeam(beam("event-1", 1L, "beam-over-cap",
                "overworld|overflow|1,64,2", "4,65,6|4EE6FF", 0.12F, 1_000), 100L));
        assertTrue(manager.applyBeam(beam("event-1", 1L, "beam-0",
                "overworld|key-0|2,64,2", "5,65,6|69DAFF", 0.20F, 1_000), 200L));
        assertEquals(EndEventWorldVfxManager.MAX_ACTIVE_BEAMS, manager.activeBeamCount());
    }

    @Test
    void clearsOnlyTheServerSelectedInstance() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        manager.applyBeam(beam("event-1", 1L, "beam-1", "overworld|key-1|1,64,2",
                "4,65,6|4EE6FF", 0.12F, 1_000), 100L);
        manager.applyBeam(beam("event-1", 1L, "beam-2", "overworld|key-2|1,64,2",
                "4,65,6|4EE6FF", 0.12F, 1_000), 100L);
        assertTrue(manager.applyClear(clear("event-1", 1L, "beam-1"), 200L));
        assertEquals(1, manager.activeBeamCount());
    }

    private static BridgePayload beam(String eventId, long generation, String instance,
                                      String mode, String end, float width, int duration) {
        return new BridgePayload("END_EVENT:END_WORLD_BEAM", 2, generation, 1L,
                eventId, instance, false, false, false, false, Set.of(), "", "RIFT_BEAM",
                duration, width, 0, 0, mode, end, "", "", "");
    }

    private static BridgePayload clear(String eventId, long generation, String instance) {
        return new BridgePayload("END_EVENT:END_WORLD_VFX_CLEAR", 2, generation, 1L,
                eventId, instance, false, false, false, false, Set.of(), "", "RIFT_BEAM",
                1_000, 0.0F, 0, 0, "", "", "", "", "");
    }
}
