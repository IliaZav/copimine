package me.copimine.client;

import net.minecraft.client.render.entity.model.SpiderEntityModel;
import net.minecraft.entity.mob.SpiderEntity;

/** Owns the adapted spider model instance used by the renderer mixin. */
public final class RiftSpiderModelRenderer {
    private final RiftSpiderModel model = new RiftSpiderModel(
            RiftSpiderModel.getTexturedModelData().createModel());

    public SpiderEntityModel<SpiderEntity> model() {
        return model;
    }
}
