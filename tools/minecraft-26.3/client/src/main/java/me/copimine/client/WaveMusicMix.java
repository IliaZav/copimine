package me.copimine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/** Refresh existing music sources on fade steps; no play/stop or settings mutation. */
public final class WaveMusicMix {
    private static volatile float factor = 1;
    private static Object world;
    private static int ticks;
    private WaveMusicMix() { }
    public static float factor() { return factor; }

    public static void tick(Minecraft client, long now) {
        if (client == null) { factor = 1; world = null; ticks = 0; return; }
        boolean changedWorld = world != client.level;
        world = client.level;
        boolean fog = !changedWorld && client.level != null && client.player != null && !client.player.isDeadOrDying()
                && ClientBridgeProtocol.endEventBlackFogEndBlocks(
                client.level.dimension().identifier().getPath(), now) > 0;
        float previous = factor;
        factor = changedWorld || client.level == null || client.player == null || client.player.isDeadOrDying()
                ? 1 : WaveMusicMixPolicy.advance(previous, fog);
        // Refresh the music category; SoundEngine reuses the instance volume hook.
        // The mixin filters exact wave_5 MUSIC instances. No option is changed or track restarted.
        if (factor != previous && (++ticks % 4 == 0 || factor == 1 || factor == .30F))
            client.getSoundManager().refreshCategoryVolume(SoundSource.MUSIC);
    }
}
