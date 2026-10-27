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
        require(BossEdgeVolleyPolicy.decide(true, true, false)
                        == BossEdgeVolleyPolicy.Decision.START,
                "an allowed phase, an expired cooldown and a safe corner must start the volley");
        require(BossEdgeVolleyPolicy.decide(true, false, false)
                        == BossEdgeVolleyPolicy.Decision.NO_CORNER,
                "a missing safe corner must abort the volley before any salvo is scheduled");
        require(BossEdgeVolleyPolicy.decide(true, true, true)
                        == BossEdgeVolleyPolicy.Decision.GATED,
                "a volley inside the cooldown must stay gated");
        require(BossEdgeVolleyPolicy.decide(false, true, false)
                        == BossEdgeVolleyPolicy.Decision.GATED,
                "a phase that does not allow the volley must stay gated");
        require(BossEdgeVolleyPolicy.relocated(0.0D) && BossEdgeVolleyPolicy.relocated(7.5D),
                "a boss that reached the corner must count as repositioned");
        require(!BossEdgeVolleyPolicy.relocated(20.0D),
                "a boss that never reached the corner must count as a failed reposition");
        require(!BossEdgeVolleyPolicy.relocated(Double.NaN),
                "an unknown distance must count as a failed reposition");
        require(BossEdgeVolleyPolicy.ARRIVAL_AIM_TICKS >= 10L,
                "the boss needs a readable aim window before the first salvo");
        require(BossEdgeVolleyPolicy.salvoAllowed(BossPhase.RIFT, true, true, true),
                "a live RIFT boss with a legal target keeps firing its salvos");
        require(!BossEdgeVolleyPolicy.salvoAllowed(BossPhase.LAST_SEAL, true, true, true),
                "entering LAST_SEAL must cancel the remaining salvos");
        require(!BossEdgeVolleyPolicy.salvoAllowed(BossPhase.RIFT, false, true, true),
                "a finished encounter must cancel the remaining salvos");
        require(!BossEdgeVolleyPolicy.salvoAllowed(BossPhase.RIFT, true, false, true),
                "a dead boss must cancel the remaining salvos");
        require(!BossEdgeVolleyPolicy.salvoAllowed(BossPhase.RIFT, true, true, false),
                "an illegal target must cancel the remaining salvos");
        System.out.println("BossEdgeVolleyPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}