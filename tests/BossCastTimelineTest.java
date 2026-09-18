import me.copimine.endevent.domain.BossAbilityState;
import me.copimine.endevent.domain.BossCastTimeline;

public final class BossCastTimelineTest {
    public static void main(String[] args) {
        check(BossCastTimeline.reconcile(BossAbilityState.TELEGRAPHING, 100, 200)
                .state() == BossAbilityState.TELEGRAPHING, "active telegraph must remain active");
        check(BossCastTimeline.reconcile(BossAbilityState.RECOVERY, 100, 200)
                .state() == BossAbilityState.RECOVERY, "recovery must honor its deadline");
        check(BossCastTimeline.reconcile(BossAbilityState.RECOVERY, 200, 200)
                .state() == BossAbilityState.NONE, "expired recovery must return to NONE");
        System.out.println("BossCastTimelineTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
