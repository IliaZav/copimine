package me.copimine.client.mixin;

import me.copimine.client.WaveMusicMix;
import me.copimine.client.WaveMusicMixPolicy;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public abstract class WaveMusicMixMixin {
    @Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void copimine$mixFogMusic(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound == null || sound.getId() == null) return;
        cir.setReturnValue(WaveMusicMixPolicy.volume(sound.getId().getNamespace(), sound.getId().getPath(),
                sound.getCategory() == SoundCategory.MUSIC, cir.getReturnValueF(), WaveMusicMix.factor()));
    }
}
