package me.copimine.client;

import net.minecraft.client.render.entity.model.SpiderEntityModel;
import net.minecraft.entity.mob.SpiderEntity;

/** Owns one adapted spider model instance per event role. */
public final class RiftSpiderModelRenderer {
    private final RiftSpiderModel ordinary = new RiftSpiderModel(
            RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.ORDINARY).createModel(),
            RiftSpiderModel.Variant.ORDINARY);
    private final RiftSpiderModel elite = new RiftSpiderModel(
            RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.ELITE).createModel(),
            RiftSpiderModel.Variant.ELITE);
    private final RiftSpiderModel waveGuardian = new RiftSpiderModel(
            RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.WAVE_GUARDIAN).createModel(),
            RiftSpiderModel.Variant.WAVE_GUARDIAN);
    private final RiftSpiderModel ritualGuard = new RiftSpiderModel(
            RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.RITUAL_GUARD).createModel(),
            RiftSpiderModel.Variant.RITUAL_GUARD);

    public SpiderEntityModel<SpiderEntity> model() {
        return ordinary;
    }

    public SpiderEntityModel<SpiderEntity> modelFor(String visualId) {
        if (visualId == null) {
            return null;
        }
        return switch (visualId.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "END_RIFT_SPIDER_V1" -> ordinary;
            case "END_RIFT_ELITE_SPIDER_V1" -> elite;
            case "END_RIFT_WAVE_GUARDIAN_SPIDER_V1" -> waveGuardian;
            case "END_RIFT_RITUAL_GUARD_SPIDER_V1" -> ritualGuard;
            default -> null;
        };
    }
}
