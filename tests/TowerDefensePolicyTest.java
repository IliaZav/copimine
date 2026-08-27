import me.copimine.endevent.domain.TowerDefensePolicy;

public final class TowerDefensePolicyTest {
    public static void main(String[] args) {
        TowerDefensePolicy.CoreState two = TowerDefensePolicy.start(2, 100L);
        TowerDefensePolicy.CoreState many = TowerDefensePolicy.start(20, 100L);
        check(two.maxHealth() == 1_200.0D, "two players must use base health");
        check(many.maxHealth() == 4_200.0D, "core health must be capped");
        check(two.deadlineMillis() == 180_100L, "deadline must be exactly three minutes");
        check(TowerDefensePolicy.finish(two, 180_100L).outcome() == TowerDefensePolicy.Outcome.SUCCESS,
                "finish at the deadline with health must succeed");
        check(TowerDefensePolicy.finish(two, 180_101L).outcome() == TowerDefensePolicy.Outcome.FAILURE,
                "finish after the deadline must fail");

        TowerDefensePolicy.CoreState damaged = TowerDefensePolicy.damage(two, "hit-1", 100.0D);
        check(damaged.currentHealth() == 1_100.0D, "valid attack must damage the core");
        check(TowerDefensePolicy.damage(damaged, "hit-1", 100.0D).currentHealth() == 1_100.0D,
                "attack IDs must be idempotent");
        check(TowerDefensePolicy.damage(damaged, " ", 100.0D).equals(damaged), "blank IDs must fail closed");
        check(TowerDefensePolicy.damage(damaged, "bad", -1.0D).equals(damaged), "negative damage must fail closed");
        check(TowerDefensePolicy.finish(TowerDefensePolicy.damage(two, "lethal", 2_000.0D), 101L).outcome()
                        == TowerDefensePolicy.Outcome.FAILURE, "depleted core must fail");

        TowerDefensePolicy.CoreState failed = TowerDefensePolicy.finish(
                TowerDefensePolicy.damage(two, "lethal", 2_000.0D), 101L);
        TowerDefensePolicy.CoreState retry = TowerDefensePolicy.retry(failed, 5_000L);
        check(retry.attempt() == failed.attempt() + 1, "retry must advance attempt");
        check(retry.currentHealth() == retry.maxHealth(), "retry must restore full health");
        check(retry.deadlineMillis() == 185_000L, "retry must start a fresh three-minute deadline");
        check(TowerDefensePolicy.retry(two, 5_000L).equals(two), "active cores must not be retried");
        check(TowerDefensePolicy.retry(TowerDefensePolicy.finish(two, 100L), 5_000L)
                        .equals(TowerDefensePolicy.finish(two, 100L)), "successful cores must not be retried");
        check(TowerDefensePolicy.damage(two, "nan", Double.NaN).equals(two), "NaN damage must fail closed");
        check(TowerDefensePolicy.damage(two, "infinity", Double.POSITIVE_INFINITY).equals(two), "infinite damage must fail closed");
        boolean invalidStart = false;
        try { TowerDefensePolicy.start(-1, 0L); } catch (IllegalArgumentException expected) { invalidStart = true; }
        check(invalidStart, "negative player counts must fail closed");
        boolean invalidRetry = false;
        try { TowerDefensePolicy.retry(failed, -1L); } catch (IllegalArgumentException expected) { invalidRetry = true; }
        check(invalidRetry, "negative retry timestamps must fail closed");
        boolean invalidFinish = false;
        try { TowerDefensePolicy.finish(two, -1L); } catch (IllegalArgumentException expected) { invalidFinish = true; }
        check(invalidFinish, "negative finish timestamps must fail closed");
        boolean overflowStart = false;
        try { TowerDefensePolicy.start(2, Long.MAX_VALUE); } catch (IllegalArgumentException expected) { overflowStart = true; }
        check(overflowStart, "overflowing deadlines must fail closed");
        System.out.println("TowerDefensePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
