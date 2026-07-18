package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolInputBoundsTest {
    @Test
    void replacesNonFiniteIntensityWithSafeZero() {
        BridgePayload payload = new BridgePayload(
                ClientBridgeProtocol.TYPE_VISUAL_START,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                1L,
                1L,
                "session",
                "test",
                true,
                true,
                false,
                false,
                Set.of("WOBBLE"),
                "WOBBLE",
                "",
                10_000,
                Float.NaN,
                0,
                0,
                "",
                "",
                "",
                "",
                ""
        );
        ShaderEffectRequest request = new ShaderEffectRequest(
                1L,
                "WOBBLE",
                "",
                10_000,
                Float.NaN,
                0,
                0,
                "test"
        );

        assertEquals(0.0F, payload.intensity());
        assertEquals(0.0F, request.intensity());
    }

    @Test
    void acceptsVisualWhenTheClientOverlayIsAlreadyAvailable() throws Exception {
        ClientVisualManager manager = new ClientVisualManager(newConfig());

        boolean started = manager.start("WOBBLE", "", 1L, 10_000, 1.0F, "", 0, 0, "test");

        assertTrue(started);
        assertTrue(manager.hasActiveVisuals());
        assertEquals("CLIENT_OVERLAY", manager.activeRuntimeRouteName());
    }

    private ClientConfig newConfig() throws Exception {
        Constructor<ClientConfig> constructor = ClientConfig.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(Path.of("build", "test-client-config.properties"));
    }
}
