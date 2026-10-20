import me.copimine.endevent.domain.BossFinalSealBarrierPolicy;
import me.copimine.endevent.domain.EventPhase;

public final class BossFinalSealBarrierPolicyTest {
    public static void main(String[] args) {
        check(BossFinalSealBarrierPolicy.keepFor(EventPhase.PRE_BOSS_COOLDOWN, 7),
                "Wave 7 barriers must stay up during the pre-boss cooldown");
        check(BossFinalSealBarrierPolicy.keepFor(EventPhase.BOSS_CINEMATIC, 7),
                "Wave 7 barriers must stay up during the boss cinematic");
        check(BossFinalSealBarrierPolicy.keepFor(EventPhase.BOSS_ACTIVE, 7),
                "Wave 7 barriers must stay up while the final seal is active");
        check(BossFinalSealBarrierPolicy.keepFor(EventPhase.BOSS_FINISH, 7),
                "Wave 7 barriers must stay up through the defeat cinematic");
        check(!BossFinalSealBarrierPolicy.keepFor(EventPhase.WAVE_7, 7),
                "the ordinary Wave 7 objective must not silently keep final-seal barriers after cleanup");
        check(!BossFinalSealBarrierPolicy.keepFor(EventPhase.BOSS_ACTIVE, 6),
                "a boss phase from another wave must not inherit Wave 7 barriers");
        System.out.println("BossFinalSealBarrierPolicyTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
