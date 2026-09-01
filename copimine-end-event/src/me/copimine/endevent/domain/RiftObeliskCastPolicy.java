package me.copimine.endevent.domain;

/** One-shot gate for the DISTORTION-only Rift Obelisk mechanic. */
public final class RiftObeliskCastPolicy {
    private RiftObeliskCastPolicy() {
    }

    public static boolean canStart(BossStage stage, boolean enabledForStage,
                                   boolean alreadyUsedThisFight, boolean activeSetPresent) {
        return enabledForStage
                && stage == BossStage.DISTORTION
                && !alreadyUsedThisFight
                && !activeSetPresent;
    }
}
