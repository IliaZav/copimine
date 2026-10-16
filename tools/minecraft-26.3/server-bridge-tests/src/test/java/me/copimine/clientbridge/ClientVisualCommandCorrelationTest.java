package me.copimine.clientbridge;

import me.copimine.protocol.NarcoticsBridgeBodyCodec;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ClientVisualCommandCorrelationTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final String STREAM = "AAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void startAckMustMatchPlayerSessionStreamSequenceAndExactEffectAndCannotReplay() {
        ClientVisualCommandCorrelation store = new ClientVisualCommandCorrelation();
        NarcoticsBridgeBodyCodec.Body body = start(10L, "INVERT", UUID.randomUUID(), 5_000L);
        assertTrue(store.register(PLAYER, SESSION, STREAM, body, 1_000L));
        assertTrue(store.mayRetry(PLAYER, SESSION, 10L, STREAM, 2_000L));

        assertTrue(store.accept(UUID.randomUUID(), SESSION, STREAM, ack(10L, "INVERT", "STARTED"), 2_000L).isEmpty());
        assertTrue(store.accept(PLAYER, UUID.randomUUID(), STREAM, ack(10L, "INVERT", "STARTED"), 2_000L).isEmpty());
        assertTrue(store.accept(PLAYER, SESSION, "BBBBBBBBBBBBBBBBBBBBBB", ack(10L, "INVERT", "STARTED"), 2_000L).isEmpty());
        assertTrue(store.accept(PLAYER, SESSION, STREAM, ack(11L, "INVERT", "STARTED"), 2_000L).isEmpty());
        assertTrue(store.accept(PLAYER, SESSION, STREAM, ack(10L, "CHAOS", "STARTED"), 2_000L).isEmpty());
        assertTrue(store.accept(PLAYER, SESSION, STREAM, ack(10L, "INVERT", "CLEARED"), 2_000L).isEmpty());

        var accepted = store.accept(PLAYER, SESSION, STREAM, ack(10L, "INVERT", "STARTED_FALLBACK_POST_PROCESS"), 2_000L);
        assertTrue(accepted.isPresent());
        assertEquals(ClientVisualCommandCorrelation.ReportKind.ACK, accepted.orElseThrow().kind());
        assertFalse(store.mayRetry(PLAYER, SESSION, 10L, STREAM, 2_001L));
        assertTrue(store.accept(PLAYER, SESSION, STREAM, ack(10L, "INVERT", "STARTED"), 2_002L).isEmpty());
        assertEquals(1, store.size(PLAYER, SESSION));
    }

    @Test
    void terminalReportsRequireTheMatchingStartedCommandAndRetireItsRecord() {
        ClientVisualCommandCorrelation store = new ClientVisualCommandCorrelation();
        NarcoticsBridgeBodyCodec.Body body = start(22L, "WOBBLE", UUID.randomUUID(), 5_000L);
        assertTrue(store.register(PLAYER, SESSION, STREAM, body, 1_000L));
        assertTrue(store.accept(PLAYER, SESSION, STREAM, finished(22L, "WOBBLE", "finished"), 2_000L).isEmpty());
        assertTrue(store.accept(PLAYER, SESSION, STREAM, error(22L, "WOBBLE", "bad-pipeline"), 2_000L).isPresent());
        assertEquals(0, store.size(PLAYER, SESSION));

        assertTrue(store.register(PLAYER, SESSION, STREAM, start(23L, "WOBBLE", UUID.randomUUID(), 5_000L), 3_000L));
        assertTrue(store.accept(PLAYER, SESSION, STREAM, ack(23L, "WOBBLE", "STARTED"), 3_001L).isPresent());
        assertEquals(ClientVisualCommandCorrelation.ReportKind.FINISHED,
                store.accept(PLAYER, SESSION, STREAM, finished(23L, "WOBBLE", "natural_end"), 4_000L)
                        .orElseThrow().kind());
        assertEquals(0, store.size(PLAYER, SESSION));
    }

    @Test
    void clearAndStopAcksUseExactReportStatusAndEmptyClearEffect() {
        ClientVisualCommandCorrelation store = new ClientVisualCommandCorrelation();
        NarcoticsBridgeBodyCodec.Body stop = new NarcoticsBridgeBodyCodec.Body(
                NarcoticsBridgeBodyCodec.Type.VISUAL_STOP, 31L, 1_000L, SESSION,
                "INVERT", "", 0L, 0.0f, 0L, 0L, "CLIENT_MOD", "", "", "admin_stop", "",
                new UUID(0L, 0L), UUID.randomUUID(), 0L);
        NarcoticsBridgeBodyCodec.Body clear = new NarcoticsBridgeBodyCodec.Body(
                NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL, 32L, 1_000L, SESSION,
                "", "", 0L, 0.0f, 0L, 0L, "CLIENT_MOD", "REPLACE_ALL_FULLSCREEN", "SYSTEM",
                "server_clear", "", new UUID(0L, 0L), new UUID(0L, 0L), 1L);
        assertTrue(store.register(PLAYER, SESSION, STREAM, stop, 1_000L));
        assertTrue(store.register(PLAYER, SESSION, STREAM, clear, 1_000L));
        assertTrue(store.accept(PLAYER, SESSION, STREAM, ack(31L, "", "STOPPED"), 1_001L).isEmpty());
        assertTrue(store.accept(PLAYER, SESSION, STREAM, ack(31L, "INVERT", "CLEARED"), 1_001L).isEmpty());
        assertTrue(store.accept(PLAYER, SESSION, STREAM, ack(31L, "INVERT", "STOPPED"), 1_002L).isPresent());
        assertTrue(store.accept(PLAYER, SESSION, STREAM, ack(32L, "INVERT", "CLEARED"), 1_003L).isEmpty());
        assertTrue(store.accept(PLAYER, SESSION, STREAM, ack(32L, "", "CLEARED"), 1_004L).isPresent());
        assertEquals(0, store.size(PLAYER, SESSION));
    }

    @Test
    void retriesStopAtTimeoutNewerCommandBoundAndStreamRetirement() {
        ClientVisualCommandCorrelation store = new ClientVisualCommandCorrelation();
        for (int sequence = 1; sequence <= 257; sequence++) {
            assertTrue(store.register(PLAYER, SESSION, STREAM,
                    start(sequence, "INVERT", UUID.randomUUID(), 10_000L), 1_000L + sequence));
        }
        assertFalse(store.mayRetry(PLAYER, SESSION, 1L, STREAM, 2_000L));
        assertTrue(store.mayRetry(PLAYER, SESSION, 2L, STREAM, 2_000L));
        assertFalse(store.mayRetry(PLAYER, SESSION, 257L, "BBBBBBBBBBBBBBBBBBBBBB", 2_000L));
        store.retireStream(PLAYER, SESSION, STREAM);
        assertFalse(store.mayRetry(PLAYER, SESSION, 257L, STREAM, 2_000L));

        assertTrue(store.register(PLAYER, SESSION, "CCCCCCCCCCCCCCCCCCCCCC",
                start(1000L, "INVERT", UUID.randomUUID(), 5_000L), 10_000L));
        assertFalse(store.mayRetry(PLAYER, SESSION, 1000L, "CCCCCCCCCCCCCCCCCCCCCC", 40_000L));
        assertTrue(store.accept(PLAYER, SESSION, "CCCCCCCCCCCCCCCCCCCCCC",
                ack(1000L, "INVERT", "STARTED"), 40_000L).isEmpty());
    }

    @Test
    void correlationCapacityIsBoundedPerRecipientAndSession() {
        ClientVisualCommandCorrelation store = new ClientVisualCommandCorrelation();
        for (int sequence = 1; sequence <= ClientVisualCommandCorrelation.MAX_RECORDS_PER_RECIPIENT_SESSION; sequence++) {
            assertTrue(store.register(PLAYER, SESSION, STREAM,
                    start(sequence, "INVERT", UUID.randomUUID(), 600_000L), 1_000L));
        }
        assertEquals(512, store.size(PLAYER, SESSION));
        assertFalse(store.register(PLAYER, SESSION, STREAM,
                start(513L, "INVERT", UUID.randomUUID(), 600_000L), 1_000L));
        assertTrue(store.register(UUID.randomUUID(), SESSION, STREAM,
                start(1L, "INVERT", UUID.randomUUID(), 600_000L), 1_000L));
    }

    private static NarcoticsBridgeBodyCodec.Body start(long sequence, String effect, UUID instance, long duration) {
        return new NarcoticsBridgeBodyCodec.Body(NarcoticsBridgeBodyCodec.Type.VISUAL_START,
                sequence, 1_000L, SESSION, effect, "", duration, 1.0f, 0L, 0L,
                "CLIENT_MOD", "PRESERVE", "TEST", "", "", instance, new UUID(0L, 0L), 0L);
    }

    private static ClientBridgePayloads.Message ack(long sequence, String effect, String status) {
        return new ClientBridgePayloads.Message(ClientBridgePayloads.VISUAL_ACK, 3, sequence, 2_000L,
                SESSION.toString(), "", false, false, false, false, Set.of(), effect, "", 0,
                0.0f, 0, 0, "CLIENT_MOD", "", "SYSTEM", "", status);
    }

    private static ClientBridgePayloads.Message finished(long sequence, String effect, String reason) {
        return new ClientBridgePayloads.Message(ClientBridgePayloads.VISUAL_FINISHED, 3, sequence, 2_000L,
                SESSION.toString(), "", false, false, false, false, Set.of(), effect, "", 0,
                0.0f, 0, 0, "CLIENT_MOD", "", "SYSTEM", reason, "");
    }

    private static ClientBridgePayloads.Message error(long sequence, String effect, String reason) {
        return new ClientBridgePayloads.Message(ClientBridgePayloads.VISUAL_ERROR, 3, sequence, 2_000L,
                SESSION.toString(), "", false, false, false, false, Set.of(), effect, "", 0,
                0.0f, 0, 0, "CLIENT_MOD", "", "SYSTEM", reason, "ERROR");
    }
}
