package me.copimine.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Local crosshair target preview; it sends nothing until an ability key is pressed. */
public final class PrisonerTargetSelector {
    private PrisonerTargetSelector() {
    }

    public static Preview preview(MinecraftClient client) {
        PrisonerHudController hud = ClientBridgeProtocol.prisonerHud();
        if (client == null || client.player == null || client.world == null
                || !hud.activeFor(client.player.getUuid())
                || !(client.crosshairTarget instanceof EntityHitResult hit)) {
            return Preview.NONE;
        }
        Entity target = hit.getEntity();
        if (!(target instanceof LivingEntity living) || target == client.player
                || !target.isAlive() || target.getWorld() != client.world) {
            return Preview.NONE;
        }
        UUID targetId = target.getUuid();
        double distance = client.player.distanceTo(target);
        boolean ally = target instanceof PlayerEntity player && player != client.player
                && hud.isTargetAllowed(PrisonerHudController.Ability.HEAL, targetId)
                && PrisonerTargetRangePolicy.allows(PrisonerHudController.Ability.HEAL, distance);
        boolean hostile = target instanceof MobEntity
                && hud.isTargetAllowed(PrisonerHudController.Ability.TURNCOAT, targetId)
                && PrisonerTargetRangePolicy.allows(
                        PrisonerHudController.Ability.TURNCOAT, distance);
        if (!ally && !hostile) return Preview.NONE;
        return new Preview(target, ally, hostile);
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        Preview preview = preview(client);
        Entity target = preview.entity();
        if (target == null || context == null || context.world() == null
                || context.consumers() == null || context.matrixStack() == null) {
            return;
        }
        Camera camera = context.camera();
        if (camera == null || camera.getPos() == null) {
            return;
        }
        VertexConsumerProvider consumers = context.consumers();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = camera.getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MatrixStack.Entry entry = matrices.peek();
        Box box = target.getBoundingBox().expand(0.045D);
        int red = preview.ally() ? 53 : 172;
        int green = preview.ally() ? 225 : 93;
        int blue = preview.ally() ? 245 : 255;
        double[][] corners = {
                {box.minX, box.minY, box.minZ}, {box.maxX, box.minY, box.minZ},
                {box.maxX, box.minY, box.maxZ}, {box.minX, box.minY, box.maxZ},
                {box.minX, box.maxY, box.minZ}, {box.maxX, box.maxY, box.minZ},
                {box.maxX, box.maxY, box.maxZ}, {box.minX, box.maxY, box.maxZ}
        };
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] edge : edges) {
            drawLine(lines, entry, corners[edge[0]], corners[edge[1]], red, green, blue);
        }
        matrices.pop();
    }

    private static void drawLine(VertexConsumer lines, MatrixStack.Entry entry,
                                 double[] from, double[] to, int red, int green, int blue) {
        lines.vertex(entry, (float) from[0], (float) from[1], (float) from[2])
                .color(red, green, blue, 220).normal(entry, 0.0F, 1.0F, 0.0F);
        lines.vertex(entry, (float) to[0], (float) to[1], (float) to[2])
                .color(red, green, blue, 220).normal(entry, 0.0F, 1.0F, 0.0F);
    }

    public record Preview(Entity entity, boolean ally, boolean hostile) {
        private static final Preview NONE = new Preview(null, false, false);

        public UUID targetIdFor(PrisonerHudController.Ability ability) {
            if (entity == null || ability == null) return null;
            boolean roleAllows = switch (ability) {
                case HEAL, BATTLE_SURGE, GUARDIAN_LINK -> ally;
                case TURNCOAT -> hostile;
            };
            return roleAllows ? entity.getUuid() : null;
        }

        public String targetUuidFor(PrisonerHudController.Ability ability) {
            UUID targetId = targetIdFor(ability);
            return targetId == null ? "" : targetId.toString();
        }

        public boolean validFor(PrisonerHudController.Ability ability) {
            return targetIdFor(ability) != null;
        }
    }
}
