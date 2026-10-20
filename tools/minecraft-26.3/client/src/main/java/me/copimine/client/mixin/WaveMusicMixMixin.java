package me.copimine.client.mixin;

import me.copimine.client.WaveMusicMix;
import me.copimine.client.WaveMusicMixPolicy;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class WaveMusicMixMixin {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void copimine$mixFogMusic(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound == null || sound.getIdentifier() == null) return;
        cir.setReturnValue(WaveMusicMixPolicy.volume(sound.getIdentifier().getNamespace(), sound.getIdentifier().getPath(),
                sound.getSource() == SoundSource.MUSIC, cir.getReturnValueF(), WaveMusicMix.factor()));
    }
}
