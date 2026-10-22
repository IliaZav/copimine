import me.copimine.endevent.domain.WaveMobRemovalPolicy;

public final class WaveMobRemovalPolicyTest {
    private WaveMobRemovalPolicyTest() { }

    public static void main(String[] args) {
        check(WaveMobRemovalPolicy.classify(true, false, "DEATH")
                        == WaveMobRemovalPolicy.Disposition.CONFIRMED_DEATH,
                "only Paper's death cause confirms an ordinary mob death");
        check(WaveMobRemovalPolicy.classify(true, false, "UNLOAD")
                        == WaveMobRemovalPolicy.Disposition.UNLOAD,
                "chunk unload retains its separate lifecycle fence");
        check(WaveMobRemovalPolicy.classify(true, true, "PLUGIN")
                        == WaveMobRemovalPolicy.Disposition.AUTHORIZED_CLEANUP,
                "a removal after explicit event-owned cleanup is accepted");
        for (String cause : new String[] {"PLUGIN", "DESPAWN", "DISCARD", "TRANSFORMATION"}) {
            check(WaveMobRemovalPolicy.classify(true, false, cause)
                            == WaveMobRemovalPolicy.Disposition.UNEXPECTED,
                    "unapproved " + cause + " removal must not release wave counters");
        }
        check(WaveMobRemovalPolicy.classify(false, false, "PLUGIN")
                        == WaveMobRemovalPolicy.Disposition.IGNORE
                        && WaveMobRemovalPolicy.classify(true, false, null)
                        == WaveMobRemovalPolicy.Disposition.UNEXPECTED,
                "old-session entities are ignored and missing removal evidence fails closed");
        System.out.println("WaveMobRemovalPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
