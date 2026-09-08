import me.copimine.endevent.domain.AbyssAnchorPolicy;

public final class AbyssAnchorPolicyTest {
    public static void main(String[] args) {
        long now = 1_000_000L;
        AbyssAnchorPolicy.Decision rescue = AbyssAnchorPolicy.evaluate(
                true, false, true, now, 0L, 1_800);
        check(rescue.rescued(), "authentic shard must rescue a void fall");
        check(rescue.health() == 2.0D, "rescue health must be exactly two");
        check(rescue.cooldownUntilMillis() == now + 1_800_000L,
                "anchor cooldown must be thirty minutes");
        check(!AbyssAnchorPolicy.evaluate(true, true, true, now, 0L, 1_800).rescued(),
                "anchor must be disabled during an active attempt");
        check(!AbyssAnchorPolicy.evaluate(false, false, true, now, 0L, 1_800).rescued(),
                "spoofed or missing shard must not rescue");
        check(!AbyssAnchorPolicy.evaluate(true, false, false, now, 0L, 1_800).rescued(),
                "unsafe Core point must fail closed");
        check(!AbyssAnchorPolicy.evaluate(true, false, true, now, rescue.cooldownUntilMillis(), 1_800).rescued(),
                "second rescue during cooldown must be rejected");
        check(AbyssAnchorPolicy.hasPriorityOverTotem(true, true, false, true,
                now, 0L, 1_800), "valid void rescue must win before Totem handling");
        check(!AbyssAnchorPolicy.hasPriorityOverTotem(true, false, false, true,
                now, 0L, 1_800), "non-void damage must not invoke Anchor");
        check(AbyssAnchorPolicy.cooldownUntil(now, 0) == now + 1_000L,
                "cooldown input must be bounded to at least one second");
        check(AbyssAnchorPolicy.cooldownUntil(now, 100_000_000)
                        == now + AbyssAnchorPolicy.MAX_COOLDOWN_SECONDS * 1000L,
                "cooldown input must be bounded to one day");
        System.out.println("AbyssAnchorPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
