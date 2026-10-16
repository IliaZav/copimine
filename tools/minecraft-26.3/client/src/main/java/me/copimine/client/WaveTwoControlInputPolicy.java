package me.copimine.client;

import java.util.Objects;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/** Applies the Wave 2 reverse mark to movement keys while preserving action keys. */
public final class WaveTwoControlInputPolicy {
    private WaveTwoControlInputPolicy() {
    }

    public static Input apply(Input input, boolean reverseActive) {
        Objects.requireNonNull(input, "input");
        if (!reverseActive) {
            return input;
        }
        return new Input(input.backward(), input.forward(), input.right(), input.left(),
                input.jump(), input.shift(), input.sprint());
    }

    /** Recomputes the movement vector after the key record has been transformed. */
    public static Vec2 moveVector(Input input) {
        Objects.requireNonNull(input, "input");
        return new Vec2(impulse(input.left(), input.right()),
                impulse(input.forward(), input.backward())).normalized();
    }

    private static float impulse(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : positive ? 1.0F : -1.0F;
    }
}
