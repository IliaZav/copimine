package me.copimine.client;

import net.minecraft.client.model.monster.spider.SpiderModel;

/** Owns one adapted spider model instance per event role. */
public final class RiftSpiderModelRenderer {
    private final RiftSpiderModel ordinary = new RiftSpiderModel(
            RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.ORDINARY).bakeRoot(),
            RiftSpiderModel.Variant.ORDINARY);
    private final RiftSpiderModel elite = new RiftSpiderModel(
            RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.ELITE).bakeRoot(),
            RiftSpiderModel.Variant.ELITE);
    private final RiftSpiderModel waveGuardian = new RiftSpiderModel(
            RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.WAVE_GUARDIAN).bakeRoot(),
            RiftSpiderModel.Variant.WAVE_GUARDIAN);
    private final RiftSpiderModel ritualGuard = new RiftSpiderModel(
            RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.RITUAL_GUARD).bakeRoot(),
            RiftSpiderModel.Variant.RITUAL_GUARD);

    public SpiderModel model() {
        return ordinary;
    }

    public SpiderModel modelFor(String visualId) {
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
