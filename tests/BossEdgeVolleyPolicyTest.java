import me.copimine.endevent.domain.BossEdgeVolleyPolicy;
import me.copimine.endevent.domain.BossPhase;

public final class BossEdgeVolleyPolicyTest {
    public static void main(String[] args) {
        require(BossEdgeVolleyPolicy.SALVO_COUNT == 5,
                "the edge volley must fire exactly five salvos");
        require(BossEdgeVolleyPolicy.COOLDOWN_MILLIS >= 60_000L
                        && BossEdgeVolleyPolicy.COOLDOWN_MILLIS <= 90_000L,
                "the edge volley cooldown must stay in the authored 60-90 s window");
        for (BossPhase phase : new BossPhase[]{
                BossPhase.RIFT, BossPhase.OVERLOAD, BossPhase.RAGE}) {
            require(BossEdgeVolleyPolicy.allowsPhase(phase),
                    phase + " must allow the edge volley");
        }
        for (BossPhase phase : new BossPhase[]{
                BossPhase.AWAKENING, BossPhase.HUNT, BossPhase.LAST_SEAL}) {
            require(!BossEdgeVolleyPolicy.allowsPhase(phase),
                    phase + " must not allow the edge volley");
        }
        require(BossEdgeVolleyPolicy.onCooldown(1_000L, 2_000L),
                "an early recast must be gated by the cooldown");
        require(!BossEdgeVolleyPolicy.onCooldown(2_000L, 2_000L),
                "the cooldown must expire at the deadline");
        System.out.println("BossEdgeVolleyPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}