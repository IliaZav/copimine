package me.copimine.client;

import java.util.Objects;
import java.util.Optional;
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

    /**
     * Transforms the actual keyboard movement only while the server-issued
     * reverse mark is active. Camera yaw and pitch are outside this input path.
     */
    public static Optional<AppliedInput> applyIfReverseActive(
            Input input, EndEventClientState state, long nowMillis) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(state, "state");
        if (!state.isReverseActive(nowMillis)) {
            return Optional.empty();
        }
        Input reversed = apply(input, true);
        return Optional.of(new AppliedInput(reversed, moveVector(reversed)));
    }

    public record AppliedInput(Input keyPresses, Vec2 moveVector) {
        public AppliedInput {
            Objects.requireNonNull(keyPresses, "keyPresses");
            Objects.requireNonNull(moveVector, "moveVector");
        }
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
