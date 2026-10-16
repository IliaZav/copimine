package me.copimine.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Supplies the selected custom effect to the current frame's 26.3 render state. */
public final class ClientPostProcessRuntime {
    private static volatile Identifier requestedEffect;
    private static Identifier lastSubmittedEffect;

    private ClientPostProcessRuntime() {}

    public static void request(Identifier effect) {
        requestedEffect = effect;
    }

    public static void clear() {
        requestedEffect = null;
    }

    public static void submit(GameRenderer renderer) {
        List<Identifier> frameEffects = renderer.gameRenderState().requestedPostEffects;
        Identifier desired = requestedEffect;
        Identifier previous = lastSubmittedEffect;
        if (previous != null && !previous.equals(desired)) {
            frameEffects.remove(previous);
        }
        if (desired != null && !frameEffects.contains(desired)) {
            frameEffects.add(desired);
        }
        lastSubmittedEffect = desired;
    }
}
