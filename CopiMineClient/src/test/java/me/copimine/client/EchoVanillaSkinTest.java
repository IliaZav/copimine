package me.copimine.client;

import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EchoVanillaSkinTest {
    private static EchoPresentationState.Frame frame(long epoch) {
        return new EchoPresentationState.Frame(new UUID(0, 1), 7, epoch, new UUID(0, 2),
                new UUID(0, 3), new UUID(0, 4), "minecraft:overworld", 1,
                EchoPresentationState.Pose.STANDING, false, EchoPresentationState.UseHand.NONE,
                0, 0, 0, 0, 0, 0);
    }
    private static SkinTextures skin(String name, SkinTextures.Model model) {
        return new SkinTextures(Identifier.of("copimine", name), null, null, null, model, true);
    }
    @Test void bothVanillaBodyGeometriesComeFromTheActualProviderResult() {
        var state = new EchoPresentationState();
        var frame = frame(10);
        state.bind(frame, 100);
        var fallback = skin("synthetic_fallback", SkinTextures.Model.WIDE);
        var slim = skin("synthetic_slim", SkinTextures.Model.SLIM);
        var wide = skin("synthetic_wide", SkinTextures.Model.WIDE);
        assertSame(slim, EchoVanillaRenderer.selectSkin(state, state.textureTicket(frame.actor()), slim, fallback));
        assertSame(wide, EchoVanillaRenderer.selectSkin(state, state.textureTicket(frame.actor()), wide, fallback));
        assertSame(fallback, EchoVanillaRenderer.selectSkin(state, state.textureTicket(frame.actor()), null, fallback));
    }
    @Test void delayedSkinCannotAttachAfterRemovalOrAReplacementEpoch() {
        var state = new EchoPresentationState();
        var frame = frame(10);
        state.bind(frame, 100);
        var old = state.textureTicket(frame.actor());
        var fallback = skin("synthetic_fallback", SkinTextures.Model.WIDE);
        var loaded = skin("synthetic_slim", SkinTextures.Model.SLIM);
        state.remove(frame);
        assertSame(fallback, EchoVanillaRenderer.selectSkin(state, old, loaded, fallback));
        state.bind(frame(11), 101);
        assertSame(fallback, EchoVanillaRenderer.selectSkin(state, old, loaded, fallback));
    }
}
