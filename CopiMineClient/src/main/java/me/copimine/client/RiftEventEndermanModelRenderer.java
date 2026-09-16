package me.copimine.client;

import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.mob.EndermanEntity;

/** Owns one bind-pose model per event Enderman variant. */
public final class RiftEventEndermanModelRenderer {
    private final RiftEventEndermanModel ordinary = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(false).createModel(), false);
    private final RiftEventEndermanModel elite = new RiftEventEndermanModel(
            RiftEventEndermanModel.getTexturedModelData(true).createModel(), true);

    public EntityModel<EndermanEntity> modelFor(EndermanRendererSelection.Kind kind) {
        return switch (kind) {
            case EVENT_ENDERMAN -> ordinary;
            case ELITE -> elite;
            default -> null;
        };
    }
}
