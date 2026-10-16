import java.util.HashMap;
import java.util.UUID;
import me.copimine.endevent.domain.ExplosiveVolleyPolicy;

public final class ExplosiveVolleyPolicyTest {
    public static void main(String[] args) {
        var hits = new HashMap<ExplosiveVolleyPolicy.TargetKey, Long>();
        UUID firstVolley = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondVolley = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID otherVolley = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID firstPlayer = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID secondPlayer = UUID.fromString("00000000-0000-0000-0000-000000000011");

        check(ExplosiveVolleyPolicy.admitTarget(hits, firstVolley, firstPlayer, 1_000L),
                "first explosive arrow may damage a player");
        check(!ExplosiveVolleyPolicy.admitTarget(hits, firstVolley, firstPlayer, 1_100L),
                "a side arrow with no live shooter still shares the volley hit limit");
        check(ExplosiveVolleyPolicy.admitTarget(hits, firstVolley, secondPlayer, 1_100L),
                "one volley can still affect a different nearby player");
        check(ExplosiveVolleyPolicy.admitTarget(hits, secondVolley, firstPlayer, 1_100L),
                "another volley from the same shooter remains independent");
        check(ExplosiveVolleyPolicy.admitTarget(hits, otherVolley, firstPlayer, 1_100L),
                "another shooter retains an independent volley");
        check(ExplosiveVolleyPolicy.admitTarget(hits, UUID.fromString(
                        "00000000-0000-0000-0000-000000000004"), firstPlayer,
                        1_000L + ExplosiveVolleyPolicy.TARGET_HIT_WINDOW_MILLIS),
                "a later volley can hit after the bounded cleanup window");
        check(!ExplosiveVolleyPolicy.admitTarget(hits, secondVolley, firstPlayer, 1_000L),
                "a backwards clock fails closed for a recent hit");
        check(!ExplosiveVolleyPolicy.admitTarget(hits, null, firstPlayer, 4_000L)
                        && !ExplosiveVolleyPolicy.admitTarget(hits, firstVolley, null, 4_000L),
                "missing volley or target identity is rejected");
        check(ExplosiveVolleyPolicy.admitTarget(hits, firstVolley, secondPlayer, 13_100L),
                "a new shot is admitted after the previous impact window expires");
        check(hits.size() == 1,
                "expired impact entries are pruned while the next volley is admitted");

        System.out.println("ExplosiveVolleyPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
