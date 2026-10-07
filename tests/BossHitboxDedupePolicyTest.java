import me.copimine.endevent.domain.BossHitboxDedupePolicy;

public final class BossHitboxDedupePolicyTest {
    public static void main(String[] args) {
        BossHitboxDedupePolicy dedupe = new BossHitboxDedupePolicy(4);
        check(dedupe.accept("melee:player-a:100", 7L, 1_000L),
                "first melee transaction must be accepted");
        check(!dedupe.accept("melee:player-a:100", 7L, 1_001L),
                "same melee transaction must be accepted only once");
        check(dedupe.accept("projectile:arrow-a", 7L, 1_002L),
                "first projectile must be accepted");
        check(!dedupe.accept("projectile:arrow-a", 7L, 1_003L),
                "same projectile must be accepted only once");
        check(dedupe.accept("projectile:arrow-b", 7L, 1_004L),
                "different projectile must be accepted");
        check(dedupe.accept("expired", 7L, 2_000L),
                "bounded storage must evict expired entries");
        check(dedupe.accept("melee:player-a:100", 8L, 2_001L),
                "a new encounter generation must not inherit old dedupe state");
        check(dedupe.size() <= 4, "dedupe storage must stay bounded");
        System.out.println("BossHitboxDedupePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
