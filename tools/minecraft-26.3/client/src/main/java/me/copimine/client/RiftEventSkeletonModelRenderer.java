package me.copimine.client;

import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;

/** Owns one event skeleton model instance per type/role visual. */
public final class RiftEventSkeletonModelRenderer {
    private final RiftEventSkeletonModel ordinary = new RiftEventSkeletonModel(
            RiftEventSkeletonModel.getTexturedModelData(RiftEventSkeletonModel.Variant.ORDINARY).bakeRoot(),
            RiftEventSkeletonModel.Variant.ORDINARY);
    private final RiftEventSkeletonModel elite = new RiftEventSkeletonModel(
            RiftEventSkeletonModel.getTexturedModelData(RiftEventSkeletonModel.Variant.ELITE).bakeRoot(),
            RiftEventSkeletonModel.Variant.ELITE);
    private final RiftEventSkeletonModel waveGuardian = new RiftEventSkeletonModel(
            RiftEventSkeletonModel.getTexturedModelData(RiftEventSkeletonModel.Variant.WAVE_GUARDIAN).bakeRoot(),
            RiftEventSkeletonModel.Variant.WAVE_GUARDIAN);
    private final RiftEventSkeletonModel ritualGuard = new RiftEventSkeletonModel(
            RiftEventSkeletonModel.getTexturedModelData(RiftEventSkeletonModel.Variant.RITUAL_GUARD).bakeRoot(),
            RiftEventSkeletonModel.Variant.RITUAL_GUARD);

    public SkeletonModel<SkeletonRenderState> modelFor(boolean elite) {
        return elite ? this.elite : this.ordinary;
    }

    public SkeletonModel<SkeletonRenderState> modelFor(String visualId) {
        if (visualId == null) {
            return null;
        }
        return switch (visualId.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "END_RIFT_SKELETON_V1" -> ordinary;
            case "END_RIFT_ELITE_SKELETON_V1" -> elite;
            case "END_RIFT_WAVE_GUARDIAN_SKELETON_V1" -> waveGuardian;
            case "END_RIFT_RITUAL_GUARD_SKELETON_V1" -> ritualGuard;
            default -> null;
        };
    }
}
