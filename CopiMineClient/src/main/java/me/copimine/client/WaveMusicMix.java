package me.copimine.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

/** Refresh existing music sources on fade steps; no play/stop or settings mutation. */
public final class WaveMusicMix {
    private static volatile float factor = 1;
    private static Object world;
    private static int ticks;
    private WaveMusicMix() { }
    public static float factor() { return factor; }

    public static void tick(MinecraftClient client, long now) {
        if (client == null) { factor = 1; world = null; ticks = 0; return; }
        boolean changedWorld = world != client.world;
        world = client.world;
        boolean fog = !changedWorld && client.world != null && client.player != null && !client.player.isDead()
                && ClientBridgeProtocol.endEventBlackFogEndBlocks(
                client.world.getRegistryKey().getValue().getPath(), now) > 0;
        float previous = factor;
        factor = changedWorld || client.world == null || client.player == null || client.player.isDead()
                ? 1 : WaveMusicMixPolicy.advance(previous, fog);
        // SoundSystem.updateSoundVolume recomputes each instance through getAdjustedVolume.
        // The mixin filters exact wave_5 MUSIC instances. No option is changed or track restarted.
        if (factor != previous && (++ticks % 4 == 0 || factor == 1 || factor == .30F))
            client.getSoundManager().updateSoundVolume(SoundCategory.MUSIC,
                    client.options.getSoundVolume(SoundCategory.MUSIC));
    }
}
