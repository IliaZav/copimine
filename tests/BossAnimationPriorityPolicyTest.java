import me.copimine.endevent.domain.BossAnimationPriorityPolicy;

public final class BossAnimationPriorityPolicyTest {
    public static void main(String[] args) {
        require(BossAnimationPriorityPolicy.canInterruptWithHurt("IDLE_BREATH"),
                "hurt must interrupt idle breathing");
        require(BossAnimationPriorityPolicy.canInterruptWithHurt("CAST_RELEASE"),
                "hurt must interrupt an ordinary cast cue");

        for (String protectedAnimation : new String[]{
                "DYING", "FINAL_STRIKE", "PHASE_TRANSITION", "GROUND_SLAM", "CHEST_STRIKE"}) {
            require(!BossAnimationPriorityPolicy.canInterruptWithHurt(protectedAnimation),
                    protectedAnimation + " must not be interrupted by ordinary hurt feedback");
        }
        require(!BossAnimationPriorityPolicy.canInterruptWithHurt("malformed-animation"),
                "unknown current animation must fail closed");
        System.out.println("BossAnimationPriorityPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
