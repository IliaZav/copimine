package me.copimine.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EchoPresentationStateTest {
    private static final UUID EVENT = new UUID(0, 1), DUEL = new UUID(0, 2);
    private static final UUID ACTOR = new UUID(0, 3), OWNER = new UUID(0, 4);

    private static EchoPresentationState.Frame frame(long epoch, long sequence, int elapsed, int duration) {
        return new EchoPresentationState.Frame(EVENT, 7, epoch, DUEL, ACTOR, OWNER,
                "minecraft:overworld", sequence, EchoPresentationState.Pose.STANDING, false,
                EchoPresentationState.UseHand.MAIN, elapsed, duration, 1, 2, 0, 3);
    }

    @Test void replicaIdentityNeverUsesTheOwnerOrCarrierIdentity() {
        var value = frame(10, 1, 0, 32);
        assertNotEquals(OWNER, value.presentationId());
        assertNotEquals(ACTOR, value.presentationId());
        assertEquals(value.presentationId(), frame(10, 2, 4, 32).presentationId());
        assertNotEquals(value.presentationId(), frame(11, 1, 0, 32).presentationId());
    }

    @Test void useProgressIsAuthoritativeAndCannotFinishOrRestartAnItem() {
        var state = new EchoPresentationState();
        assertTrue(state.bind(frame(10, 1, 12, 32), 100));
        assertEquals(12, state.view(ACTOR, "minecraft:overworld", 100).useElapsed());
        assertEquals(14, state.view(ACTOR, "minecraft:overworld", 200).useElapsed());
        assertEquals(32, state.view(ACTOR, "minecraft:overworld", 1_400).useElapsed());
        assertFalse(state.update(frame(10, 1, 0, 32), 200));
        assertEquals(14, state.view(ACTOR, "minecraft:overworld", 200).useElapsed());
    }

    @Test void aStatePacketCannotCreateAnActorAndRemovalIsTerminal() {
        var state = new EchoPresentationState();
        assertFalse(state.update(frame(10, 1, 0, 32), 100));
        assertTrue(state.bind(frame(10, 1, 0, 32), 100));
        var textureTicket = state.textureTicket(ACTOR);
        assertTrue(state.remove(frame(10, 2, 0, 0)));
        assertNull(state.view(ACTOR, "minecraft:overworld", 101));
        assertFalse(state.bind(frame(10, 3, 0, 32), 102));
        assertFalse(state.acceptsTexture(textureTicket));
    }

    @Test void removeBeforeBindAlsoFencesALateCreation() {
        var state = new EchoPresentationState();
        assertTrue(state.remove(frame(10, 2, 0, 0)));
        assertFalse(state.bind(frame(10, 1, 0, 32), 100));
        assertFalse(state.bind(frame(10, 3, 0, 32), 101));
        assertTrue(state.bind(frame(11, 1, 0, 32), 102));
    }

    @Test void actorCapacityDoesNotEvictAStillLiveReplica() {
        var state = new EchoPresentationState();
        for (int i = 0; i < EchoPresentationState.MAX_ACTORS; i++) {
            var f = frame(10, 1, 0, 32);
            assertTrue(state.bind(new EchoPresentationState.Frame(f.event(), f.generation(), f.epoch(),
                    f.duel(), new UUID(0, 100 + i), f.owner(), f.dimension(), f.sequence(), f.pose(),
                    f.sprinting(), f.hand(), f.useElapsed(), f.useDuration(), f.swingSerial(),
                    f.hurtSerial(), f.deathTicks(), f.equipmentVersion()), 100));
        }
        assertFalse(state.bind(frame(10, 1, 0, 32), 100));
        assertNotNull(state.view(new UUID(0, 100), "minecraft:overworld", 101));
    }

    @Test void newerEpochAtomicallyClearsOldViewsAndTextureTickets() {
        var state = new EchoPresentationState();
        assertTrue(state.bind(frame(10, 8, 0, 32), 100));
        var old = state.textureTicket(ACTOR);
        assertTrue(state.bind(frame(11, 1, 0, 32), 101));
        assertFalse(state.update(frame(10, 999, 0, 32), 102));
        assertFalse(state.remove(frame(10, 999, 0, 0)));
        assertFalse(state.acceptsTexture(old));
        assertTrue(state.acceptsTexture(state.textureTicket(ACTOR)));
    }

    @Test void dimensionAndDeadlineNeverExposeAStaleBody() {
        var state = new EchoPresentationState();
        assertTrue(state.bind(frame(10, 1, 0, 32), 100));
        assertNull(state.view(ACTOR, "minecraft:the_end", 101));
        assertNull(state.view(ACTOR, "minecraft:overworld", 2_100));
        assertFalse(state.update(frame(10, 2, 0, 32), 2_101));
        assertFalse(state.bind(frame(10, 3, 0, 32), 2_102));
    }

    @Test void transportResetAndLocalClearHaveDifferentStalePacketSemantics() {
        var state = new EchoPresentationState();
        assertTrue(state.bind(frame(10, 1, 0, 32), 100));
        state.clear();
        assertFalse(state.bind(frame(10, 2, 0, 32), 101));
        state.reset();
        assertTrue(state.bind(frame(10, 1, 0, 32), 102));
    }

    @Test void clearingBeforeTheFirstSceneDoesNotPreventItsLegitimateBinding() {
        var state = new EchoPresentationState();
        state.clear();
        assertTrue(state.bind(frame(10, 1, 0, 32), 100));
    }

    @Test void nativeCarrierUnloadClosesItsIdentityAndPendingSkinResult() {
        var state = new EchoPresentationState();
        assertTrue(state.bind(frame(10, 1, 0, 32), 100));
        var texture = state.textureTicket(ACTOR);
        state.retire(ACTOR);
        assertNull(state.view(ACTOR, "minecraft:overworld", 101));
        assertFalse(state.update(frame(10, 2, 0, 32), 102));
        assertFalse(state.bind(frame(10, 3, 0, 32), 103));
        assertFalse(state.acceptsTexture(texture));
        assertTrue(state.bind(frame(11, 1, 0, 32), 104));
    }

    @Test void invalidUseAndIdentityAreRejectedWithoutNormalizingIntoAnActor() {
        assertThrows(IllegalArgumentException.class, () -> frame(0, 1, 0, 32));
        assertThrows(IllegalArgumentException.class, () -> frame(10, 0, 0, 32));
        assertThrows(IllegalArgumentException.class, () -> frame(10, 1, 33, 32));
        assertThrows(IllegalArgumentException.class, () -> frame(10, 1, 0, 72_001));
        assertThrows(IllegalArgumentException.class, () -> new EchoPresentationState.Frame(EVENT, 7, 10,
                DUEL, OWNER, OWNER, "minecraft:overworld", 1, EchoPresentationState.Pose.STANDING,
                false, EchoPresentationState.UseHand.NONE, 0, 0, 0, 0, 0, 0));
    }
}
