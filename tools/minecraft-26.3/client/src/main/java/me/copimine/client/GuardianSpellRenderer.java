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
    public record Pose(String mesh, float yaw, float pitch, float roll, float scale) { }
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
        return new Pose(mesh, yaw, pitch, (entity.tickCount + partialTick) * 18F,
                model == 830013 ? 1.6F : 1.25F);
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
            default -> "";
        };
        if (mesh.isEmpty() || GuardianSpellMesh.vertices(mesh).isEmpty()) return null;
        float age = entity.tickCount + partialTick;
        float scale = .18F + .82F * Math.min(1F, age / 6F);
        return new Pose(mesh, -entity.getYRot(), entity.getXRot(), model==830204 ? 0 : age*4F, scale);
    }

    public static boolean submit(Pose pose, PoseStack matrices, SubmitNodeCollector collector) {
        if (pose == null) return false;
        var vertices = GuardianSpellMesh.vertices(pose.mesh());
        if (vertices.isEmpty()) return false;
        matrices.pushPose();
        matrices.rotateDegrees(Axis.YP, pose.yaw());
        matrices.rotateDegrees(Axis.XP, pose.pitch());
        matrices.rotateDegrees(Axis.ZP, pose.roll());
        matrices.scale(pose.scale(), pose.scale(), pose.scale());
        collector.submitCustomGeometry(matrices, RenderTypes.entityTranslucentEmissive(TEXTURE), (transform, buffer) -> {
            for (var vertex : vertices) {
                // Preserve volume under an emissive material: face shading is
                // authored here rather than depending on a dark arena lightmap.
                int shade = Math.round(255F * (.72F + .18F * Math.max(0,vertex.ny())
                        + .10F * Math.max(0,-vertex.nz())));
                buffer.addVertex(transform,vertex.x(),vertex.y(),vertex.z())
                        .setColor(shade,shade,shade,255).setUv(vertex.u(),vertex.v())
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                        .setNormal(transform,vertex.nx(),vertex.ny(),vertex.nz());
            }
        });
        matrices.popPose();
        return true;
    }
}
