package me.copimine.client;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EchoPresentationPacketTest {
    private static final String EVENT = new UUID(0, 1).toString(), DUEL = new UUID(0, 2).toString();
    private static final String ACTOR = new UUID(0, 3).toString(), OWNER = new UUID(0, 4).toString();

    private static BridgePayload packet(String type, String fields, String dimension) {
        return new BridgePayload(type, 2, 7, 100, EVENT, DUEL, false, false, false,
                false, Set.of(), "", "END_RIFT_ECHO_V1", 2_000, 0, 0, 0,
                ACTOR, fields, "", "", dimension);
    }
    private static String fields() { return OWNER + "|10|1|CROUCHING|1|OFF|12|32|3|4|0|5"; }

    @Test void helloAndCapabilityUpdatesActuallyPreserveTheEchoFeature() {
        var capabilities = Set.of("ECHO_PRESENTATION_V1");
        assertTrue(BridgePayload.hello(EVENT, "test", capabilities, true, false, false)
                .supportedEffects().contains("ECHO_PRESENTATION_V1"));
        assertTrue(BridgePayload.capabilitiesUpdate(EVENT, "test", capabilities, true, false, false)
                .supportedEffects().contains("ECHO_PRESENTATION_V1"));
    }

    @Test void sharedEnvelopeRetainsAllIdentityAndUseFields() {
        var value = EchoPresentationPacket.decode(packet("END_EVENT:END_ECHO_BIND", fields(), "minecraft:overworld"));
        assertEquals("BIND", value.operation());
        var f = value.frame();
        assertEquals(UUID.fromString(EVENT), f.event()); assertEquals(7, f.generation());
        assertEquals(10, f.epoch()); assertEquals(UUID.fromString(DUEL), f.duel());
        assertEquals(UUID.fromString(ACTOR), f.actor()); assertEquals(UUID.fromString(OWNER), f.owner());
        assertEquals(EchoPresentationState.UseHand.OFF, f.hand()); assertEquals(12, f.useElapsed());
        assertEquals(32, f.useDuration()); assertEquals(3, f.swingSerial());
        assertEquals(4, f.hurtSerial()); assertEquals(5, f.equipmentVersion());
        assertEquals(EchoPresentationState.Pose.CROUCHING, f.pose()); assertTrue(f.sprinting());
    }

    @Test void malformedPacketsCannotBecomeBindings() {
        assertThrows(IllegalArgumentException.class, () -> EchoPresentationPacket.decode(
                packet("END_EVENT:END_ECHO_BIND", fields() + "|extra", "minecraft:overworld")));
        assertThrows(IllegalArgumentException.class, () -> EchoPresentationPacket.decode(
                packet("END_EVENT:END_ECHO_BIND", fields().replace("|1|OFF", "|yes|OFF"), "minecraft:overworld")));
        assertThrows(IllegalArgumentException.class, () -> EchoPresentationPacket.decode(
                packet("END_EVENT:END_ECHO_BIND", fields(), "overworld")));
        assertThrows(IllegalArgumentException.class, () -> EchoPresentationPacket.decode(
                packet("END_EVENT:END_ENTITY_BIND", fields(), "minecraft:overworld")));
    }
}
