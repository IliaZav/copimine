package me.copimine.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaveTwoPhantomActivationBodyCodecTest {
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000111");

    @Test
    void acceptsAndRoundTripsTheServerAuthoredActivationFields() {
        EndEventBridgeBodyCodec.Body active = activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 1_500,
                "the_end|wave2|8", "phantom-14", "12.5,64,-3.25", "ACTIVE");
        EndEventBridgeBodyCodec.Body clear = activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 0,
                "the_end|wave2|8", "phantom-14", "12.5,64,-3.25", "CLEAR");
        EndEventBridgeBodyCodec.Body shortWarning = activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 50,
                "the_end|wave2|8", "phantom-14", "12.5,64,-3.25", "ACTIVE");

        assertEquals(active, EndEventBridgeBodyCodec.decode(EndEventBridgeBodyCodec.encode(active)));
        assertEquals(clear, EndEventBridgeBodyCodec.decode(EndEventBridgeBodyCodec.encode(clear)));
        assertEquals(shortWarning, EndEventBridgeBodyCodec.decode(EndEventBridgeBodyCodec.encode(shortWarning)));
    }

    @Test
    void rejectsMalformedActivationIdentityPositionAndStateFields() {
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wrong-instance", 1_500,
                "the_end|wave2|8", "phantom-14", "12.5,64,-3.25", "ACTIVE")));
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 1_500,
                "the_end|wave2|8", "", "12.5,64,-3.25", "ACTIVE")));
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 1_500,
                "the_end|wave2|8", "phantom-14", "NaN,64,-3.25", "ACTIVE")));
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 1_500,
                "the_end|wave2|8", "phantom-14", "12.5,64", "ACTIVE")));
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 1_500,
                "the_end|wave2|8", "phantom 14", "12.5,64,-3.25", "ACTIVE")));
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 1_500,
                "the_end|wave2|8", "phantom-14", "12.5,4097,-3.25", "ACTIVE")));
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 15_001,
                "the_end|wave2|8", "phantom-14", "12.5,64,-3.25", "ACTIVE")));
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 0,
                "the_end|wave2|8", "phantom-14", "12.5,64,-3.25", "ACTIVE")));
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(activation(
                "WAVE2_PHANTOM_ACTIVATION_V1", "wave2-phantom-activation", 51,
                "the_end|wave2|8", "phantom-14", "12.5,64,-3.25", "ACTIVE")));
    }

    @Test
    void acceptsBoundedServerAuthoredHuntLayoutEntries() {
        EndEventBridgeBodyCodec.Body layoutEntry = new EndEventBridgeBodyCodec.Body(
                EndEventBridgeBodyCodec.Type.END_PLAYER_STATE, 4, 10_001, EVENT,
                "wave2-hunt-mark", "12.5,64,-3.25", "WAVE2_HUNT_MARK_V1", 8_000,
                0.25F, 0, 0, "the_end|wave2|8", "", "phantom-00", "", "ACTIVE");

        assertEquals(layoutEntry, EndEventBridgeBodyCodec.decode(
                EndEventBridgeBodyCodec.encode(layoutEntry)));
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(
                new EndEventBridgeBodyCodec.Body(
                        EndEventBridgeBodyCodec.Type.END_PLAYER_STATE, 4, 10_002, EVENT,
                        "wave2-hunt-mark", "12.5,64,-3.25", "WAVE2_HUNT_MARK_V1", 8_000,
                        0.25F, 0, 0, "the_end|wave2|8", "", "", "", "ACTIVE")));
    }

    private static EndEventBridgeBodyCodec.Body activation(String visualId, String instanceId,
            long durationMillis, String mode, String phantomId, String source, String status) {
        return new EndEventBridgeBodyCodec.Body(
                EndEventBridgeBodyCodec.Type.END_PLAYER_STATE, 4, 10_000, EVENT,
                instanceId, source, visualId, durationMillis, 0.0F, 0, 0,
                mode, "", phantomId, "", status);
    }
}
