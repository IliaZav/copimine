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

/**
 * Local bone motion over the native gait for the Wave 6 Rift Ram.
 *
 * <p>The Ram's AI is scripted by the plugin, so vanilla never receives the
 * attack states that drive a Ravager's own animation.  The server publishes a
 * pose token and a lease; this mixin turns that token into a full-body
 * performance across neck, head, jaw, all four legs and the body.</p>
 *
 * <p>Signed poses are presentation only: they never grant movement or damage.
 * Amplitude is deliberately large enough to read at 20 TPS from the arena
 * floor, where the old version moved only the neck by a few degrees.</p>
 */
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
        ModelPart neck=child(root,"neck"), body=child(root,"body");
        // The native hierarchy is root -> neck -> head -> mouth. Resolve the
        // deeper joints through their parent, falling back to the root in case
        // a version flattens the tree.
        ModelPart head=child(root,"head");
        if (head==null) head=child(neck,"head");
        ModelPart mouth=child(root,"mouth");
        if (mouth==null) mouth=child(head,"mouth");
        if (mouth==null) mouth=child(neck,"mouth");
        ModelPart frontRight=child(root,"right_front_leg"), frontLeft=child(root,"left_front_leg");
        ModelPart hindRight=child(root,"right_hind_leg"), hindLeft=child(root,"left_hind_leg");
        float p=bound.copimine$poseProgress(), ease=p*p*(3-2*p);
        float age=state.ageInTicks;
        switch(bound.copimine$wavePose()) {
            case "WAVE_WINDUP" -> {
                // Rear back on the haunches, drop the head, paw the ground.
                if(neck!=null) { neck.xRot+=.55F*ease; neck.z-=2.6F*ease; }
                if(head!=null) head.xRot-= .30F*ease;
                if(body!=null) { body.xRot-=.16F*ease; body.y-=.9F*ease; }
                if(hindRight!=null) hindRight.xRot+= .30F*ease;
                if(hindLeft!=null) hindLeft.xRot+= .24F*ease;
                float paw=(float)Math.sin(age*.9F)*.22F*ease;
                if(frontRight!=null) frontRight.xRot+=paw;
                if(frontLeft!=null) frontLeft.xRot-=paw;
            }
            case "WAVE_FROZEN" -> {
                // Crouch low and shiver: the stomp is charging under the body.
                if(neck!=null) { neck.xRot-=.48F*ease; neck.y-=2.1F*ease; }
                if(body!=null) { body.y-=.95F*ease; }
                float shiver=(float)Math.sin(p*Math.PI*14F)*.05F*ease;
                if(head!=null) { head.xRot+= .20F*ease; head.zRot=shiver; }
                if(frontRight!=null) frontRight.xRot-=.16F*ease;
                if(frontLeft!=null) frontLeft.xRot-=.12F*ease;
            }
            case "WAVE_RELEASE" -> {
                // Two-beat stomp: a hard drop, then a smaller aftershock.
                float first=(float)Math.sin(Math.min(1F,p/.30F)*Math.PI)*Math.max(0F,1F-p/.8F);
                float second=(float)Math.sin(Math.max(0F,(p-.44F))/.24F*Math.PI)*Math.max(0F,1F-(p-.44F)/.42F);
                float hit=first+.55F*Math.max(0F,second);
                if(neck!=null) { neck.xRot+=.75F*hit; neck.y+=2.8F*hit; }
                if(head!=null) head.xRot-=.35F*hit;
                if(mouth!=null) mouth.xRot+=.9F*Math.max(first,second);
                if(body!=null) { body.xRot+=.20F*hit; body.y+=1.1F*hit; }
                for(ModelPart leg:new ModelPart[]{frontRight,frontLeft})
                    if(leg!=null) leg.xRot-=.48F*first;
                for(ModelPart leg:new ModelPart[]{hindRight,hindLeft})
                    if(leg!=null) leg.xRot+=.18F*first;
            }
            case "WAVE_COMBAT" -> {
                if(neck!=null && state.walkAnimationSpeed>.20F) {
                    float bob=(float)Math.sin(state.walkAnimationPos*.9F)*.16F
                            *Math.min(1F,state.walkAnimationSpeed*2.4F);
                    neck.xRot+=.22F+bob;
                }
                if(body!=null && state.walkAnimationSpeed>.20F)
                    body.y+=Math.abs((float)Math.sin(state.walkAnimationPos*.9F))*.22F;
            }
            case "WAVE_HIT" -> {
                // Full-body stagger away from the hit, head thrown aside.
                float stagger=(1-ease)*(float)Math.sin(p*Math.PI*5);
                if(neck!=null) { neck.xRot-=.42F*(1-ease); neck.zRot+=stagger*.22F; }
                if(head!=null) { head.zRot+=stagger*.18F; head.xRot+=.22F*(1-ease); }
                if(body!=null) { body.zRot+=stagger*.075F; body.y-=.25F*(1-ease); }
                if(frontRight!=null) frontRight.zRot+=stagger*.10F;
                if(frontLeft!=null) frontLeft.zRot+=stagger*.10F;
            }
            case "WAVE_RECOVER" -> {
                // Exhausted pant: shoulders drop and the head bobs back up.
                if(neck!=null) neck.xRot+=.24F*(1-ease);
                if(head!=null) head.xRot+=(.12F+.06F*(float)Math.sin(age*.7F))*(1-ease);
                if(body!=null) body.y-=.18F*(1-ease);
            }
            case "WAVE_DEATH" -> {
                // The armour is gone and the beast collapses forward.
                float fall=Math.min(1F,p/.85F), slump=fall*fall;
                if(body!=null) { body.xRot+= .85F*slump; body.y-= 2.4F*slump; }
                if(neck!=null) { neck.xRot+= .95F*slump; neck.y-= 2.0F*slump; }
                if(head!=null) head.xRot+= .45F*slump;
                if(mouth!=null) mouth.xRot+= .55F*slump;
                for(ModelPart leg:new ModelPart[]{frontRight,frontLeft})
                    if(leg!=null) leg.xRot-=.75F*slump;
                for(ModelPart leg:new ModelPart[]{hindRight,hindLeft})
                    if(leg!=null) leg.xRot+=.40F*slump;
            }
            case "WAVE_ROAR" -> {
                // Armour plate torn off: rear, jaw wide, challenge the party.
                float roar=(float)Math.sin(Math.min(1F,p/.25F)*Math.PI)*Math.max(0F,1F-p);
                if(neck!=null) { neck.xRot-=.55F*roar; neck.y+=1.7F*roar; }
                if(head!=null) head.xRot-=.35F*roar;
                if(mouth!=null) mouth.xRot+=1.15F*roar;
                if(body!=null) { body.xRot-=.14F*roar; body.y+=.55F*roar; }
                if(frontRight!=null) frontRight.xRot-=.30F*roar;
                if(frontLeft!=null) frontLeft.xRot-=.24F*roar;
            }
            default -> { }
        }
    }

    private static ModelPart child(ModelPart root,String name) {
        return root.hasChild(name)?root.getChild(name):null;
    }
}