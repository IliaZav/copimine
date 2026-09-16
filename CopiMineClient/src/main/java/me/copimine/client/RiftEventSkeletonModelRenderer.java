package me.copimine.client;

import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import net.minecraft.entity.mob.AbstractSkeletonEntity;

/** Owns the ordinary and elite event skeleton model instances. */
public final class RiftEventSkeletonModelRenderer {
    private final RiftEventSkeletonModel ordinary = new RiftEventSkeletonModel(
            RiftEventSkeletonModel.getTexturedModelData(false).createModel(), false);
    private final RiftEventSkeletonModel elite = new RiftEventSkeletonModel(
            RiftEventSkeletonModel.getTexturedModelData(true).createModel(), true);

    public SkeletonEntityModel<AbstractSkeletonEntity> modelFor(boolean elite) {
        return elite ? this.elite : this.ordinary;
    }
}
