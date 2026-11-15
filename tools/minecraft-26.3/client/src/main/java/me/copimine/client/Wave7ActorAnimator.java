package me.copimine.client;

import me.copimine.protocol.Wave7AnimationFrame;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Authored local-space clips layered over gait; no entity lookups, timers or gameplay. */
public final class Wave7ActorAnimator {
    private Wave7ActorAnimator() { }
    public static void apply(ModelPart root, LivingEntityRenderState state, boolean warden) {
        if (!(state instanceof EndRiftRenderStateAccess access)) return;
        // Legacy HIT/DEATH own the interruption; never stack a cast or another recoil over it.
        if ("WAVE_HIT".equals(access.copimine$wavePose()) || "WAVE_DEATH".equals(access.copimine$wavePose())) return;
        Wave7AnimationFrame frame = access.copimine$trialFrame();
        ModelPart body = part(root, warden ? "bone/body" : "body");
        ModelPart head = part(root, warden ? "bone/body/head" : "head");
        ModelPart right = part(root, warden ? "bone/body/right_arm" : "right_arm");
        ModelPart left = part(root, warden ? "bone/body/left_arm" : "left_arm");
        if (frame != null) {
            float p = frame.progress(), smooth = p*p*(3-2*p);
            float draw = switch(frame.stage()) {
                case WINDUP, EMERGE -> smooth;
                case RELEASE -> 1-smooth*.7F;
                case RECOVERY -> 1-smooth;
                default -> 0;
            };
            // Contact is the final windup frame, exactly when Paper releases
            // damage/projectiles. Release starts there and recoils, never hits late.
            float contact = Math.clamp((p-.72F)/.28F, 0F, 1F);
            float strike = frame.stage() == Wave7AnimationFrame.Stage.WINDUP ? contact*contact*(3-2*contact)
                    : frame.stage() == Wave7AnimationFrame.Stage.RELEASE ? 1-smooth : 0;
            String action = frame.action().name();
            if (action.startsWith("MARKSMAN_")) {
                // Draw -> string release -> bow recoil. Legs retain the real gait.
                rotate(right, -1.45F*draw, -.15F*draw, -.07F*draw);
                rotate(left, -1.4F*draw, .65F*draw, .12F*draw);
                rotate(body, .06F*draw, -.18F*draw, 0);
                translate(left, 0, 0, strike*2.8F);
                rotate(head, -.05F*draw, .10F*draw, 0);
                if (action.equals("MARKSMAN_HEAVY_SHOT")) {
                    rotate(body, -.12F*draw+.25F*strike, -.16F*draw, 0);
                    translate(right, 0, 0, strike*2);
                } else if (action.equals("MARKSMAN_SPREAD_SHOT") || action.equals("MARKSMAN_PAIRED_CROSSFIRE")) {
                    rotate(body, 0, (p-.5F)*.32F*draw, 0);
                    rotate(left, 0, .18F*draw, -.15F*draw);
                }
            } else if (action.startsWith("ARCHMAGE_")) {
                switch (frame.action()) {
                    case ARCHMAGE_FANGS, ARCHMAGE_FANGS_GROUP -> {
                        rotate(right, -2.4F*draw+1.9F*strike, -.2F*draw, -.25F*draw);
                        rotate(left, -1.8F*draw+1.6F*strike, .35F*draw, .48F*draw);
                        rotate(body, -.08F*draw+.42F*strike, 0, 0);
                        translate(body, 0, strike*.9F, 0);
                    }
                    case ARCHMAGE_SPEAR -> {
                        rotate(right, -1.6F*draw, -.20F*draw, -.16F*draw);
                        rotate(left, -.9F*draw, .3F*draw, .7F*draw);
                        translate(right, 0, 0, -4.5F*strike);
                        rotate(body, .15F*strike, -.3F*draw, 0);
                    }
                    case ARCHMAGE_BEAM -> {
                        rotate(right, -1.5F*draw, -.25F*draw, -.38F*draw);
                        rotate(left, -1.5F*draw, .25F*draw, .38F*draw);
                        translate(right, 0, 0, -strike*2);
                        translate(left, 0, 0, -strike*2);
                        rotate(body, -.07F*draw+.15F*strike, 0, 0);
                    }
                    case ARCHMAGE_STEP -> {
                        rotate(right, -.5F*draw, 0, -.45F*draw);
                        rotate(left, -.5F*draw, 0, .45F*draw);
                        rotate(body, .12F*draw, .4F*strike, -.08F*draw);
                    }
                    default -> { }
                }
                ModelPart ring = part(root, "right_arm/rift_sigil_ring");
                if (ring != null) { ring.visible=true; ring.yRot += p*1.5F; ring.xScale=ring.zScale=1+draw*.25F; }
            } else {
                switch(frame.action()) {
                    case WARDEN_NARROW_STRIKE -> {
                        rotate(right, -1.3F*draw+1.7F*strike, -.25F*draw, -.2F*draw);
                        rotate(body, .15F*strike, -.28F*draw+.42F*strike, 0);
                    }
                    case WARDEN_WIDE_STRIKE -> {
                        rotate(right, -.8F*draw, -.5F*draw+.9F*strike, -.7F*draw);
                        rotate(left, -.8F*draw, .5F*draw-.9F*strike, .7F*draw);
                        rotate(body, .12F*strike, -.45F*draw+.85F*strike, 0);
                    }
                    case WARDEN_SONIC_LUNGE -> {
                        rotate(right, -.65F*draw, 0, -.7F*draw);
                        rotate(left, -.65F*draw, 0, .7F*draw);
                        rotate(body, -.12F*draw+.5F*strike, 0, 0);
                        rotate(head, -.15F*draw, 0, 0);
                    }
                    case WARDEN_SLAM -> {
                        rotate(right, -2.6F*draw+2.5F*strike, 0, -.2F*draw);
                        rotate(left, -2.6F*draw+2.5F*strike, 0, .2F*draw);
                        rotate(body, -.2F*draw+.65F*strike, 0, 0);
                        translate(body, 0, strike*2, 0);
                    }
                    default -> { }
                }
                if (frame.stage() == Wave7AnimationFrame.Stage.EMERGE) {
                    rotate(body, -.25F*(1-smooth), 0, 0);
                    rotate(right, -.5F*(1-smooth), 0, -.15F);
                    rotate(left, -.5F*(1-smooth), 0, .15F);
                }
            }
        }
        // These native counters reflect accepted Paper damage/death. Canceled
        // attacks do not invent a hurt pose; cast ownership remains unchanged.
        if (state.hasRedOverlay) {
            float recoil=(float)Math.sin(Math.min(1F, (float)(state.ageInTicks % 10F)/10F)*Math.PI)*.14F;
            rotate(body,-recoil,0,recoil*.45F);
        }
        if (state.deathTime > 0) {
            float fall=Math.min(1F,state.deathTime/20F);
            rotate(body,.7F*fall,0,.16F*fall);
            rotate(right,.55F*fall,0,-.15F*fall); rotate(left,.55F*fall,0,.15F*fall);
        }
    }
    private static ModelPart part(ModelPart root,String path) {
        ModelPart current=root;
        for (String name:path.split("/")) { if (!current.hasChild(name)) return null; current=current.getChild(name); }
        return current;
    }
    private static void rotate(ModelPart p,float x,float y,float z) { if(p!=null){p.xRot+=x;p.yRot+=y;p.zRot+=z;} }
    private static void translate(ModelPart p,float x,float y,float z) { if(p!=null){p.x+=x;p.y+=y;p.z+=z;} }
}
