package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.junit.jupiter.api.Test;

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
}
