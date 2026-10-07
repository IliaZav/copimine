import me.copimine.endevent.EventTaskRegistry;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.scheduler.BukkitTask;

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
        AtomicBoolean cancelled = new AtomicBoolean();
        BukkitTask task = (BukkitTask) Proxy.newProxyInstance(
                EventTaskRegistryTest.class.getClassLoader(),
                new Class<?>[] {BukkitTask.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getTaskId" -> 77;
                    case "isCancelled" -> cancelled.get();
                    case "cancel" -> {
                        cancelled.set(true);
                        yield null;
                    }
                    case "isSync" -> true;
                    case "getOwner" -> null;
                    case "toString" -> "test-task-77";
                    default -> defaultValue(method.getReturnType());
                });
        registry.register(task);
        cancelled.set(true);
        check(registry.taskIds().isEmpty(), "cancelled task must leave the active registry");
        check(registry.drainCancelledTaskIds().equals(java.util.List.of(77)),
                "cancelled task id must remain observable for diagnostics");
        System.out.println("EventTaskRegistryTest OK");
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        return null;
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
