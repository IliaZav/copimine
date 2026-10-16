package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;

/** One carrier, one deferred vanilla avatar submission; no authored replacement clips. */
public final class EchoVanillaRenderer {
    private static final EchoPresentationState STATE = new EchoPresentationState();
    private static final Map<UUID, CachedView> VIEWS = new HashMap<>();

    private static final class CachedView {
        final LivingEntity carrier;
        final EchoPlayerView player;
        int lastAge = Integer.MIN_VALUE;
        CachedView(LivingEntity carrier, EchoPlayerView player) {
            this.carrier = carrier;
            this.player = player;
        }
    }

    private EchoVanillaRenderer() { }

    public static boolean apply(BridgePayload payload, long nowMillis) {
        var packet = EchoPresentationPacket.decode(payload);
        boolean accepted = switch (packet.operation()) {
            case "BIND" -> STATE.bind(packet.frame(), nowMillis);
            case "STATE" -> STATE.update(packet.frame(), nowMillis);
            case "REMOVE" -> STATE.remove(packet.frame());
            default -> false;
        };
        if (accepted && packet.operation().equals("REMOVE")) VIEWS.remove(packet.frame().actor());
        return accepted;
    }

    public static boolean render(LivingEntityRenderState carrierState, PoseStack matrices,
                                 SubmitNodeCollector collector, CameraRenderState camera) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.player.isDeadOrDying()
                || !(carrierState instanceof EndRiftRenderStateAccess binding)
                || binding.copimine$isEcho()) return false;
        Entity resolved = client.level.getEntity(binding.copimine$entityId());
        if (!(resolved instanceof LivingEntity carrier) || carrier instanceof EchoPlayerView) return false;

        String actorId = binding.copimine$uuid();
        if (actorId == null || actorId.isBlank()) return false;
        String dimension = client.level.dimension().identifier().toString();
        var semantic = STATE.view(UUID.fromString(actorId), dimension, System.currentTimeMillis());
        if (semantic == null) return false;

        CachedView cached = VIEWS.get(semantic.frame().actor());
        if (cached == null || cached.carrier != carrier
                || !cached.player.getUUID().equals(semantic.frame().presentationId())) {
            if (VIEWS.size() >= EchoPresentationState.MAX_ACTORS && cached == null) return false;
            cached = new CachedView(carrier, new EchoPlayerView(client.level, semantic.frame()));
            VIEWS.put(semantic.frame().actor(), cached);
        }

        var ticket = STATE.textureTicket(semantic.frame().actor());
        var ownerEntry = client.getConnection() == null ? null
                : client.getConnection().getPlayerInfo(semantic.frame().owner());
        PlayerSkin skin = selectSkin(STATE, ticket,
                ownerEntry == null ? null : ownerEntry.getSkin(),
                DefaultPlayerSkin.get(semantic.frame().owner()));
        cached.player.project(carrier, semantic, skin, false);

        var candidate = client.getEntityRenderDispatcher().getRenderer(cached.player);
        if (!(candidate instanceof AvatarRenderer<?> rawRenderer)) return false;
        AvatarRenderer renderer = rawRenderer;
        AvatarRenderState echoState = (AvatarRenderState) renderer.createRenderState();
        float partialTick = camera == null ? 0.0F : camera.cameraEntityPartialTicks;
        renderer.extractRenderState(cached.player, echoState, partialTick);
        copyCarrierAnimation(carrierState, echoState, semantic.frame().deathTicks());
        // Detached echo names must never compete with the carrier's normal label.
        echoState.nameTag = null;
        renderer.submit(echoState, matrices, collector, camera);
        return true;
    }

    private static void copyCarrierAnimation(LivingEntityRenderState source,
                                             AvatarRenderState target, int deathTicks) {
        target.ageInTicks = source.ageInTicks;
        target.walkAnimationPos = source.walkAnimationPos;
        target.walkAnimationSpeed = source.walkAnimationSpeed;
        target.bodyRot = source.bodyRot;
        target.yRot = source.yRot;
        target.xRot = source.xRot;
        target.deathTime = Math.max(source.deathTime, deathTicks);
        target.hasRedOverlay = source.hasRedOverlay;
    }

    /** A slow or stale skin fetch cannot bind to a newer actor generation. */
    static PlayerSkin selectSkin(EchoPresentationState state, EchoPresentationState.TextureTicket ticket,
                                 PlayerSkin observed, PlayerSkin fallback) {
        return observed != null && state.acceptsTexture(ticket) ? observed : fallback;
    }

    public static void tick(Minecraft client, long nowMillis) {
        if (client.level == null || client.player == null || client.player.isDeadOrDying()) {
            clear();
            return;
        }
        String dimension = client.level.dimension().identifier().toString();
        VIEWS.entrySet().removeIf(entry -> {
            CachedView cached = entry.getValue();
            if (cached.carrier.isRemoved() || cached.carrier.level() != client.level)
                STATE.retire(entry.getKey());
            var semantic = STATE.view(entry.getKey(), dimension, nowMillis);
            if (semantic == null) return true;
            cached.player.project(cached.carrier, semantic, null,
                    cached.lastAge != cached.carrier.tickCount);
            cached.lastAge = cached.carrier.tickCount;
            return false;
        });
        // Iterate packet-owned identities only; never scan the client world.
        for (UUID actor : STATE.actorIds()) STATE.view(actor, dimension, nowMillis);
    }

    public static void clear() { STATE.clear(); VIEWS.clear(); }
    public static void reset() { STATE.reset(); VIEWS.clear(); }
    public static void retire(UUID actor) { STATE.retire(actor); VIEWS.remove(actor); }
}
