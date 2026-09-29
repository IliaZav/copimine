import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import me.copimine.endevent.runtime.EncounterResourceScope;

public final class EncounterResourceScopeTest {
    public static void main(String[] args) {
        List<String> order = new ArrayList<>();
        EncounterResourceScope scope = new EncounterResourceScope(3L, null);
        AtomicInteger firstCloserAttempts = new AtomicInteger();
        scope.registerCloser(() -> {
            order.add("first");
            if (firstCloserAttempts.incrementAndGet() == 1) {
                throw new IllegalStateException("first closer failed");
            }
        });
        scope.registerCloser(() -> order.add("second"));

        EncounterResourceScope.CleanupResult result = scope.closeResources();
        check(!result.success(), "cleanup failure must be returned to the owner");
        check(result.failures().size() == 1, "one closer failure must be recorded");
        check(order.equals(List.of("second", "first")),
                "all closers must run in reverse registration order");
        check(!scope.closed(), "scope remains retryable after a cleanup failure");
        check(scope.closeResources().success(), "a repeated close must retry failed cleanup");
        check(scope.closed(), "scope closes after every failed cleanup action succeeds");
        check(order.equals(List.of("second", "first", "first")),
                "successful cleanup actions must not run again during retry");
        check(scope.closeResources().success() && order.size() == 3,
                "closing a successful scope remains idempotent");

        EncounterResourceScope throwingScope = new EncounterResourceScope(4L, null);
        throwingScope.registerCloser(() -> { throw new IllegalArgumentException("boom"); });
        try {
            throwingScope.close();
        } catch (EncounterResourceScope.CleanupException expected) {
            check(expected.result().failures().size() == 1,
                    "close must expose the same cleanup failure result");
            check(!throwingScope.closed(), "a failed close stays retryable");
            System.out.println("EncounterResourceScopeTest OK");
            return;
        }
        throw new AssertionError("AutoCloseable close must propagate cleanup failure");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
