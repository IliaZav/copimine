import me.copimine.endevent.domain.RitualCasterTacticsPolicy;

/** Regression test for the native AI ownership boundary of Wave 6 casters. */
public final class RitualCasterAiOwnershipPolicyTest {
    public static void main(String[] args) {
        check(!RitualCasterTacticsPolicy.nativeAiEnabled(
                        RitualCasterTacticsPolicy.State.GUARDED_CASTING),
                "guarded casters must remain server-controlled");
        check(!RitualCasterTacticsPolicy.nativeAiEnabled(
                        RitualCasterTacticsPolicy.State.EXPOSED_CASTING),
                "exposed casters must remain server-controlled until their first accepted hit");
        check(RitualCasterTacticsPolicy.nativeAiEnabled(
                        RitualCasterTacticsPolicy.State.AWAKENED_ATTACKING),
                "awakened casters must receive native combat AI");
        System.out.println("RitualCasterAiOwnershipPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
