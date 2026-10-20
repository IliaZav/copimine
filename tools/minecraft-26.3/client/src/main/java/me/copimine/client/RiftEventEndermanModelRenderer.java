package me.copimine.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;

/** Owns one bind-pose model per event Enderman role and type. */
public final class RiftEventEndermanModelRenderer {
    private final RiftEventEndermanModel ordinary = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.ORDINARY).bakeRoot(),
            RiftEventEndermanModel.Variant.ORDINARY);
    private final RiftEventEndermanModel elite = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.ELITE).bakeRoot(),
            RiftEventEndermanModel.Variant.ELITE);
    private final RiftEventEndermanModel waveGuardian = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.WAVE_GUARDIAN).bakeRoot(),
            RiftEventEndermanModel.Variant.WAVE_GUARDIAN);
    private final RiftEventEndermanModel ritualGuard = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.RITUAL_GUARD).bakeRoot(),
            RiftEventEndermanModel.Variant.RITUAL_GUARD);
    private final RiftEventEndermanModel ritualCaster = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.RITUAL_CASTER).bakeRoot(),
            RiftEventEndermanModel.Variant.RITUAL_CASTER);

    public EntityModel<EndermanRenderState> modelFor(EndermanRendererSelection.Kind kind) {
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
