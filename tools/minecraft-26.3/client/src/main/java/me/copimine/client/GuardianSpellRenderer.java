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
    public record Pose(String mesh, float yaw, float pitch, float roll, float scale, float age) { }
    private record Motion(float width, float height, float depth, float twist, float surge, int tint) { }
    private GuardianSpellRenderer() { }

    public static Pose thrownPose(Entity entity, float partialTick) {
        if (!(entity instanceof Snowball || entity instanceof Fireball)
                || !(entity instanceof ItemSupplier item) || entity.isRemoved()) return null;
        int model = EndRiftItemModelData.firstFloatAsExactInteger(
                item.getItem().get(DataComponents.CUSTOM_MODEL_DATA));
        String mesh = model == 830031 ? "rift_lance" : model == 830013 ? "inferno_core" : "";
        if (mesh.isEmpty() || GuardianSpellMesh.vertices(mesh).isEmpty()) return null;
        var velocity = entity.getDeltaMovement();
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float yaw = horizontal > 0.0001 ? (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z)) : -entity.getYRot();
        float pitch = velocity.lengthSqr() > 0.0001
                ? (float) -Math.toDegrees(Math.atan2(velocity.y, horizontal)) : entity.getXRot();
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;
        float age = entity.tickCount + partialTick;
        return new Pose(mesh, yaw, pitch, age * 18F, model == 830013 ? 1.6F : 1.25F, age);
    }

    public static Pose displayPose(Display entity, float partialTick) {
        if (!(entity instanceof Display.ItemDisplay item) || entity.isRemoved()) return null;
        int model = EndRiftItemModelData.firstFloatAsExactInteger(
                item.getItemStack().get(DataComponents.CUSTOM_MODEL_DATA));
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
        float yaw = -entity.getYRot() + (model == 830208 ? age * 1.8F
                : model == 830215 ? age * 3F
                : model == 830216 ? age * 5F
                : model == 830219 ? age * 0.7F : 0F);
        float roll = model == 830204 || model >= 830208 ? 0F : age * 4F;
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
            case 830218 -> 1.8F;
            case 830219 -> 1.6F;
            default -> 1F;
        };
        return new Pose(mesh, yaw, entity.getXRot(), roll, scale * size, age);
    }

    private static Motion motion(Pose pose) {
        float age = pose.age();
        float heartbeat = .5F + .5F * (float) Math.sin(age * .32F);
        float charge = Math.min(1F, age / 12F);
        float release = (float) Math.sin(Math.PI * Math.min(1F, age / 18F));
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
            // The chest socket compresses inward as it charges, then the gyro
            // rings snap outward on release. A strong twist keeps the nested
            // rings counter-rotating so the body never reads as a static cube.
            case "chest_beam" -> new Motion(1F - .16F * charge + .10F * release,
                    1F - .16F * charge + .10F * release,
                    1F + .20F * release, .46F, release + .25F * charge, 0xF3C6FF);
            // The rock eruption climbs out of its own base, widening on release
            // rather than spinning, so it never reads as the rotating rune.
            case "geyser" -> new Motion(1F + .10F * release, .78F + .34F * charge + .14F * release,
                    1F + .10F * release, .06F, release, 0xFFD9C0);
            // The floor rift only opens across its committed axis: no roll, a
            // small spread as the crack widens, and a flat-to-bright surge.
            case "rift_fracture" -> new Motion(.86F + .20F * charge, 1F + .22F * release,
                    1.10F - .18F * charge, .02F, release, 0xFFB27A);
            // The fire column breathes and twists slowly, hotter and taller as
            // the charge completes, distinct from the rock shards.
            case "inferno_geyser" -> new Motion(.92F + .14F * heartbeat,
                    .72F + .40F * charge + .16F * release,
                    .92F + .14F * heartbeat, -.18F, heartbeat, 0xFFCC86);
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
        collector.submitCustomGeometry(matrices, RenderTypes.entityTranslucentEmissive(TEXTURE), (transform, buffer) -> {
            for (var vertex : vertices) {
                // Preserve volume under an emissive material: face shading is
                // authored here rather than depending on a dark arena lightmap.
                float shade = .64F + .17F * Math.max(0, vertex.ny())
                        + .09F * Math.max(0, -vertex.nz()) + .10F * motion.surge();
                float twist = motion.twist() * vertex.y()
                        * (float) Math.sin(pose.age() * .24F + vertex.y() * 3F);
                float cosine = (float) Math.cos(twist), sine = (float) Math.sin(twist);
                float x = vertex.x() * cosine - vertex.z() * sine;
                float z = vertex.x() * sine + vertex.z() * cosine;
                int tint = motion.tint();
                buffer.addVertex(transform, x, vertex.y(), z)
                        .setColor(Math.round(((tint >>> 16) & 255) * shade),
                                Math.round(((tint >>> 8) & 255) * shade),
                                Math.round((tint & 255) * shade), 255).setUv(vertex.u(),vertex.v())
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                        .setNormal(transform, vertex.nx() * cosine - vertex.nz() * sine,
                                vertex.ny(), vertex.nx() * sine + vertex.nz() * cosine);
            }
        });
        matrices.popPose();
        return true;
    }
}
