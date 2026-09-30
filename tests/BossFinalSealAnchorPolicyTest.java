import me.copimine.endevent.domain.BossFinalSealAnchorPolicy;
import me.copimine.endevent.domain.BossPhase;

public final class BossFinalSealAnchorPolicyTest {
    public static void main(String[] args) {
        require(BossFinalSealAnchorPolicy.shouldPin(BossPhase.LAST_SEAL),
                "LAST_SEAL must pin the boss to the Core");
        require(!BossFinalSealAnchorPolicy.shouldPin(BossPhase.RAGE),
                "RAGE must retain normal combat movement");
        require(BossFinalSealAnchorPolicy.isAtCore(
                        10.5D, 64.0D, -3.5D,
                        10.5D, 64.0D, -3.5D, 0.05D),
                "the exact combat anchor must count as on Core");
        require(!BossFinalSealAnchorPolicy.isAtCore(
                        12.0D, 64.0D, -3.5D,
                        10.5D, 64.0D, -3.5D, 0.05D),
                "an offset boss must not be reported as on Core");
        System.out.println("BossFinalSealAnchorPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
