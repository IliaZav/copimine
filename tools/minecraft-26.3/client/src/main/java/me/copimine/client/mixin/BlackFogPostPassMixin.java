package me.copimine.client.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import java.util.Map;
import me.copimine.client.BlackFogVolumeRuntime;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Updates only our named UBO through Minecraft's device API; no OpenGL state or shaderpack edits. */
@Mixin(PostPass.class)
public abstract class BlackFogPostPassMixin {
    @Shadow @Final private Map<String, GpuBuffer> customUniforms;

    @Inject(method = "addToFrame", at = @At("HEAD"))
    private void copimine$updateVolume(FrameGraphBuilder graph,
            Map<Identifier, ResourceHandle<RenderTarget>> targets, GpuBufferSlice projection, CallbackInfo ci) {
        GpuBuffer buffer = customUniforms.get("Wave5VolumeConfig");
        if (buffer == null) return;
        if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
            buffer.close();
            buffer = RenderSystem.getDevice().createBuffer(() -> "CopiMine Wave5 volume",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BlackFogVolumeRuntime.UNIFORM_BYTES);
            customUniforms.put("Wave5VolumeConfig", buffer);
        }
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), BlackFogVolumeRuntime.uniforms());
    }
}
