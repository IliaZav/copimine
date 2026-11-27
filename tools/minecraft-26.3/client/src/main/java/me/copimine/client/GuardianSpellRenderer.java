package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;

/** Velocity-aligned real projectile mesh: one actor, one body, no per-frame world scan. */
public final class GuardianSpellRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/world/guardian_spell_materials.png");
    public record Pose(String mesh, float yaw, float pitch, float roll, float scale, float age, float duration, int stage) { }
    private record Motion(float width, float height, float depth, float twist, float surge, int tint) { }
    private GuardianSpellRenderer() { }

    public static Pose thrownPose(Entity entity, float partialTick) {
        if (!(entity instanceof Snowball || entity instanceof Fireball)
                || !(entity instanceof ItemSupplier item) || entity.isRemoved()) return null;
        var data = item.getItem().get(DataComponents.CUSTOM_MODEL_DATA);
        int model = EndRiftItemModelData.firstFloatAsExactInteger(data);
        boolean cinder = model == 830013 && Float.valueOf(830240F).equals(data.getFloat(1));
        String mesh = model == 830031 ? "rift_lance" : model == 830013 ? "inferno_core" : "";
        if (mesh.isEmpty() || GuardianSpellMesh.vertices(mesh).isEmpty()) return null;
        var velocity = entity.getDeltaMovement();
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float yaw = horizontal > 0.0001 ? (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z)) : -entity.getYRot();
        float pitch = velocity.lengthSqr() > 0.0001
                ? (float) -Math.toDegrees(Math.atan2(velocity.y, horizontal)) : entity.getXRot();
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;
        float age = entity.tickCount + partialTick;
        return new Pose(mesh, yaw, pitch, SpellVisualRenderer.reducedMotion() ? 0F : age * 18F,
                cinder ? .58F : model == 830013 ? 1.6F : 1.25F, age, 24F, 3);
    }

    public static Pose displayPose(Display entity, float partialTick) {
        if (!(entity instanceof Display.ItemDisplay item) || entity.isRemoved()) return null;
        var data = item.getItemStack().get(DataComponents.CUSTOM_MODEL_DATA);
        int model = EndRiftItemModelData.firstFloatAsExactInteger(data);
        String mesh = switch (model) {
            case 830200 -> "void_core";
            case 830201 -> "inferno_core";
            case 830202 -> "rift_lance";
            case 830203 -> "echo_shard";
            case 830204 -> "final_seal";
            case 830205 -> "chest_iris";
            case 830206 -> "kagune_splinter";
            case 830207 -> "fault_fragment";
            case 830208 -> "void_rune";
            case 830209 -> "inferno_crown";
            case 830210 -> "summon_arch";
            case 830211 -> "arrow_rack";
            case 830212 -> "rift_gate";
            case 830213 -> "snare_chains";
            case 830214 -> "echo_bloom";
            case 830215 -> "euphoria_helix";
            case 830216 -> "chest_beam";
            case 830217 -> "geyser";
            case 830218 -> "rift_fracture";
            case 830219 -> "inferno_geyser";
            // N23-P1-06 residual: the three Kagune roles own three bodies.
            case 830220 -> "kagune_stab";
            case 830221 -> "kagune_eruption";
            case 830222 -> "kagune_small";
            // V5.1 REMASTER — cinematic VFX meshes (§9B-§9L)
            case 830223 -> "void_crush";
            case 830224 -> "claw_chain";
            case 830225 -> "dimensional_carve";
            // V7 REMASTER — new cinematic VFX meshes
            case 830226 -> "shoulder_slam";
            case 830227 -> "hunt_dash";
            case 830228 -> "grab_shove";
            case 830229 -> "seal_lance";
            case 830230 -> "ring_of_kagune";
            case 830231 -> "ambush_grab";
            case 830232 -> "infernal_surge";
            case 830233 -> "mirror_carnage";
            case 830234 -> "inferno_curtain";
            case 830235 -> "earthquake_wave";
            case 830236 -> "spire_harbinger";
            default -> "";
        };
        if (mesh.isEmpty() || GuardianSpellMesh.vertices(mesh).isEmpty()) return null;
        float age = entity.tickCount + partialTick;
        float scale = .18F + .82F * Math.min(1F, age / 6F);
        // The mesh itself breathes in local space; no extra entity per ring or rib.
        if (model == 830214) scale *= 1F + .22F*(float)Math.sin(Math.min(1F,age/24F)*Math.PI);
        if (model == 830213) scale *= 1F - .15F*Math.min(1F, age/30F);
        // A floor rune spins around its vertical normal, never through the floor.
        // Arches and arrow racks remain upright and keep their committed facing.
        float spin = SpellVisualRenderer.reducedMotion() ? 0F : age;
        float yaw = -entity.getYRot() + (model == 830208 ? spin * 1.8F
                : model == 830215 ? spin * 3F
                : model == 830216 ? spin * 5F
                : model == 830219 ? spin * 0.7F
                : model == 830221 ? spin * 0.55F
                : model == 830222 ? spin * 1.4F : 0F);
        float roll = model == 830204 || model >= 830208 ? 0F : spin * 4F;
        float size = switch (model) {
            case 830208 -> 2.6F;
            case 830209 -> 2.1F;
            case 830210 -> 1.8F;
            // Miniboss signatures sit on and around their own caster, so they
            // are sized to the body rather than to a boss-targeted effect.
            case 830212 -> 1.35F;
            case 830213 -> 1.5F;
            case 830214 -> 1.4F;
            case 830215 -> 1.3F;
            case 830216 -> 1.15F;
            // The floor hazards are sized to their real footprint: the rock
            // eruption and the rift band read wide, the fire column tall.
            case 830217 -> 1.5F;
            // NEW V5.1 REMASTER SPELLS — §9B-§9L cinematic cast geometries
            case 830223 -> 1.6F;  // void_crush — expanding 3D shock front
            case 830224 -> 1.4F;  // claw_chain — melee slash ribbons
            case 830225 -> 1.3F;  // dimensional_carve — portal tear
            case 830218 -> 1.8F;
            case 830219 -> 1.6F;
            // V7 spells — assign sizes by visual footprint
            case 830226 -> 1.35F;  // shoulder_slam — compact body impact
            case 830227 -> 1.5F;   // hunt_dash — elongated dash trail
            case 830228 -> 1.3F;   // grab_shove — close grapple
            case 830229 -> 1.6F;   // seal_lance — tall narrow volley
            case 830230 -> 1.8F;   // ring_of_kagune — wide defensive ring
            case 830231 -> 1.4F;   // ambush_grab — emerging tendrils
            case 830232 -> 1.9F;   // infernal_surge — arena-wide fire wall
            case 830233 -> 1.5F;   // mirror_carnage — fractured shards
            case 830234 -> 1.7F;   // inferno_curtain — overhead fire curtain
            case 830235 -> 2.0F;   // earthquake_wave — ground-level shock front
            case 830236 -> 1.4F;   // spire_harbinger — tall skeleton spire
            // The Kagune family: the stab reads as a long reached spike, the
            // eruption as a wide floor burst, the small summon as a low knot.
            case 830220 -> 1.25F;
            case 830221 -> 1.45F;
            case 830222 -> 1F;
            default -> 1F;
        };
        Float durationValue = data.getFloat(1);
        Float stageValue = data.getFloat(2);
        float duration = durationValue != null && Float.isFinite(durationValue)
                ? Math.clamp(durationValue, 2F, 80F) : 24F;
        int stage = stageValue == null ? 0 : Math.clamp(stageValue.intValue(), 0, 2);
        return new Pose(mesh, yaw, entity.getXRot(), roll, scale * size, age, duration, stage);
    }

    private static Motion motion(Pose pose) {
        float age = pose.age();
        float phase = Math.clamp(age / pose.duration(), 0, 1);
        boolean calm = SpellVisualRenderer.reducedMotion();
        float heartbeat = calm ? .5F : .5F + .5F * (float) Math.sin(age * .32F);
        float charge = phase * phase * (3F - 2F * phase);
        float release = pose.stage() == 2 ? 1F - phase * phase : (float) Math.sin(Math.PI * phase);
        // Multi-phase cinematic: stage 0=telegraph/charge, 1=release, 2=aftermath/collapse
        int stage = pose.stage();
        float telegraph = stage == 0 ? charge : stage == 1 ? 1F - charge * .3F : 1F - phase;
        float impact = stage == 0 ? charge * charge : stage == 1 ? release : 1F - phase * phase;
        return switch (pose.mesh()) {
            case "void_core" -> new Motion(1F + .14F * heartbeat, 1F - .09F * heartbeat,
                    1F + .14F * heartbeat, .24F, heartbeat, 0xEAD2FF);
            case "inferno_core" -> new Motion(1F - .08F * heartbeat, 1F + .16F * heartbeat,
                    1F + .15F * release, -.30F, heartbeat, 0xFFCEAA);
            case "rift_lance" -> new Motion(1F, 1F, 1F + .15F * release, .08F, release, 0xD8DCFF);
            case "echo_shard" -> new Motion(.70F + .30F * charge, 1F, 1F + .12F * release,
                    -.19F, release, 0xC6F0FF);
            case "final_seal" -> new Motion(1F - .16F * charge, 1F + .19F * charge,
                    1F - .16F * charge, .11F, heartbeat, 0xFFD7F3);
            case "chest_iris" -> new Motion(.79F + .21F * charge, .79F + .21F * charge,
                    1F + .11F * release, 0F, heartbeat, 0xFFDFBC);
            case "kagune_splinter" -> new Motion(1F, .80F + .20F * charge, 1F,
                    .38F, release, 0xE1C6FF);
            case "fault_fragment" -> new Motion(1F + .15F * release, 1F + .24F * release,
                    1F + .15F * release, .05F, release, 0xFFC1A9);
            case "void_rune" -> new Motion(.88F + .12F * charge, 1F, .88F + .12F * charge,
                    0F, heartbeat, 0xDAB5FF);
            case "inferno_crown" -> new Motion(1F, .68F + .35F * charge + .17F * heartbeat,
                    1F, .16F, heartbeat, 0xFFD2AF);
            case "summon_arch" -> new Motion(.82F + .18F * charge, .65F + .35F * charge,
                    1F, .15F, release, 0xC7E8FF);
            case "arrow_rack" -> new Motion(1F - .12F * charge, 1F, .95F + .19F * release,
                    0F, release, 0xFFE9D6);
            case "rift_gate" -> new Motion(.62F + .38F * charge, 1F, 1F + .12F * release,
                    -.08F, release, 0xBEF6FF);
            case "snare_chains" -> new Motion(1.15F - .26F * charge, 1F,
                    1.15F - .26F * charge, .21F, heartbeat, 0xEFCAFF);
            case "echo_bloom" -> new Motion(1F + .13F * release, .88F + .12F * charge,
                    1F + .13F * release, -.34F, release, 0xC5F2FF);
            case "euphoria_helix" -> new Motion(1F + .08F * heartbeat, .85F + .18F * charge,
                    1F + .08F * heartbeat, .38F, heartbeat, 0xFAABFF);
            // §9H CHEST BEAM — 5-shot cinematic
            // Stage 0: charging — chest compresses, energy rings tighten, deep pulse
            // Stage 1: release — chest snaps open, beam extends with twist, impact crown
            // Stage 2: aftermath — fading glow, plates close
            case "chest_beam" -> {
                float chestScale = stage == 0
                    ? 1F - .22F * charge + .06F * heartbeat  // compress inward during charge
                    : stage == 1
                        ? .85F + .18F * release  // snap open on release
                        : .92F + .10F * (1F - phase);  // slowly close
                float depth = stage == 0
                    ? 1F + .12F * charge  // beam starts extending
                    : stage == 1
                        ? 1F + .35F * release  // full beam length
                        : 1F + .25F * (1F - phase);  // retract
                float twistVal = stage == 0
                    ? .35F + .25F * charge  // rings spin up
                    : stage == 1
                        ? .60F * release  // energy release twist
                        : .15F * (1F - phase);  // winding down
                float surge = stage == 0
                    ? .35F * charge + .15F * heartbeat  // building energy glow
                    : stage == 1
                        ? .65F * release + .25F * charge  // peak brightness
                        : .30F * (1F - phase);  // fading
                int tint = stage == 0 ? 0xF3C6FF
                    : stage == 1 ? 0xFFF0FF  // white-hot at release
                    : 0xE0A0FF;  // cooling violet
                yield new Motion(chestScale, chestScale, depth, twistVal, surge, tint);
            }
            // §9D GEYSER — multi-phase eruption with ground crack, column, collapse
            // Stage 0: ground cracks appear, pressure builds, glow intensifies
            // Stage 1: eruption — column rises, flame tongues, rock debris
            // Stage 2: collapse — column fades, debris falls
            case "geyser" -> {
                float geyserWidth = stage == 0
                    ? 1F + .08F * heartbeat  // ground pulses
                    : stage == 1
                        ? .92F + .18F * release + .06F * (float)Math.sin(age * .6F)  // column widens, wavers
                        : .88F + .15F * (1F - phase);  // settling
                float geyserHeight = stage == 0
                    ? .72F + .28F * charge + .08F * heartbeat  // pressure building, rising
                    : stage == 1
                        ? .65F + .50F * release + .12F * (float)Math.sin(age * .4F)  // TALL eruption with waver
                        : .70F + .25F * (1F - phase);  // collapsing
                float geyserDepth = stage == 0
                    ? 1F + .08F * heartbeat
                    : stage == 1
                        ? .92F + .18F * release + .06F * (float)Math.sin(age * .6F + 1F)
                        : .88F + .15F * (1F - phase);
                float twistVal = stage == 0
                    ? .04F + .04F * charge  // slow swirl building
                    : stage == 1
                        ? .12F * release + .06F * (float)Math.sin(age * .8F)  // fast spiral during eruption
                        : .03F * (1F - phase);  // dying swirl
                float surge = stage == 0
                    ? .25F * charge + .15F * heartbeat  // glow building
                    : stage == 1
                        ? .55F * release + .10F * (float)Math.sin(age * .5F)  // bright peak with flicker
                        : .20F * (1F - phase);  // fading embers
                int tint = stage == 0 ? 0xFF8C40  // amber warning glow
                    : stage == 1 ? 0xFFD9C0  // white-hot core at peak
                    : 0xCC7740;  // cooling embers
                yield new Motion(geyserWidth, geyserHeight, geyserDepth, twistVal, surge, tint);
            }
            case "rift_fracture" -> new Motion(.86F + .20F * charge, 1F + .22F * release,
                    1.10F - .18F * charge, .02F, release, 0xFFB27A);
            case "inferno_geyser" -> new Motion(.92F + .14F * heartbeat,
                    .72F + .40F * charge + .16F * release,
                    .92F + .14F * heartbeat, -.18F, heartbeat, 0xFFCC86);
            case "kagune_stab" -> new Motion(.90F - .12F * charge, 1F + .05F * release,
                    1F + .38F * release, .10F, release, 0xD9B8FF);
            case "kagune_eruption" -> new Motion(.95F + .18F * heartbeat,
                    .70F + .38F * charge + .18F * release,
                    .95F + .18F * heartbeat, -.22F, heartbeat, 0xE7B8FF);
            case "kagune_small" -> new Motion(.86F + .18F * charge + .06F * heartbeat,
                    .86F + .18F * charge, .86F + .18F * charge,
                    .30F, charge, 0xF0C4FF);
            // V5.1 REMASTER — void_crush: expanding shock front with safe gaps
            case "void_crush" -> new Motion(
                    .60F + .45F * charge,  // width expands outward
                    .50F + .55F * charge + .10F * release,  // height lifts then settles
                    .60F + .45F * charge,  // depth expands
                    .08F,  // slight twist
                    .30F * charge + .40F * release,  // surge builds then peaks
                    0xD8B0FF);  // purple void energy
            // V5.1 REMASTER — claw_chain: fast melee slash ribbons
            case "claw_chain" -> new Motion(
                    1.20F - .25F * phase,  // width narrows from wide sweep
                    .70F + .35F * charge,  // height lifts during windup
                    1F + .20F * release,  // depth extends on contact
                    .30F * release,  // twist on release
                    release * .60F,  // surge peaks at contact
                    0xFFC0E0);  // pale magenta slash
            // V5.1 REMASTER — dimensional_carve: vertical tear with depth
            case "dimensional_carve" -> new Motion(
                    .80F + .25F * charge,  // width opens
                    1F + .15F * release,  // height stretches
                    .60F + .45F * charge,  // depth opens as portal
                    .12F * release,  // slight twist on release
                    .40F * charge + .30F * release,  // surge
                    0xA0D8FF);  // icy cyan
            // V7 — shoulder_slam: compact lateral body slam
            // Stage 0: wind up — body leans, shoulder pulls back
            // Stage 1: impact — shoulder drives forward, compression
            // Stage 2: recovery — body resets
            case "shoulder_slam" -> new Motion(
                    stage == 0 ? .88F + .14F * charge : stage == 1 ? 1.15F - .18F * phase : .92F + .10F * (1F - phase),
                    stage == 0 ? 1F - .06F * charge : stage == 1 ? 1F + .10F * release : .95F + .08F * (1F - phase),
                    stage == 0 ? .92F + .12F * charge : stage == 1 ? 1F + .20F * release : .95F + .10F * (1F - phase),
                    stage == 0 ? .10F * charge : stage == 1 ? .25F * release : .05F * (1F - phase),
                    stage == 0 ? .20F * charge : stage == 1 ? .55F * release : .15F * (1F - phase),
                    0xFFC8A0);  // warm amber impact
            // V7 — hunt_dash: fast forward dash with trail
            case "hunt_dash" -> new Motion(
                    1F + .08F * release,
                    1F,
                    1.2F + .40F * charge + .25F * release,
                    .15F * release,
                    .50F * charge + .35F * release,
                    0xC0A0FF);  // violet speed streak
            // V7 — grab_shove: close-range grapple push
            case "grab_shove" -> new Motion(
                    stage == 0 ? 1F + .05F * heartbeat : 1.10F - .12F * phase,
                    stage == 0 ? 1F : .85F + .18F * release,
                    stage == 0 ? 1F + .15F * charge : .95F + .20F * release,
                    .10F * release,
                    .30F * charge + .45F * release,
                    0xFF80A0);  // hot pink grip
            // V7 — seal_lance: tall narrow lance volley
            case "seal_lance" -> new Motion(
                    .80F + .22F * release,
                    1.2F + .30F * charge + .25F * release,
                    .85F + .18F * release,
                    .08F * release,
                    .35F * charge + .40F * release,
                    0xC080FF);  // purple seal energy
            // V7 — ring_of_kagune: wide defensive barrier ring
            case "ring_of_kagune" -> new Motion(
                    .60F + .45F * charge,
                    .80F + .25F * release,
                    .60F + .45F * charge,
                    .20F * release,
                    .25F * charge + .35F * release,
                    0xCC66FF);  // deep kagune violet
            // V7 — ambush_grab: emerging tendrils from below
            case "ambush_grab" -> new Motion(
                    1F + .10F * release,
                    stage == 0 ? .60F + .42F * charge : 1.1F + .15F * release,
                    1F + .10F * release,
                    .18F * release,
                    .30F * charge + .50F * release,
                    0xCC44AA);  // magenta ambush
            // V7 — infernal_surge: arena-wide fire wall array
            case "infernal_surge" -> new Motion(
                    .70F + .35F * charge,
                    .60F + .45F * charge,
                    .70F + .35F * charge,
                    .05F,
                    .40F * charge + .50F * release,
                    0xFF6633);  // inferno orange
            // V7 — mirror_carnage: fractured crystal shard planes
            case "mirror_carnage" -> new Motion(
                    .70F + .35F * release,
                    .60F + .45F * release,
                    .70F + .35F * release,
                    .30F * release,
                    .20F * charge + .45F * release,
                    0x88AAFF);  // cold mirror blue
            // V7 — inferno_curtain: overhead falling fire curtain
            case "inferno_curtain" -> new Motion(
                    .80F + .25F * charge,
                    .50F + .55F * charge + .10F * release,
                    .80F + .25F * charge,
                    .08F * release,
                    .30F * charge + .45F * release,
                    0xFF4400);  // intense fire
            // V7 — earthquake_wave: ground-level expanding shock front
            case "earthquake_wave" -> new Motion(
                    .50F + .55F * charge,
                    .60F + .20F * release,
                    .50F + .55F * charge,
                    .04F,
                    .35F * charge + .40F * release,
                    0xCC9944);  // earth brown-gold
            // V7 — spire_harbinger: tall skeletal spire with arms
            case "spire_harbinger" -> new Motion(
                    .85F + .18F * charge,
                    1F + .25F * release,
                    .85F + .18F * charge,
                    .12F * release,
                    .25F * charge + .30F * release,
                    0x88CC88);  // pale green rift energy
            default -> new Motion(1F, 1F, 1F, 0F, 0F, 0xFFFFFF);
        };
    }

    public static boolean submit(Pose pose, PoseStack matrices, SubmitNodeCollector collector) {
        if (pose == null) return false;
        var vertices = GuardianSpellMesh.vertices(pose.mesh());
        if (vertices.isEmpty()) return false;
        matrices.pushPose();
        matrices.rotateDegrees(Axis.YP, pose.yaw());
        matrices.rotateDegrees(Axis.XP, pose.pitch());
        matrices.rotateDegrees(Axis.ZP, pose.roll());
        Motion motion = motion(pose);
        matrices.scale(pose.scale() * motion.width(), pose.scale() * motion.height(),
                pose.scale() * motion.depth());
        float progress = Math.clamp(pose.age() / pose.duration(), 0, 1);
        int opacity = pose.stage() == 2 ? Math.round(255 * (1 - progress * progress * progress)) : 255;
        boolean calm = SpellVisualRenderer.reducedMotion();
        collector.submitCustomGeometry(matrices, RenderTypes.entityTranslucentEmissive(TEXTURE), (transform, buffer) -> {
            for (var vertex : vertices) {
                // Preserve volume under an emissive material: face shading is
                // authored here rather than depending on a dark arena lightmap.
                float shade = .46F + .27F * Math.max(0, vertex.ny())
                        + .13F * Math.max(0, -vertex.nz()) + .10F * motion.surge();
                float twist = motion.twist() * vertex.y()
                        * (float) Math.sin((calm ? 0 : pose.age() * .24F) + vertex.y() * 3F);
                float cosine = (float) Math.cos(twist), sine = (float) Math.sin(twist);
                float x = vertex.x() * cosine - vertex.z() * sine;
                float z = vertex.x() * sine + vertex.z() * cosine;
                int tint = motion.tint();
                buffer.addVertex(transform, x, vertex.y(), z)
                        .setColor(Math.round(((tint >>> 16) & 255) * shade),
                                Math.round(((tint >>> 8) & 255) * shade),
                                Math.round((tint & 255) * shade), opacity).setUv(vertex.u(),vertex.v())
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                        .setNormal(transform, vertex.nx() * cosine - vertex.nz() * sine,
                                vertex.ny(), vertex.nx() * sine + vertex.nz() * cosine);
            }
        });
        collector.submitCustomGeometry(matrices, SpellVisualRenderer.layer(),
                (transform, buffer) -> GuardianSpellAccents.draw(transform, buffer, pose));
        matrices.popPose();
        return true;
    }
}
