package me.copimine.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local crosshair target preview; it sends nothing until an ability key is pressed. */
public final class PrisonerTargetSelector {
    private static final int MAX_QUERY_ENTITIES = 256;
    private static final PrisonerTargetPreviewCache PREVIEW_CACHE = new PrisonerTargetPreviewCache();

    private PrisonerTargetSelector() {
    }

    public static Preview preview(Minecraft client) {
        PrisonerHudController hud = ClientBridgeProtocol.prisonerHud();
        if (client == null || client.player == null || client.level == null
                || !client.player.isAlive() || client.player.isSpectator()
                || client.player.level() != client.level || client.gui.screen() != null
                || !hud.activeFor(client.player.getUUID())) {
            PREVIEW_CACHE.invalidate();
            return Preview.NONE;
        }
        return PREVIEW_CACHE.getOrCompute(client.level, client.player.getUUID(), client.level.getGameTime(),
                () -> computePreview(client, hud));
    }

    /** Called at connection/world boundaries so a cached entity is never retained across levels. */
    public static void invalidatePreview() {
        PREVIEW_CACHE.invalidate();
    }

    private static Preview computePreview(Minecraft client, PrisonerHudController hud) {
        Camera camera = client.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized() || camera.entity() != client.player) {
            return Preview.NONE;
        }
        Vec3 origin = camera.position();
        Vec3 direction = new Vec3(camera.forwardVector()).normalize();
        // Account for third-person camera offset; feet-to-target limits still stay 24/28.
        double rayLength = PrisonerTargetRangePolicy.TURNCOAT_TARGET_RANGE
                + Math.min(8.0D, origin.distanceTo(client.player.getEyePosition()));
        Vec3 end = origin.add(direction.scale(rayLength));
        HitResult blockHit = client.level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        double colliderDistance = blockHit.getType() == HitResult.Type.MISS
                ? rayLength : origin.distanceTo(blockHit.getLocation());
        AABB rayBounds = new AABB(origin, end).inflate(1.0D);
        List<Entity> queryEntities = new ArrayList<>(MAX_QUERY_ENTITIES);
        client.level.getEntities(EntityTypeTest.forClass(Entity.class), rayBounds,
                target -> target != client.player
                        && target instanceof LivingEntity living
                        && living.isAlive() && !living.isSpectator() && living.level() == client.level,
                queryEntities, MAX_QUERY_ENTITIES);
        List<PrisonerTargetRayPolicy.Candidate<Entity>> candidates = new ArrayList<>(queryEntities.size());
        for (Entity target : queryEntities) {
            if (!(target instanceof LivingEntity) || !target.isAlive() || target.isSpectator()
                    || target.level() != client.level) continue;
            candidates.add(new PrisonerTargetRayPolicy.Candidate<>(target, target.getUUID(),
                    target.getBoundingBox(), client.player.position().distanceTo(target.position()),
                    target instanceof Player, target instanceof Mob));
        }
        PrisonerTargetRayPolicy.Selection<Entity> selection = PrisonerTargetRayPolicy.select(
                origin, direction, rayLength, colliderDistance, candidates, hud::isTargetAllowed);
        Preview result = selection == null ? Preview.NONE
                : new Preview(selection.target(), selection.ally(), selection.hostile());
        if (result.entity() != null
                && (!result.entity().isAlive() || result.entity().level() != client.level)) {
            PREVIEW_CACHE.invalidate();
            return Preview.NONE;
        }
        return result;
    }

    /** Native outline hooks use a fresh ray and never mutate glowing flags or retain an entity. */
    public static int outlineColor(Minecraft client, Entity renderedEntity) {
        if (client == null || client.player == null || client.level == null
                || !(renderedEntity instanceof LivingEntity) || !renderedEntity.isAlive()
                || renderedEntity.isSpectator() || renderedEntity.level() != client.level) {
            return 0;
        }
        PrisonerHudController hud = ClientBridgeProtocol.prisonerHud();
        if (!hud.activeFor(client.player.getUUID())) return 0;
        UUID renderedId = renderedEntity.getUUID();
        boolean supportEligible = renderedEntity instanceof Player
                && hud.isTargetAllowed(PrisonerHudController.Ability.HEAL, renderedId);
        boolean hostileEligible = renderedEntity instanceof Mob
                && hud.isTargetAllowed(PrisonerHudController.Ability.TURNCOAT, renderedId);
        if (!supportEligible && !hostileEligible) return 0;
        Preview hovered = preview(client);
        if (hovered.entity() != renderedEntity) return 0;
        return PrisonerTargetHighlightPolicy.colorFor(hud, client.player.getUUID(),
                hovered.entity().getUUID(), renderedId, hovered.ally(), hovered.hostile());
    }

    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        Preview preview = preview(client);
        Entity target = preview.entity();
        if (target == null || context == null || client.level == null) {
            return;
        }
        Camera camera = client.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) {
            return;
        }
        Vec3 cameraPos = camera.position();
        AABB box = target.getBoundingBox().inflate(0.045D);
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
        EndRiftRenderSubmission.submit(context, cameraPos, RenderTypes.lines(), (matrices, lines) -> {
            PoseStack.Pose entry = matrices.last();
            for (int[] edge : edges) {
                drawLine(lines, entry, corners[edge[0]], corners[edge[1]], red, green, blue);
            }
        });
    }

    private static void drawLine(VertexConsumer lines, PoseStack.Pose entry,
                                 double[] from, double[] to, int red, int green, int blue) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(length) || length < 1.0E-6D) return;
        float nx = (float) (dx / length);
        float ny = (float) (dy / length);
        float nz = (float) (dz / length);
        lines.addVertex(entry, (float) from[0], (float) from[1], (float) from[2])
                .setColor(red, green, blue, 220)
                .setNormal(entry, nx, ny, nz)
                .setLineWidth(2.0F);
        lines.addVertex(entry, (float) to[0], (float) to[1], (float) to[2])
                .setColor(red, green, blue, 220)
                .setNormal(entry, nx, ny, nz)
                .setLineWidth(2.0F);
    }

    public record Preview(Entity entity, boolean ally, boolean hostile) {
        private static final Preview NONE = new Preview(null, false, false);

        public UUID targetIdFor(PrisonerHudController.Ability ability) {
            if (entity == null || ability == null) return null;
            boolean roleAllows = switch (ability) {
                case HEAL, BATTLE_SURGE, GUARDIAN_LINK -> ally;
                case TURNCOAT -> hostile;
            };
            return roleAllows ? entity.getUUID() : null;
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
