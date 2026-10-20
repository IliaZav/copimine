package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class WaveTwoControlInputPolicyTest {
    @Test
    void reverseMarkSwapsOnlyDirectionalKeysAndRebuildsMovementVector() {
        Input original = new Input(true, false, true, false, true, true, true);

        Input reversed = WaveTwoControlInputPolicy.apply(original, true);
        Vec2 movement = WaveTwoControlInputPolicy.moveVector(reversed);

        assertEquals(new Input(false, true, false, true, true, true, true), reversed);
        assertEquals(-0.70710677F, movement.x, 0.0001F);
        assertEquals(-0.70710677F, movement.y, 0.0001F);
    }

    @Test
    void withoutReverseMarkDirectionalInputIsUnchanged() {
        Input original = new Input(true, false, false, true, false, true, false);

        assertSame(original, WaveTwoControlInputPolicy.apply(original, false));
    }

    @Test
    void activeServerReverseMarkInvertsMovementAxesThroughTheClientState() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(new EndEventPacket("END_CONTROL_START", "wave-2", 1L,
                "reverse-mark-1", 2_000L, "", "", "", ""), 1_000L));
        Input original = new Input(true, false, true, false, true, true, true);
        Vec2 originalMovement = WaveTwoControlInputPolicy.moveVector(original);

        Optional<WaveTwoControlInputPolicy.AppliedInput> applied =
                WaveTwoControlInputPolicy.applyIfReverseActive(original, state, 1_001L);

        assertTrue(applied.isPresent());
        assertEquals(new Input(false, true, false, true, true, true, true),
                applied.orElseThrow().keyPresses());
        assertEquals(0.70710677F, originalMovement.x, 0.0001F);
        assertEquals(0.70710677F, originalMovement.y, 0.0001F);
        assertEquals(-0.70710677F, applied.orElseThrow().moveVector().x, 0.0001F);
        assertEquals(-0.70710677F, applied.orElseThrow().moveVector().y, 0.0001F);
    }

    @Test
    void expiredServerReverseMarkDoesNotAlterClientMovementInput() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(new EndEventPacket("END_CONTROL_START", "wave-2", 1L,
                "reverse-mark-1", 100L, "", "", "", ""), 1_000L));
        Input original = new Input(true, false, true, false, true, true, true);

        Optional<WaveTwoControlInputPolicy.AppliedInput> applied =
                WaveTwoControlInputPolicy.applyIfReverseActive(original, state, 1_100L);

        assertFalse(applied.isPresent());
        assertSame(original, WaveTwoControlInputPolicy.apply(original, state.isReverseActive(1_100L)));
    }
}
