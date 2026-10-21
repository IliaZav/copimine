package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientBridgeConnectionStateTest {
    private static final UUID PLAYER_ID = UUID.fromString("3b71e30a-8ee8-42fd-b67e-d6757ec5b9b1");
    private static final UUID SESSION_ONE = UUID.fromString("cc50fd27-f74e-46e8-9c13-aed43590d58a");
    private static final UUID SESSION_TWO = UUID.fromString("bdc443a5-95b0-45b4-a2ad-3a6cb4ad77c8");
    private static final UUID EVENT_ID = UUID.fromString("0b1306dc-41bc-41ed-a054-1c2550d0db8e");

    @Test
    void openCreatesFreshEpochAndCanonicalThirtyTwoByteNonce() {
        ClientBridgeConnectionState state = new ClientBridgeConnectionState();

        ClientBridgeConnectionState.Connection first = state.open(PLAYER_ID);
        ClientBridgeConnectionState.Connection second = state.open(PLAYER_ID);
        ClientBridgeConnectionState.Connection third = new ClientBridgeConnectionState().open(PLAYER_ID);

        assertEquals(PLAYER_ID, first.playerUuid());
        assertEquals(PLAYER_ID, second.playerUuid());
        assertTrue(first.localEpoch() > 0);
        assertTrue(second.localEpoch() > first.localEpoch());
        assertTrue(third.localEpoch() > second.localEpoch());
        assertCanonicalNonce(first.nonce());
        assertCanonicalNonce(second.nonce());
        assertNotEquals(first.nonce(), second.nonce());
        assertEquals(second, state.currentConnection().orElseThrow());
    }

    @Test
    void sameVerifiedSessionIsIdempotentButReplacementResetsSessionStateAndInvalidatesQueuedWork() {
        ClientBridgeConnectionState state = openAndVerify(SESSION_ONE);
        ClientBridgeConnectionState.Connection connection = state.currentConnection().orElseThrow();
        UUID endEventStream = streamId(11);
        UUID narcoticsStream = streamId(12);
        String endEventStreamText = encodeStreamId(endEventStream);
        String narcoticsStreamText = encodeStreamId(narcoticsStream);

        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, endEventStreamText));
        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.NARCOTICS, narcoticsStreamText));
        state.setReady(ClientBridgeConnectionState.Sender.END_EVENT, true);
        state.setReady(ClientBridgeConnectionState.Sender.NARCOTICS, true);
        state.setOutstandingRequestId(ClientBridgeConnectionState.Sender.END_EVENT, streamId(21));
        state.setOutstandingRequestId(ClientBridgeConnectionState.Sender.NARCOTICS, streamId(22));
        state.setControlHelloId(streamId(23));
        assertEquals(1, state.nextRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(1, state.nextRequestCounter(ClientBridgeConnectionState.Sender.NARCOTICS));
        assertTrue(state.recordGeneration(4));
        ClientBridgeConnectionState.QueueTicket queuedBeforeSameSession = state.captureQueueTicket();
        ClientBridgeConnectionState.QueueTicket queuedBeforeReplacement = state.captureQueueTicket();

        state.verifiedSessionChanged(SESSION_ONE);

        assertTrue(state.isCurrent(queuedBeforeSameSession));
        assertEquals(Optional.of(endEventStreamText), state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(1, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(Optional.of(streamId(23)), state.controlHelloId());

        state.verifiedSessionChanged(SESSION_TWO);

        assertFalse(state.isCurrent(queuedBeforeReplacement));
        assertEquals(connection, state.currentConnection().orElseThrow());
        assertEquals(Optional.of(SESSION_TWO), state.verifiedSession());
        assertEquals(Optional.empty(), state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(Optional.empty(), state.activeStream(ClientBridgeConnectionState.Sender.NARCOTICS));
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.END_EVENT));
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.NARCOTICS));
        assertEquals(Optional.empty(), state.outstandingRequestId(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(Optional.empty(), state.outstandingRequestId(ClientBridgeConnectionState.Sender.NARCOTICS));
        assertEquals(Optional.empty(), state.controlHelloId());
        assertEquals(0, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(0, state.lastRequestCounter(ClientBridgeConnectionState.Sender.NARCOTICS));
        assertEquals(1, state.seenStreamCount(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(1, state.seenStreamCount(ClientBridgeConnectionState.Sender.NARCOTICS));
        assertFalse(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, endEventStreamText));
        assertFalse(state.installStream(ClientBridgeConnectionState.Sender.NARCOTICS, narcoticsStreamText));
        assertEquals(OptionalLong.of(4), state.generationHighWater());
        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT,
                encodeStreamId(streamId(113))));
        assertEquals(OptionalLong.empty(), state.generationHighWater());
        assertTrue(state.recordGeneration(3));
        assertEquals(OptionalLong.of(3), state.generationHighWater());

        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, encodeStreamId(streamId(13))));
        assertEquals(1, state.nextRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, encodeStreamId(streamId(14))));
        assertEquals(2, state.nextRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
    }

    @Test
    void streamIdsHavePerSenderLifetimeCapsAndConnectionWideReplayProtection() {
        ClientBridgeConnectionState state = openAndVerify(SESSION_ONE);
        String firstEndEventId = null;
        String firstNarcoticsId = null;

        for (int index = 0; index < 256; index++) {
            String endEventId = encodeStreamId(streamId(1000 + index));
            String narcoticsId = encodeStreamId(streamId(2000 + index));
            if (index == 0) {
                firstEndEventId = endEventId;
                firstNarcoticsId = narcoticsId;
            }
            assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, endEventId));
            assertTrue(state.installStream(ClientBridgeConnectionState.Sender.NARCOTICS, narcoticsId));
        }

        assertEquals(256, state.seenStreamCount(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(256, state.seenStreamCount(ClientBridgeConnectionState.Sender.NARCOTICS));
        assertFalse(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, encodeStreamId(streamId(3000))));
        assertFalse(state.installStream(ClientBridgeConnectionState.Sender.NARCOTICS, encodeStreamId(streamId(3001))));
        assertFalse(state.installStream(ClientBridgeConnectionState.Sender.NARCOTICS, firstEndEventId));
        assertFalse(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, firstNarcoticsId));

        state.verifiedSessionChanged(SESSION_TWO);
        assertFalse(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, firstEndEventId));
        assertFalse(state.installStream(ClientBridgeConnectionState.Sender.NARCOTICS, firstNarcoticsId));
        assertEquals(256, state.seenStreamCount(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(256, state.seenStreamCount(ClientBridgeConnectionState.Sender.NARCOTICS));

        state.open(PLAYER_ID);
        state.verifiedSessionChanged(SESSION_ONE);
        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, firstEndEventId));
        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.NARCOTICS, firstNarcoticsId));
    }

    @Test
    void generationHighWaterUsesConstantSpaceAndResetsOnlyForANewEndEventStream() {
        ClientBridgeConnectionState state = openAndVerify(SESSION_ONE);
        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT,
                encodeStreamId(streamId(991))));

        assertFalse(state.recordGeneration(Long.MAX_VALUE));
        assertEquals(OptionalLong.empty(), state.generationHighWater());

        for (int generation = 1; generation <= 96; generation++) {
            assertTrue(state.recordGeneration(generation));
        }
        assertTrue(state.recordGeneration(96));
        assertFalse(state.recordGeneration(95));
        assertEquals(OptionalLong.of(96), state.generationHighWater());

        state.verifiedSessionChanged(SESSION_TWO);
        assertEquals(OptionalLong.of(96), state.generationHighWater());
        assertFalse(state.recordGeneration(1));
        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT,
                encodeStreamId(streamId(997))));
        assertEquals(OptionalLong.empty(), state.generationHighWater());
        assertTrue(state.recordGeneration(2));

        state.close();
        state.open(PLAYER_ID);
        state.verifiedSessionChanged(SESSION_ONE);
        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT,
                encodeStreamId(streamId(998))));
        assertEquals(OptionalLong.empty(), state.generationHighWater());
        assertTrue(state.recordGeneration(1));
    }

    @Test
    void closeAndReopenClearAllConnectionScopedStateAndInvalidateOldQueueTickets() {
        ClientBridgeConnectionState state = openAndVerify(SESSION_ONE);
        String streamText = encodeStreamId(streamId(41));
        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, streamText));
        state.setReady(ClientBridgeConnectionState.Sender.END_EVENT, true);
        state.setOutstandingRequestId(ClientBridgeConnectionState.Sender.END_EVENT, streamId(42));
        state.setControlHelloId(streamId(43));
        assertEquals(1, state.nextRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertTrue(state.recordGeneration(6));
        ClientBridgeConnectionState.QueueTicket queued = state.captureQueueTicket();
        long closedEpoch = state.currentConnection().orElseThrow().localEpoch();

        state.close();

        assertFalse(state.isCurrent(queued));
        assertEquals(Optional.empty(), state.currentConnection());
        assertEquals(Optional.empty(), state.verifiedSession());
        assertEquals(Optional.empty(), state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT));
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(Optional.empty(), state.outstandingRequestId(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(Optional.empty(), state.controlHelloId());
        assertEquals(0, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(0, state.seenStreamCount(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(OptionalLong.empty(), state.generationHighWater());

        ClientBridgeConnectionState.Connection reopened = state.open(PLAYER_ID);
        assertTrue(reopened.localEpoch() > closedEpoch);
        assertTrue(state.isCurrent(state.captureQueueTicket()));
        state.verifiedSessionChanged(SESSION_ONE);
        assertTrue(state.installStream(ClientBridgeConnectionState.Sender.END_EVENT, streamText));
        assertTrue(state.recordGeneration(1));
    }

    @Test
    void playerChangeUsesANewLocalEpochAndCannotValidatePriorQueueTicket() {
        ClientBridgeConnectionState state = openAndVerify(SESSION_ONE);
        ClientBridgeConnectionState.QueueTicket firstConnection = state.captureQueueTicket();
        long firstEpoch = state.currentConnection().orElseThrow().localEpoch();
        UUID nextPlayer = UUID.fromString("34089156-396d-40a8-b947-1e266867a36e");

        ClientBridgeConnectionState.Connection next = state.open(nextPlayer);

        assertEquals(nextPlayer, next.playerUuid());
        assertTrue(next.localEpoch() > firstEpoch);
        assertFalse(state.isCurrent(firstConnection));
        assertEquals(0, state.seenStreamCount(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(OptionalLong.empty(), state.generationHighWater());
    }

    @Test
    void requestCounterRefusesLongMaxAndLeavesSenderUnavailable() throws ReflectiveOperationException {
        ClientBridgeConnectionState state = openAndVerify(SESSION_ONE);
        assertTrue(state.installStream(
                ClientBridgeConnectionState.Sender.END_EVENT,
                encodeStreamId(streamId(501))));
        state.setReady(ClientBridgeConnectionState.Sender.END_EVENT, true);
        setRequestCounter(state, ClientBridgeConnectionState.Sender.END_EVENT, Long.MAX_VALUE - 1);

        assertThrows(
                IllegalStateException.class,
                () -> state.nextRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(Long.MAX_VALUE - 1, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(Optional.empty(), state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT));
        assertThrows(
                IllegalStateException.class,
                () -> state.nextRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));

        state.verifiedSessionChanged(SESSION_TWO);
        assertTrue(state.installStream(
                ClientBridgeConnectionState.Sender.END_EVENT,
                encodeStreamId(streamId(502))));
        state.setReady(ClientBridgeConnectionState.Sender.END_EVENT, true);
        setRequestCounter(state, ClientBridgeConnectionState.Sender.END_EVENT, Long.MAX_VALUE);

        assertThrows(
                IllegalStateException.class,
                () -> state.nextRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(Long.MAX_VALUE, state.lastRequestCounter(ClientBridgeConnectionState.Sender.END_EVENT));
        assertFalse(state.isReady(ClientBridgeConnectionState.Sender.END_EVENT));
        assertEquals(Optional.empty(), state.activeStream(ClientBridgeConnectionState.Sender.END_EVENT));
    }

    private static ClientBridgeConnectionState openAndVerify(UUID sessionId) {
        ClientBridgeConnectionState state = new ClientBridgeConnectionState();
        state.open(PLAYER_ID);
        state.verifiedSessionChanged(sessionId);
        return state;
    }

    private static void assertCanonicalNonce(String nonce) {
        assertEquals(43, nonce.length());
        assertTrue(nonce.matches("[A-Za-z0-9_-]{43}"));
        byte[] decoded = Base64.getUrlDecoder().decode(nonce);
        assertEquals(32, decoded.length);
        assertEquals(nonce, Base64.getUrlEncoder().withoutPadding().encodeToString(decoded));
    }

    private static UUID streamId(long lowBits) {
        return new UUID(0x0102030405060708L, lowBits);
    }

    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0x1020304050607080L, leastSignificantBits);
    }

    private static String encodeStreamId(UUID streamId) {
        byte[] bytes = ByteBuffer.allocate(16)
                .putLong(streamId.getMostSignificantBits())
                .putLong(streamId.getLeastSignificantBits())
                .array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void setRequestCounter(
            ClientBridgeConnectionState state,
            ClientBridgeConnectionState.Sender sender,
            long requestCounter) throws ReflectiveOperationException {
        Field sendersField = ClientBridgeConnectionState.class.getDeclaredField("senders");
        sendersField.setAccessible(true);
        Object senderState = ((Map<?, ?>) sendersField.get(state)).get(sender);
        Field counterField = senderState.getClass().getDeclaredField("requestCounter");
        counterField.setAccessible(true);
        counterField.setLong(senderState, requestCounter);
    }
}
