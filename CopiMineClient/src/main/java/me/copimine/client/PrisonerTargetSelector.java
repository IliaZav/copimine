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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local crosshair target preview; it sends nothing until an ability key is pressed. */
public final class PrisonerTargetSelector {
    private PrisonerTargetSelector() {
    }

    public static Preview preview(MinecraftClient client) {
        PrisonerHudController hud = ClientBridgeProtocol.prisonerHud();
        if (client == null || client.player == null || client.world == null
                || !client.player.isAlive() || client.player.isSpectator()
                || client.player.getWorld() != client.world || client.currentScreen != null
                || !hud.activeFor(client.player.getUuid())) {
            return Preview.NONE;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null || !camera.isReady() || camera.getFocusedEntity() != client.player) {
            return Preview.NONE;
        }
        Vec3d origin = camera.getPos();
        Vec3d direction = new Vec3d(camera.getHorizontalPlane()).normalize();
        // Account for third-person camera offset; feet-to-target limits still stay 24/28.
        double rayLength = PrisonerTargetRangePolicy.TURNCOAT_TARGET_RANGE
                + Math.min(8.0D, origin.distanceTo(client.player.getEyePos()));
        Vec3d end = origin.add(direction.multiply(rayLength));
        HitResult blockHit = client.world.raycast(new RaycastContext(origin, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
        double colliderDistance = blockHit.getType() == HitResult.Type.MISS
                ? rayLength : origin.distanceTo(blockHit.getPos());
        List<PrisonerTargetRayPolicy.Candidate<Entity>> candidates = new ArrayList<>();
        Box rayBounds = new Box(origin, end).expand(1.0D);
        for (Entity target : client.world.getOtherEntities(client.player, rayBounds)) {
            if (!(target instanceof LivingEntity) || !target.isAlive() || target.isSpectator()
                    || target.getWorld() != client.world) continue;
            candidates.add(new PrisonerTargetRayPolicy.Candidate<>(target, target.getUuid(),
                    target.getBoundingBox(), client.player.getPos().distanceTo(target.getPos()),
                    target instanceof PlayerEntity, target instanceof MobEntity));
        }
        PrisonerTargetRayPolicy.Selection<Entity> selection = PrisonerTargetRayPolicy.select(
                origin, direction, rayLength, colliderDistance, candidates, hud::isTargetAllowed);
        return selection == null ? Preview.NONE
                : new Preview(selection.target(), selection.ally(), selection.hostile());
    }

    /** Native outline hooks use a fresh ray and never mutate glowing flags or retain an entity. */
    public static int outlineColor(MinecraftClient client, Entity renderedEntity) {
        if (client == null || client.player == null || client.world == null
                || !(renderedEntity instanceof LivingEntity) || !renderedEntity.isAlive()
                || renderedEntity.isSpectator() || renderedEntity.getWorld() != client.world) {
            return 0;
        }
        PrisonerHudController hud = ClientBridgeProtocol.prisonerHud();
        if (!hud.activeFor(client.player.getUuid())) return 0;
        UUID renderedId = renderedEntity.getUuid();
        boolean supportEligible = renderedEntity instanceof PlayerEntity
                && hud.isTargetAllowed(PrisonerHudController.Ability.HEAL, renderedId);
        boolean hostileEligible = renderedEntity instanceof MobEntity
                && hud.isTargetAllowed(PrisonerHudController.Ability.TURNCOAT, renderedId);
        if (!supportEligible && !hostileEligible) return 0;
        Preview hovered = preview(client);
        if (hovered.entity() != renderedEntity) return 0;
        return PrisonerTargetHighlightPolicy.colorFor(hud, client.player.getUuid(),
                hovered.entity().getUuid(), renderedId, hovered.ally(), hovered.hostile());
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
