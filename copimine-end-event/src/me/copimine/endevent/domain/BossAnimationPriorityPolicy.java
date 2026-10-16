package me.copimine.endevent.domain;

/** Prevents a routine hit flinch from replacing a terminal or authored strike pose. */
public final class BossAnimationPriorityPolicy {
    private BossAnimationPriorityPolicy() {
    }

    public static boolean canInterruptWithHurt(String currentAnimation) {
        BossAnimationId current = BossAnimationId.fromWire(currentAnimation);
        if (current == BossAnimationId.UNKNOWN) {
            return false;
        }
        return current != BossAnimationId.DYING
                && current != BossAnimationId.FINAL_STRIKE
                && current != BossAnimationId.PHASE_TRANSITION
                && current != BossAnimationId.GROUND_SLAM
                && current != BossAnimationId.CHEST_STRIKE;
    }
}
