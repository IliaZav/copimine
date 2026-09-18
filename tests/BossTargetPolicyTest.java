import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.BossTargetPolicy;

public final class BossTargetPolicyTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");

    public static void main(String[] args) {
        BossTargetPolicy.TargetDecision locked = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), FIRST, List.of(), 1_000L, 6_000L, 0L);
        check(locked.target().equals(FIRST), "a valid target must remain during its lock window");
        check(locked.newLockUntilMillis() == 6_000L, "a retained target must keep its lock deadline");

        BossTargetPolicy.TargetDecision expired = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), FIRST, List.of(), 6_000L, 6_000L, 0L);
        check(expired.target().equals(SECOND), "an expired two-player lock must switch once, not oscillate");
        check(expired.newLockUntilMillis() == 11_000L,
                "a newly selected target must receive one bounded lock window");

        BossTargetPolicy.TargetDecision invalid = BossTargetPolicy.chooseTarget(
                List.of(SECOND, THIRD), FIRST, List.of(FIRST), 2_000L, 10_000L, 0L);
        check(invalid.target().equals(SECOND), "an invalid target must be replaced immediately");
        check(BossTargetPolicy.chooseTarget(List.of(), FIRST, List.of(), 2_000L, 10_000L, 0L).target() == null,
                "an empty roster must produce no target");

        BossTargetPolicy.TargetDecision first = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), null, List.of(), 0L, 0L, 0L);
        BossTargetPolicy.TargetDecision held = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), first.target(), List.of(first.target()), 2_500L,
                first.newLockUntilMillis(), 1L);
        check(held.target().equals(first.target()), "refreshes must not ping-pong between two players");
        BossTargetPolicy.TargetDecision next = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), held.target(), List.of(held.target()),
                held.newLockUntilMillis(), held.newLockUntilMillis(), 1L);
        check(!next.target().equals(held.target()), "the next choice may change only after the lock expires");

        BossTargetPolicy.TargetDecision deterministicA = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND, THIRD), null, List.of(), 100L, 0L, 1L);
        BossTargetPolicy.TargetDecision deterministicB = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND, THIRD), null, List.of(), 100L, 0L, 1L);
        check(deterministicA.equals(deterministicB), "the same roster and cursor must be deterministic");
        System.out.println("BossTargetPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
