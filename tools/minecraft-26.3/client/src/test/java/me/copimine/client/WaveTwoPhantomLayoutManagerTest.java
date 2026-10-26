package me.copimine.client;

import me.copimine.protocol.EndEventBridgeBodyCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveTwoPhantomLayoutManagerTest {
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final UUID OTHER_EVENT = UUID.fromString("00000000-0000-0000-0000-000000000222");

    @Test
    void rendersOnlyACompleteStableServerAuthoredLayout() {
        WaveTwoPhantomLayoutManager manager = new WaveTwoPhantomLayoutManager();
        List<WaveTwoPhantomLayoutManager.Phantom> expected = new ArrayList<>();
        for (int index = 0; index < WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT; index++) {
            String id = phantomId(index);
            WaveTwoPhantomLayout.Position position = new WaveTwoPhantomLayout.Position(index + 0.5, 64, -3.5);
            expected.add(new WaveTwoPhantomLayoutManager.Phantom(id, position));
            assertTrue(manager.apply(packet(10_000 + index, 8, id, position, "ACTIVE"), 500 + index));
            if (index + 1 < WaveTwoPhantomLayout.MIN_PHANTOM_COUNT) {
                assertFalse(manager.isComplete("the_end", EVENT.toString(), 4, 8));
            } else {
                assertTrue(manager.isComplete("the_end", EVENT.toString(), 4, 8));
            }
        }

        assertTrue(manager.isComplete("the_end", EVENT.toString(), 4, 8));
        assertEquals(expected, manager.layout("the_end", EVENT.toString(), 4, 8));
        assertTrue(manager.apply(packet(10_100, 8, "phantom-00", expected.getFirst().position(), "ACTIVE"), 600));
        assertFalse(manager.apply(packet(10_101, 8, "phantom-00",
                new WaveTwoPhantomLayout.Position(90.5, 64, 90.5), "ACTIVE"), 601));
        assertEquals(expected, manager.layout("the_end", EVENT.toString(), 4, 8));
    }

    @Test
    void clearAndNewCycleDropOldServerPositionsAndRejectStaleEntries() {
        WaveTwoPhantomLayoutManager manager = new WaveTwoPhantomLayoutManager();
        for (int index = 0; index < WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT; index++) {
            assertTrue(manager.apply(packet(20_000 + index, 8, phantomId(index),
                    new WaveTwoPhantomLayout.Position(index + 0.5, 64, 0.5), "ACTIVE"), 1_000 + index));
        }
        assertTrue(manager.apply(packet(20_100, 8, "", null, "CLEAR"), 1_100));
        assertFalse(manager.isComplete("the_end", EVENT.toString(), 4, 8));
        assertFalse(manager.apply(packet(20_050, 8, phantomId(31),
                new WaveTwoPhantomLayout.Position(31.5, 64, 0.5), "ACTIVE"), 1_101));

        assertTrue(manager.apply(packet(20_200, 9, phantomId(0),
                new WaveTwoPhantomLayout.Position(1.5, 65, 0.5), "ACTIVE"), 1_200));
        assertFalse(manager.isComplete("the_end", EVENT.toString(), 4, 9));
        assertTrue(manager.apply(packet(20_201, 9, phantomId(1),
                new WaveTwoPhantomLayout.Position(2.5, 65, 0.5), "ACTIVE"), 1_201));
        assertEquals(2, manager.layout("the_end", EVENT.toString(), 4, 9).size());
        assertTrue(manager.layout("the_end", EVENT.toString(), 4, 8).isEmpty());
    }

    @Test
    void clearFromUnseenEventCannotRetireTheCurrentLayout() {
        WaveTwoPhantomLayoutManager manager = new WaveTwoPhantomLayoutManager();
        WaveTwoPhantomLayout.Position position = new WaveTwoPhantomLayout.Position(3.5, 65, -4.5);
        assertTrue(manager.apply(packet(30_000, 3, phantomId(0), position, "ACTIVE"), 2_000));

        assertFalse(manager.apply(packet(OTHER_EVENT, 30_001, 3, "", null, "CLEAR"), 2_001));
        assertEquals(List.of(new WaveTwoPhantomLayoutManager.Phantom(phantomId(0), position)),
                manager.layout("the_end", EVENT.toString(), 4, 3));
    }

    private static String phantomId(int index) {
        return "phantom-%02d".formatted(index);
    }

    private static EndEventBridgeBodyCodec.Body packet(long timestamp, int cycle, String phantomId,
            WaveTwoPhantomLayout.Position position, String status) {
        return packet(EVENT, timestamp, cycle, phantomId, position, status);
    }

    private static EndEventBridgeBodyCodec.Body packet(UUID eventUuid, long timestamp, int cycle,
            String phantomId, WaveTwoPhantomLayout.Position position, String status) {
        String source = position == null ? "" : position.x() + "," + position.y() + "," + position.z();
        return new EndEventBridgeBodyCodec.Body(
                EndEventBridgeBodyCodec.Type.END_PLAYER_STATE, 4, timestamp, eventUuid,
                "wave2-hunt-mark", source, "WAVE2_HUNT_MARK_V1", "ACTIVE".equals(status) ? 8_000 : 0,
                "ACTIVE".equals(status) ? 0.25F : 0.0F, 0, 0,
                "the_end|wave2|" + cycle, "", phantomId, "", status);
    }
}
