package me.copimine.client;

import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.mob.EndermanEntity;

/** Owns one bind-pose model per event Enderman role and type. */
public final class RiftEventEndermanModelRenderer {
    private final RiftEventEndermanModel ordinary = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.ORDINARY).createModel(),
            RiftEventEndermanModel.Variant.ORDINARY);
    private final RiftEventEndermanModel elite = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.ELITE).createModel(),
            RiftEventEndermanModel.Variant.ELITE);
    private final RiftEventEndermanModel waveGuardian = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.WAVE_GUARDIAN).createModel(),
            RiftEventEndermanModel.Variant.WAVE_GUARDIAN);
    private final RiftEventEndermanModel ritualGuard = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.RITUAL_GUARD).createModel(),
            RiftEventEndermanModel.Variant.RITUAL_GUARD);
    private final RiftEventEndermanModel ritualCaster = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.RITUAL_CASTER).createModel(),
            RiftEventEndermanModel.Variant.RITUAL_CASTER);

    public EntityModel<EndermanEntity> modelFor(EndermanRendererSelection.Kind kind) {
        return switch (kind) {
            case EVENT_ENDERMAN -> ordinary;
            case ELITE -> elite;
            case WAVE_GUARDIAN -> waveGuardian;
            case RITUAL_GUARD -> ritualGuard;
            case RITUAL_CASTER -> ritualCaster;
            default -> null;
        };
    }
}
