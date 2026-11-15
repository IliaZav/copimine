package me.copimine.client.mixin;

import me.copimine.client.EndRiftRenderStateAccess;
import me.copimine.client.RiftRamVisualPolicy;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.ravager.RavagerModel;
import net.minecraft.client.renderer.entity.state.RavagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Local bone motion over native gait; signed poses never grant movement or damage. */
@Mixin(RavagerModel.class)
public abstract class RiftRamModelAnimationMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/RavagerRenderState;)V",at=@At("HEAD"))
    private void copimine$resetRamPose(RavagerRenderState state,CallbackInfo ci) {
        if (state instanceof EndRiftRenderStateAccess bound
                && RiftRamVisualPolicy.isRiftRamVisual(bound.copimine$visual()))
            ((RavagerModel)(Object)this).root().getAllParts().forEach(ModelPart::resetPose);
    }
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/RavagerRenderState;)V",at=@At("TAIL"))
    private void copimine$ramPose(RavagerRenderState state,CallbackInfo ci) {
        if (!(state instanceof EndRiftRenderStateAccess bound)
                || !RiftRamVisualPolicy.isRiftRamVisual(bound.copimine$visual())) return;
        ModelPart root=((RavagerModel)(Object)this).root();
        ModelPart neck=root.hasChild("neck")?root.getChild("neck"):null;
        ModelPart body=root.hasChild("body")?root.getChild("body"):null;
        float p=bound.copimine$poseProgress(), ease=p*p*(3-2*p);
        switch(bound.copimine$wavePose()) {
            case "WAVE_WINDUP" -> {
                if(neck!=null) { neck.xRot+=.35F*ease; neck.z-=1.8F*ease; }
                if(body!=null) body.xRot-=.05F*ease;
            }
            case "WAVE_FROZEN" -> {
                if(neck!=null) { neck.xRot-=.3F*ease; neck.y-=1.4F*ease; }
                if(body!=null) body.y-=.7F*ease;
            }
            case "WAVE_RELEASE" -> {
                float weight=.15F*(float)Math.sin(state.ageInTicks*1.1F);
                if(neck!=null) { neck.xRot+=.24F; neck.y+=weight; }
                if(body!=null) body.xRot+=weight*.18F;
            }
            case "WAVE_HIT" -> {
                float stagger=(1-ease)*(float)Math.sin(p*Math.PI*5);
                if(neck!=null) { neck.xRot-=.28F*(1-ease); neck.zRot+=stagger*.12F; }
                if(body!=null) body.zRot+=stagger*.035F;
            }
            case "WAVE_RECOVER" -> { if(neck!=null) neck.xRot+=.12F*(1-ease); }
            default -> { }
        }
    }
}
