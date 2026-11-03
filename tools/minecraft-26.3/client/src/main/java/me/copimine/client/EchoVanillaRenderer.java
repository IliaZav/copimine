package me.copimine.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

/**
 * One carrier, one deferred vanilla avatar submission.
 *
 * <p>The Echo is rendered by the *real vanilla player path*: a detached
 * {@link RemotePlayer} view that never ticks physics, item effects or
 * networking, submitted through {@code AvatarRenderer}. No authored
 * replacement clips exist for the copy; every pose, swing, draw, reload,
 * block, eat, crouch, sprint and hurt frame is produced by the same vanilla
 * animation code that draws actual players, driven only by the semantic state
 * the server publishes for the duel.</p>
 *
 * <p>Unlike the earlier {@code calculateEntityAnimation}-only projection, this
 * renderer now feeds the view through the vanilla per-tick gait/swing/use
 * bookkeeping ({@code tickCount}, {@code walkAnimationPos/Speed},
 * {@code oAttackAnim/attackAnim}, {@code swinging}, {@code useItemRemainingTicks},
 * {@code hurtTime}) so the {@code AvatarRenderer} sees a player mid-animation
 * instead of a rest pose.</p>
 */
public final class EchoVanillaRenderer {
    private static final EchoPresentationState STATE = new EchoPresentationState();
    private static final Map<UUID, CachedView> VIEWS = new HashMap<>();
    private static final EquipmentSlot[] VISIBLE_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    /** Vanilla swing length in ticks ({@code LivingEntity.SWING_DURATION}). */
    private static final int SWING_TICKS = 6;
    /** Vanilla hurt flash length used for a taken hit. */
    private static final int HURT_TICKS = 10;

    /**
     * ECHO-003: the copy reads as the owner's own body, visibly drained.
     *
     * <p>The skin is the player's real skin, only darkened, with the eyes the
     * only thing that stays bright. This tint is applied by
     * {@link EchoEyesFeature} on top of the drawn avatar, so the copy is
     * recognisable but never mistaken for a teammate.</p>
     */
    static final int ECHO_BODY_TINT = 0xFF5A5268;

    private static final class CachedView {
        final LivingEntity carrier;
        final EchoPlayerView player;
        int lastAge = Integer.MIN_VALUE;
        long lastSequence = Long.MIN_VALUE;
        long lastSwingSerial = Long.MIN_VALUE;
        long lastHurtSerial = Long.MIN_VALUE;
        boolean lastCrouching;
        boolean lastUsing;
        int lastDeath;
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
        driveVanillaAnimation(cached, semantic, carrier);

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

    /**
     * Advance the vanilla animation clocks exactly like {@code Player.tick}
     * would, but from server-authored receipts instead of a second simulation.
     *
     * <p>Each block mirrors the vanilla bookkeeping method so the render state
     * built afterwards is a genuine mid-animation player state: gait from the
     * view's own movement delta, a six-tick swing for each accepted melee hit,
     * a short hurt flash for each accepted incoming hit, and a crouch
     * transition that is not a teleport between poses.</p>
     */
    private static void driveVanillaAnimation(CachedView cached, EchoPresentationState.View view,
                                              LivingEntity carrier) {
        EchoPlayerView player = cached.player;
        var frame = view.frame();
        int tick = player.tickCount;

        // Vanilla gait: the walk clock advances once per tick with the horizontal
        // speed exactly as LivingEntity.tick does, so the copy's legs, arms and
        // idle sway come from the game's own animation curve.
        float horizontalSpeed = (float) Math.sqrt(
                player.getDeltaMovement().horizontalDistanceSqr());
        player.walkAnimation.update(horizontalSpeed, 0.4F, (float) (tick - 1));

        // Crouch: vanilla owns the eye height and body lean transition.
        boolean crouching = frame.pose() == EchoPresentationState.Pose.CROUCHING;
        if (crouching != cached.lastCrouching) {
            player.setShiftKeyDown(crouching);
            cached.lastCrouching = crouching;
        }

        // Swing: one swing per accepted melee/critical receipt.
        if (frame.swingSerial() != cached.lastSwingSerial) {
            cached.lastSwingSerial = frame.swingSerial();
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        }

        // Item use: the vanilla use clock runs from the real started item, so
        // bow draw, crossbow reload, shield block and eating animate with the
        // game's own use timeline. It is started once per use, never restarted.
        boolean using = frame.hand() != EchoPresentationState.UseHand.NONE
                && frame.useDuration() > 0 && frame.deathTicks() == 0;
        if (using && !cached.lastUsing) {
            player.startUsingItem(frame.hand() == EchoPresentationState.UseHand.OFF
                    ? net.minecraft.world.InteractionHand.OFF_HAND
                    : net.minecraft.world.InteractionHand.MAIN_HAND);
        } else if (!using && cached.lastUsing) {
            player.stopUsingItem();
        }
        cached.lastUsing = using;

        // Hurt flash mirrors vanilla when a damage receipt arrives.
        if (frame.hurtSerial() != cached.lastHurtSerial) {
            cached.lastHurtSerial = frame.hurtSerial();
            player.hurtTime = HURT_TICKS;
            player.hurtDuration = HURT_TICKS;
        }
        if (player.hurtTime > 0) player.hurtTime--;

        int death = frame.deathTicks();
        cached.lastDeath = death;
        cached.lastSequence = frame.sequence();
        cached.lastAge = tick;
    }

    private static void copyCarrierAnimation(LivingEntityRenderState source,
                                             AvatarRenderState target, int deathTicks) {
        target.ageInTicks = source.ageInTicks;
        target.bodyRot = source.bodyRot;
        target.yRot = source.yRot;
        target.xRot = source.xRot;
        target.deathTime = Math.max(source.deathTime, deathTicks);
        target.hasRedOverlay = source.hasRedOverlay;
        // Gait, swing and use clocks stay owned by the projected player view:
        // copying the carrier's (a pillager's) clocks would replay its gait.
    }

    /** A slow or stale skin fetch cannot bind to a newer actor generation. */
    static PlayerSkin selectSkin(EchoPresentationState state, EchoPresentationState.TextureTicket ticket,
                                 PlayerSkin observed, PlayerSkin fallback) {
        return observed != null && state.acceptsTexture(ticket) ? observed : fallback;
    }

    static void projectEquipment(EchoPlayerView view, LivingEntity carrier, int version,
                                 boolean force) {
        for (var slot : VISIBLE_SLOTS) {
            ItemStack nativeStack = carrier.getItemBySlot(slot);
            if (force || !ItemStack.matches(view.getItemBySlot(slot), nativeStack))
                view.setItemSlot(slot, nativeStack.copy());
        }
        view.setEquipmentVersion(version);
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
            driveVanillaAnimation(cached, semantic, cached.carrier);
            return false;
        });
        // Iterate packet-owned identities only; never scan the client world.
        for (UUID actor : STATE.actorIds()) STATE.view(actor, dimension, nowMillis);
    }

    /**
     * NotFound-safe skin identifier for diagnostics only; kept because the
     * texture catalog logs every echo asset once per session.
     */
    static Identifier echoEyeTexture() {
        return Identifier.fromNamespaceAndPath("copimine", "textures/entity/end_event_echo_eyes.png");
    }

    public static void clear() { STATE.clear(); VIEWS.clear(); }
    public static void reset() { STATE.reset(); VIEWS.clear(); }
    public static void retire(UUID actor) { STATE.retire(actor); VIEWS.remove(actor); }
}
