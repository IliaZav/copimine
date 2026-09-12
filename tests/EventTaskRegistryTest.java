import me.copimine.endevent.EventTaskRegistry;
import java.util.concurrent.atomic.AtomicInteger;

public final class EventTaskRegistryTest {
    public static void main(String[] args) {
        expectIllegalArgument(() -> new EventTaskRegistry(0L),
                "zero generation must be rejected");
        expectIllegalArgument(() -> new EventTaskRegistry(-1L),
                "negative generation must be rejected");
        EventTaskRegistry registry = new EventTaskRegistry(4L);
        check(registry.generation() == 4L, "registry must retain its positive generation");
        check(registry.owns(4L), "current generation must be accepted");
        check(!registry.owns(3L), "stale generation must be rejected");
        AtomicInteger callbacks = new AtomicInteger();
        check(registry.runIfOwned(4L, callbacks::incrementAndGet),
                "a current-generation callback must execute");
        check(!registry.runIfOwned(3L, callbacks::incrementAndGet),
                "a stale-generation callback must not execute");
        check(callbacks.get() == 1, "stale callback must not mutate runtime state");
        System.out.println("EventTaskRegistryTest OK");
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
