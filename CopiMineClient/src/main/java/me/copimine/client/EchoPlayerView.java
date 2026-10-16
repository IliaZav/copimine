package me.copimine.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/** Unregistered renderer view. Never tick vanilla physics, item effects or networking. */
public final class EchoPlayerView extends OtherClientPlayerEntity {
    private static final EquipmentSlot[] VISIBLE_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private EchoPresentationState.View semantic;
    private SkinTextures skin;
    private int equipmentVersion = -1;

    public EchoPlayerView(ClientWorld world, EchoPresentationState.Frame frame) {
        super(world, new GameProfile(frame.presentationId(), "RiftEcho"));
        skin = DefaultSkinHelper.getSkinTextures(frame.owner());
        // All vanilla outer skin layers are enabled only on this detached view.
        getDataTracker().set(PLAYER_MODEL_PARTS, (byte) 0x7f);
    }

    public void project(LivingEntity carrier, EchoPresentationState.View view, SkinTextures ownerSkin,
                        boolean animationTick) {
        semantic = view;
        if (ownerSkin != null) skin = ownerSkin;
        var frame = view.frame();
        prevX = carrier.prevX; prevY = carrier.prevY; prevZ = carrier.prevZ;
        lastRenderX = carrier.lastRenderX; lastRenderY = carrier.lastRenderY; lastRenderZ = carrier.lastRenderZ;
        setPosition(carrier.getPos());
        setVelocity(carrier.getVelocity());
        setOnGround(carrier.isOnGround());
        setYaw(carrier.getYaw()); prevYaw = carrier.prevYaw;
        setPitch(carrier.getPitch()); prevPitch = carrier.prevPitch;
        bodyYaw = carrier.bodyYaw; prevBodyYaw = carrier.prevBodyYaw;
        headYaw = carrier.headYaw; prevHeadYaw = carrier.prevHeadYaw;
        setPose(frame.pose() == EchoPresentationState.Pose.CROUCHING ? EntityPose.CROUCHING : EntityPose.STANDING);
        setSneaking(frame.pose() == EchoPresentationState.Pose.CROUCHING);
        setSprinting(frame.sprinting());
        setMainArm(carrier.getMainArm());
        age = carrier.age;
        fallDistance = carrier.fallDistance;
        setHealth(carrier.getHealth());
        // The real carrier is the sole selection/HP authority. This detached
        // view is never inserted into ClientWorld or a network player list.
        setBoundingBox(carrier.getBoundingBox());
        projectEquipment(carrier, frame.equipmentVersion(), animationTick);
        projectNativeFeedback(carrier, frame);
        if (animationTick) {
            // Reuse vanilla gait and swing evaluation without super.tick(),
            // which would simulate a second actor and could consume items.
            updateLimbs(false);
        }
    }

    private void projectNativeFeedback(LivingEntity carrier, EchoPresentationState.Frame frame) {
        // Native timers keep advancing while the actor is outside the frustum.
        // Replaying a retained serial here would restart an already-ended hit.
        handSwinging = carrier.handSwinging; handSwingTicks = carrier.handSwingTicks;
        handSwingProgress = carrier.handSwingProgress; lastHandSwingProgress = carrier.lastHandSwingProgress;
        preferredHand = carrier.preferredHand;
        hurtTime = carrier.hurtTime; maxHurtTime = carrier.maxHurtTime;
        deathTime = Math.max(frame.deathTicks(), carrier.deathTime);
    }

    private void projectEquipment(LivingEntity carrier, int version, boolean animationTick) {
        if (equipmentVersion != version || animationTick) {
            for (var slot : VISIBLE_SLOTS) {
                ItemStack nativeStack = carrier.getEquippedStack(slot);
                if (!ItemStack.areEqual(getEquippedStack(slot), nativeStack))
                    equipStack(slot, nativeStack.copy());
            }
            equipmentVersion = version;
        }
    }

    @Override public SkinTextures getSkinTextures() { return skin == null ? super.getSkinTextures() : skin; }
    @Override public boolean isUsingItem() {
        return semantic != null && semantic.frame().hand() != EchoPresentationState.UseHand.NONE
                && semantic.frame().useDuration() > 0 && semantic.frame().deathTicks() == 0;
    }
    @Override public Hand getActiveHand() {
        return semantic != null && semantic.frame().hand() == EchoPresentationState.UseHand.OFF
                ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }
    @Override public ItemStack getActiveItem() { return isUsingItem() ? getStackInHand(getActiveHand()) : ItemStack.EMPTY; }
    @Override public int getItemUseTime() { return isUsingItem() ? semantic.useElapsed() : 0; }
    @Override public int getItemUseTimeLeft() {
        return isUsingItem() ? Math.max(0, getActiveItem().getMaxUseTime(this) - semantic.useElapsed()) : 0;
    }
}
