import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.domain.RealitySplitRuntimePolicy;

public final class RealitySplitRuntimePolicyTest {
    public static void main(String[] args) {
        check(RealitySplitRuntimePolicy.allowsRuntime(7, EventPhase.WAVE_7, false),
                "official Wave 7 runs its trial runtime");
        check(RealitySplitRuntimePolicy.allowsRuntime(7, EventPhase.COLLECTING, true),
                "a disposable Wave 7 test recovers trials without changing the event phase");
        check(!RealitySplitRuntimePolicy.allowsRuntime(6, EventPhase.COLLECTING, true),
                "a disposable Wave 6 does not run Wave 7 trials");
        check(!RealitySplitRuntimePolicy.allowsRuntime(7, EventPhase.COLLECTING, false),
                "an ordinary collecting event does not run Wave 7 trials");
        check(!RealitySplitRuntimePolicy.allowsRuntime(7, EventPhase.RECOVERY_REQUIRED, true),
                "a recovery-required event does not run a disposable trial");
        check(!RealitySplitRuntimePolicy.allowsRuntime(7, null, true),
                "a missing phase cannot activate the trial runtime");
        System.out.println("RealitySplitRuntimePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
