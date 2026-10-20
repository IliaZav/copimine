package me.copimine.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One carrier, one vanilla player body; no authored replacement animation clips. */
public final class EchoVanillaRenderer {
    private static final EchoPresentationState STATE = new EchoPresentationState();
    private static final Map<UUID, CachedView> VIEWS = new HashMap<>();
    private static final class CachedView {
        final LivingEntity carrier;
        final EchoPlayerView player;
        int lastAge = Integer.MIN_VALUE;
        CachedView(LivingEntity carrier, EchoPlayerView player) { this.carrier = carrier; this.player = player; }
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

    public static boolean render(LivingEntity carrier, float yaw, float delta, MatrixStack matrices,
                                 VertexConsumerProvider consumers, int light) {
        if (carrier instanceof EchoPlayerView) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.player.isDead()) return false;
        String dimension = client.world.getRegistryKey().getValue().toString();
        var semantic = STATE.view(carrier.getUuid(), dimension, System.currentTimeMillis());
        if (semantic == null) return false;
        CachedView cached = VIEWS.get(carrier.getUuid());
        if (cached == null || cached.carrier != carrier
                || !cached.player.getUuid().equals(semantic.frame().presentationId())) {
            if (VIEWS.size() >= EchoPresentationState.MAX_ACTORS && cached == null) return false;
            cached = new CachedView(carrier, new EchoPlayerView(client.world, semantic.frame()));
            VIEWS.put(carrier.getUuid(), cached);
        }
        var ticket = STATE.textureTicket(carrier.getUuid());
        var ownerEntry = client.getNetworkHandler() == null ? null
                : client.getNetworkHandler().getPlayerListEntry(semantic.frame().owner());
        SkinTextures skin = selectSkin(STATE, ticket,
                ownerEntry == null ? null : ownerEntry.getSkinTextures(),
                DefaultSkinHelper.getSkinTextures(semantic.frame().owner()));
        // Rendering can be skipped or occur several times during a native tick.
        // The retained gait advances only in the existing client tick callback.
        cached.player.project(carrier, semantic, skin, false);
        var renderer = client.getEntityRenderDispatcher().getRenderer(cached.player);
        if (!(renderer instanceof PlayerEntityRenderer playerRenderer)) return false;
        var texture = cached.player.getSkinTextures().texture();
        Set<RenderLayer> bodyLayers = Set.of(RenderLayer.getEntitySolid(texture),
                RenderLayer.getEntityCutoutNoCull(texture), RenderLayer.getEntityTranslucent(texture));
        VertexConsumerProvider scoped = layer -> bodyLayers.contains(layer)
                ? new SkinTint(consumers.getBuffer(layer)) : consumers.getBuffer(layer);
        playerRenderer.render(cached.player, yaw, delta, matrices, scoped, light);
        return true;
    }

    /** Existing skin-provider results cannot attach to an obsolete actor identity. */
    static SkinTextures selectSkin(EchoPresentationState state, EchoPresentationState.TextureTicket ticket,
                                   SkinTextures observed, SkinTextures fallback) {
        return observed != null && state.acceptsTexture(ticket) ? observed : fallback;
    }

    public static void tick(MinecraftClient client, long nowMillis) {
        if (client.world == null || client.player == null || client.player.isDead()) { clear(); return; }
        String dimension = client.world.getRegistryKey().getValue().toString();
        VIEWS.entrySet().removeIf(entry -> {
            CachedView cached = entry.getValue();
            if (cached.carrier.isRemoved() || cached.carrier.getWorld() != client.world)
                STATE.retire(entry.getKey());
            var semantic = STATE.view(entry.getKey(), dimension, nowMillis);
            if (semantic == null) return true;
            cached.player.project(cached.carrier, semantic, null, cached.lastAge != cached.carrier.age);
            cached.lastAge = cached.carrier.age;
            return false;
        });
        // Inspect only packet-owned identities and retained carriers, never a world scan.
        for (UUID actor : STATE.actorIds()) STATE.view(actor, dimension, nowMillis);
    }

    public static void clear() { STATE.clear(); VIEWS.clear(); }
    public static void reset() { STATE.reset(); VIEWS.clear(); }
    public static void retire(UUID actor) { STATE.retire(actor); VIEWS.remove(actor); }

    /** Tint only vanilla skin layers; armor, held items and other players retain their colors. */
    private record SkinTint(VertexConsumer delegate) implements VertexConsumer {
        public VertexConsumer vertex(float x, float y, float z) { delegate.vertex(x, y, z); return this; }
        public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color((int) (r * .58f), (int) (g * .42f), (int) (b * .72f), a); return this;
        }
        public VertexConsumer texture(float u, float v) { delegate.texture(u, v); return this; }
        public VertexConsumer overlay(int u, int v) { delegate.overlay(u, v); return this; }
        public VertexConsumer light(int u, int v) { delegate.light(u, v); return this; }
        public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
    }
}
