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

    /**
     * V2 keeps the same one-shot invariant but names the corresponding boss
     * band RIFT.  Keeping this overload separate prevents a string alias from
     * accidentally allowing the set in a scripted or late phase.
     */
    public static boolean canStart(V2BossStage stage, boolean enabledForStage,
                                   boolean alreadyUsedThisFight, boolean activeSetPresent) {
        return enabledForStage
                && stage == V2BossStage.RIFT
                && !alreadyUsedThisFight
                && !activeSetPresent;
    }
}
