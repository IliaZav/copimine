package me.copimine.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

/** Unregistered renderer view. Never tick vanilla physics, item effects or networking. */
public final class EchoPlayerView extends RemotePlayer {
    private static final EquipmentSlot[] VISIBLE_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private EchoPresentationState.View semantic;
    private PlayerSkin skin;
    private int equipmentVersion = -1;

    public EchoPlayerView(ClientLevel world, EchoPresentationState.Frame frame) {
        super(world, new GameProfile(frame.presentationId(), "RiftEcho"));
        skin = DefaultPlayerSkin.get(frame.owner());
        // All vanilla outer skin layers are enabled only on this detached view.
        getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f);
    }

    public void project(LivingEntity carrier, EchoPresentationState.View view, PlayerSkin ownerSkin,
                        boolean animationTick) {
        semantic = view;
        if (ownerSkin != null) skin = ownerSkin;
        var frame = view.frame();
        xo = carrier.xo; yo = carrier.yo; zo = carrier.zo;
        xOld = carrier.xOld; yOld = carrier.yOld; zOld = carrier.zOld;
        setPos(carrier.position());
        setDeltaMovement(carrier.getDeltaMovement());
        setOnGround(carrier.onGround());
        setYRot(carrier.getYRot()); yRotO = carrier.yRotO;
        setXRot(carrier.getXRot()); xRotO = carrier.xRotO;
        yBodyRot = carrier.yBodyRot; yBodyRotO = carrier.yBodyRotO;
        yHeadRot = carrier.yHeadRot; yHeadRotO = carrier.yHeadRotO;
        setPose(frame.pose() == EchoPresentationState.Pose.CROUCHING ? Pose.CROUCHING : Pose.STANDING);
        setShiftKeyDown(frame.pose() == EchoPresentationState.Pose.CROUCHING);
        setSprinting(frame.sprinting());
        setMainArm(carrier.getMainArm());
        tickCount = carrier.tickCount;
        fallDistance = carrier.fallDistance;
        setHealth(carrier.getHealth());
        // The real carrier is the sole selection/HP authority. This detached
        // view is never inserted into ClientWorld or a network player list.
        setBoundingBox(carrier.getBoundingBox());
        projectEquipment(carrier, frame.equipmentVersion(), animationTick);
        if (animationTick) {
            // Reuse vanilla gait and swing evaluation without super.tick(),
            // which would simulate a second actor and could consume items.
            calculateEntityAnimation(false);
        }
    }

    private void projectEquipment(LivingEntity carrier, int version, boolean animationTick) {
        if (equipmentVersion != version || animationTick) {
            for (var slot : VISIBLE_SLOTS) {
                ItemStack nativeStack = carrier.getItemBySlot(slot);
                if (!ItemStack.matches(getItemBySlot(slot), nativeStack))
                    setItemSlot(slot, nativeStack.copy());
            }
            equipmentVersion = version;
        }
    }

    @Override public PlayerSkin getSkin() { return skin == null ? super.getSkin() : skin; }
    @Override public boolean isUsingItem() {
        return semantic != null && semantic.frame().hand() != EchoPresentationState.UseHand.NONE
                && semantic.frame().useDuration() > 0 && semantic.frame().deathTicks() == 0;
    }
    @Override public InteractionHand getUsedItemHand() {
        return semantic != null && semantic.frame().hand() == EchoPresentationState.UseHand.OFF
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
    @Override public ItemStack getUseItem() { return isUsingItem() ? getItemInHand(getUsedItemHand()) : ItemStack.EMPTY; }
    @Override public int getTicksUsingItem() { return isUsingItem() ? semantic.useElapsed() : 0; }
    @Override public int getUseItemRemainingTicks() {
        return isUsingItem() ? Math.max(0, getUseItem().getUseDuration(this) - semantic.useElapsed()) : 0;
    }
}
