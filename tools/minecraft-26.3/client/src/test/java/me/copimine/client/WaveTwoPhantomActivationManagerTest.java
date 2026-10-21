package me.copimine.client;

import me.copimine.protocol.EndEventBridgeBodyCodec;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveTwoPhantomActivationManagerTest {
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000111");

    @Test
    void acceptsOneServerSelectedWarningAndKeepsItsTokenAcrossRefresh() {
        WaveTwoPhantomActivationManager manager = new WaveTwoPhantomActivationManager();

        assertTrue(manager.apply(packet(EVENT, 4, 10_000, 8, "phantom-14", "12.5,64,-3.25", "ACTIVE", 1_500), 500));
        WaveTwoPhantomActivationManager.Activation first = manager.active("the_end", 700);
        assertNotNull(first);
        assertEquals("phantom-14", first.phantomId());
        assertEquals(new WaveTwoPhantomLayout.Position(12.5, 64.0, -3.25), first.position());

        assertTrue(manager.apply(packet(EVENT, 4, 10_100, 8, "phantom-14", "12.5,64,-3.25", "ACTIVE", 1_500), 800));
        WaveTwoPhantomActivationManager.Activation refreshed = manager.active("the_end", 900);
        assertNotNull(refreshed);
        assertEquals(first.warningToken(), refreshed.warningToken());
        assertFalse(manager.apply(packet(EVENT, 4, 10_100, 8, "phantom-15", "13.5,64,-3.25", "ACTIVE", 1_500), 900));
    }

    @Test
    void clearsOnlyTheMatchingWarningAndNeverReusesItsPhantomWithinTheCycle() {
        WaveTwoPhantomActivationManager manager = new WaveTwoPhantomActivationManager();
        assertTrue(manager.apply(packet(EVENT, 4, 10_000, 8, "phantom-14", "12.5,64,-3.25", "ACTIVE", 1_500), 500));

        assertTrue(manager.apply(packet(EVENT, 4, 10_100, 8, "phantom-other", "14.5,64,-3.25", "CLEAR", 0), 600));
        assertNotNull(manager.active("the_end", 700));
        assertTrue(manager.apply(packet(EVENT, 4, 10_200, 8, "phantom-14", "12.5,64,-3.25", "CLEAR", 0), 800));
        assertNull(manager.active("the_end", 801));

        assertFalse(manager.apply(packet(EVENT, 4, 10_300, 8, "phantom-14", "12.5,64,-3.25", "ACTIVE", 1_500), 900));
        assertTrue(manager.apply(packet(EVENT, 4, 10_400, 8, "phantom-15", "13.5,64,-3.25", "ACTIVE", 1_500), 1_000));
        assertEquals("phantom-15", manager.active("the_end", 1_001).phantomId());
    }

    @Test
    void fencesExpiredWarningsOldCyclesAndRetiredEventIdentities() {
        WaveTwoPhantomActivationManager manager = new WaveTwoPhantomActivationManager();
        assertTrue(manager.apply(packet(EVENT, 4, 10_000, 8, "phantom-14", "12.5,64,-3.25", "ACTIVE", 1_500), 500));
        assertNull(manager.active("the_end", 2_000));
        assertFalse(manager.apply(packet(EVENT, 4, 10_100, 7, "phantom-15", "13.5,64,-3.25", "ACTIVE", 1_500), 2_001));
        assertTrue(manager.apply(packet(EVENT, 4, 10_200, 9, "phantom-15", "13.5,64,-3.25", "ACTIVE", 1_500), 2_010));

        UUID nextEvent = UUID.fromString("00000000-0000-0000-0000-000000000222");
        assertFalse(manager.apply(packet(nextEvent, 1, 10_199, 1, "phantom-1", "1.5,64,1.5", "ACTIVE", 1_500), 2_020));
        assertTrue(manager.apply(packet(nextEvent, 1, 10_300, 1, "phantom-1", "1.5,64,1.5", "ACTIVE", 1_500), 2_030));
        assertFalse(manager.apply(packet(EVENT, 4, 10_400, 9, "phantom-15", "13.5,64,-3.25", "ACTIVE", 1_500), 2_040));
        assertEquals(nextEvent.toString(), manager.active("the_end", 2_050).eventId());
    }

    @Test
    void newCycleReplacesThePreviousWarningAndLocalClearDoesNotReopenItsPhantom() {
        WaveTwoPhantomActivationManager manager = new WaveTwoPhantomActivationManager();
        assertTrue(manager.apply(packet(EVENT, 4, 10_000, 8, "phantom-14", "12.5,64,-3.25", "ACTIVE", 1_500), 500));

        assertTrue(manager.apply(packet(EVENT, 4, 10_100, 9, "phantom-15", "13.5,64,-3.25", "ACTIVE", 1_500), 600));
        WaveTwoPhantomActivationManager.Activation rotated = manager.active("the_end", 700);
        assertEquals("phantom-15", rotated.phantomId());
        assertTrue(manager.apply(packet(EVENT, 4, 10_200, 9, "phantom-15", "13.5,64,-3.25", "ACTIVE", 1_500), 800));
        assertEquals(rotated.warningToken(), manager.active("the_end", 801).warningToken());
        assertFalse(manager.apply(packet(EVENT, 4, 10_300, 8, "phantom-16", "15.5,64,-3.25", "ACTIVE", 1_500), 900));

        manager.clearPresentation();
        assertNull(manager.active("the_end", 901));
        assertFalse(manager.apply(packet(EVENT, 4, 10_400, 9, "phantom-15", "13.5,64,-3.25", "ACTIVE", 1_500), 1_000));
        assertTrue(manager.apply(packet(EVENT, 4, 10_500, 9, "phantom-16", "15.5,64,-3.25", "ACTIVE", 1_500), 1_100));
        assertEquals("phantom-16", manager.active("the_end", 1_101).phantomId());
    }

    @Test
    void retainsTheExactServerWarningLeaseInMillis() {
        WaveTwoPhantomActivationManager manager = new WaveTwoPhantomActivationManager();
        assertTrue(manager.apply(packet(EVENT, 4, 10_000, 8, "phantom-14", "12.5,64,-3.25", "ACTIVE", 50), 100));

        assertEquals(150L, manager.active("the_end", 149).expiresAtMillis());
        assertNull(manager.active("the_end", 150));
    }

    private static EndEventBridgeBodyCodec.Body packet(UUID event, long generation, long timestamp,
            int cycle, String phantomId, String source, String status, long duration) {
        return new EndEventBridgeBodyCodec.Body(
                EndEventBridgeBodyCodec.Type.END_PLAYER_STATE, generation, timestamp, event,
                "wave2-phantom-activation", source, "WAVE2_PHANTOM_ACTIVATION_V1", duration,
                0.0F, 0, 0, "the_end|wave2|" + cycle, "", phantomId, "", status);
    }
}
