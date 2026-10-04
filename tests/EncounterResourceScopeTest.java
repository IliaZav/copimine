import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import me.copimine.endevent.runtime.EncounterResourceScope;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

public final class EncounterResourceScopeTest {
    public static void main(String[] args) {
        List<String> order = new ArrayList<>();
        EncounterResourceScope scope = new EncounterResourceScope(3L, "WAVE_6:wave-6", null);
        check(scope.owner().equals("WAVE_6:wave-6"),
                "resource scopes expose the phase and wave that own their resources");
        scope.registerCloser(() -> {
            order.add("first");
            throw new IllegalStateException("first closer failed");
        });
        scope.registerCloser(() -> order.add("second"));

        EncounterResourceScope.CleanupResult result = scope.closeResources();
        check(!result.success(), "cleanup failure must be returned to the owner");
        check(result.failures().size() == 1, "one closer failure must be recorded");
        check(order.equals(List.of("second", "first")),
                "all closers must run in reverse registration order");
        check(scope.closed(), "scope must be closed after cleanup attempt");
        check(scope.closeResources().failures().size() == 1,
                "repeated close must preserve the auditable failure result");

        failedCleanupIsRetriedWithoutReplayingCompletedActions();
        entityCleanupCanUpgradeAnExistingHandle();
        failedEntityCleanupKeepsLiveEntityUntilRetryCompletes();
        failedTaskCancellationIsRetried();
        tasksRegisteredDuringCleanupAreCancelledImmediately(false);
        tasksRegisteredDuringCleanupAreCancelledImmediately(true);

        EncounterResourceScope throwingScope = new EncounterResourceScope(4L, null);
        throwingScope.registerCloser(() -> { throw new IllegalArgumentException("boom"); });
        try {
            throwingScope.close();
        } catch (EncounterResourceScope.CleanupException expected) {
            check(expected.result().failures().size() == 1,
                    "close must expose the same cleanup failure result");
            System.out.println("EncounterResourceScopeTest OK");
            return;
        }
        throw new AssertionError("AutoCloseable close must propagate cleanup failure");
    }

    private static void failedCleanupIsRetriedWithoutReplayingCompletedActions() {
        EncounterResourceScope scope = new EncounterResourceScope(5L, null);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        scope.registerCloser(() -> completed.incrementAndGet());
        scope.registerCloser(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("retry once");
            }
        });

        EncounterResourceScope.CleanupResult first = scope.closeResources();
        check(!first.success(), "a failed cleanup action must remain visible");
        check(completed.get() == 1, "successful cleanup must run on the first close");
        check(scope.closed(), "a closing scope must reject new gameplay work");

        EncounterResourceScope.CleanupResult second = scope.closeResources();
        check(second.success(), "a later close must retry the failed cleanup action");
        check(attempts.get() == 2, "failed cleanup must be attempted again exactly once");
        check(completed.get() == 1, "successful cleanup must not be replayed on retry");
        check(scope.closeResources().success(), "successful cleanup must remain idempotent");
    }

    private static void entityCleanupCanUpgradeAnExistingHandle() {
        EncounterResourceScope scope = new EncounterResourceScope(6L, "WAVE_6", null);
        UUID entityId = UUID.randomUUID();
        List<String> order = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        Runnable legacyRegistration = () -> order.add("legacy");
        Runnable richerCleanup = () -> {
            order.add("richer-attempt");
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("entity cleanup retry once");
            }
        };
        scope.registerEntity(entityId);
        scope.registerEntity(entityId, legacyRegistration);
        scope.registerEntity(entityId, richerCleanup);
        scope.registerEntity(entityId, richerCleanup);

        check(!scope.closeResources().success(), "failed entity cleanup must be retained");
        check(order.equals(List.of("richer-attempt", "legacy")),
                "all distinct callbacks must run newest-first when a UUID is upgraded");
        check(scope.closeResources().success(), "failed entity callback must be retried");
        check(order.equals(List.of("richer-attempt", "legacy", "richer-attempt")),
                "successful entity callbacks must not repeat on retry");
    }

    private static void failedTaskCancellationIsRetried() {
        EncounterResourceScope scope = new EncounterResourceScope(7L, null);
        AtomicInteger attempts = new AtomicInteger();
        AtomicBoolean cancelled = new AtomicBoolean();
        BukkitTask task = (BukkitTask) java.lang.reflect.Proxy.newProxyInstance(
                BukkitTask.class.getClassLoader(), new Class<?>[]{BukkitTask.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getTaskId" -> 77;
                    case "isCancelled" -> cancelled.get();
                    case "cancel" -> {
                        if (attempts.incrementAndGet() == 1) {
                            throw new IllegalStateException("task cancellation retry once");
                        }
                        cancelled.set(true);
                        yield null;
                    }
                    case "toString" -> "retryable-task";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
        scope.registerTask(task);

        check(!scope.closeResources().success(), "failed task cancellation must be reported");
        check(scope.closeResources().success(), "task cancellation must be retried");
        check(attempts.get() == 2, "task handle must survive until cancellation succeeds");
    }

    private static void tasksRegisteredDuringCleanupAreCancelledImmediately(boolean failFirst) {
        EncounterResourceScope scope = new EncounterResourceScope(9L, null);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger otherCleanup = new AtomicInteger();
        AtomicBoolean cancelled = new AtomicBoolean();
        BukkitTask task = (BukkitTask) Proxy.newProxyInstance(BukkitTask.class.getClassLoader(),
                new Class<?>[]{BukkitTask.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getTaskId" -> 99;
                    case "isCancelled" -> cancelled.get();
                    case "cancel" -> {
                        if (attempts.incrementAndGet() == 1 && failFirst)
                            throw new IllegalStateException("nested cancellation failed once");
                        cancelled.set(true);
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
        scope.registerCloser(otherCleanup::incrementAndGet);
        scope.registerCloser(() -> {
            scope.registerTask(task);
            check(attempts.get() == 1, "a task registered during cleanup must be cancelled before register returns");
            check(cancelled.get() != failFirst, "nested task cancellation state must reflect the actual attempt");
        });
        var result = scope.closeResources();
        check(otherCleanup.get() == 1, "nested cancellation failure must not interrupt remaining cleanup");
        check(result.success() != failFirst, "nested cancellation failure must be auditable");
        if (failFirst) {
            check(result.failures().stream().anyMatch(f -> f.resourceType().equals("task")
                    && f.error().getMessage().equals("nested cancellation failed once")),
                    "audit must retain the actual cancellation error");
            check(scope.closeResources().success() && cancelled.get() && attempts.get() == 2,
                    "a failed nested task must remain owned for retry");
            check(otherCleanup.get() == 1, "retry must not repeat successful closers");
        }
    }

    private static void failedEntityCleanupKeepsLiveEntityUntilRetryCompletes() {
        UUID entityId = UUID.randomUUID();
        AtomicBoolean alive = new AtomicBoolean(true);
        AtomicInteger removals = new AtomicInteger();
        Entity entity = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> entityId;
                    case "isValid" -> alive.get();
                    case "isDead" -> !alive.get();
                    case "remove" -> { removals.incrementAndGet(); alive.set(false); yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(),
                new Class<?>[]{Server.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getEntity")) {
                        return entityId.equals(arguments[0]) && alive.get() ? entity : null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            Object previousServer = serverField.get(null);
            serverField.set(null, server);
            try {
                EncounterResourceScope scope = new EncounterResourceScope(8L, "WAVE_6", null);
                AtomicInteger attempts = new AtomicInteger();
                AtomicInteger completed = new AtomicInteger();
                scope.registerEntity(entityId, () -> completed.incrementAndGet());
                scope.registerEntity(entityId, () -> {
                    check(Bukkit.getEntity(entityId) != null,
                            "entity-specific cleanup must retain access to a live entity on retry");
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("release player before entity removal");
                    }
                });

                check(!scope.closeResources().success(), "failed entity-specific cleanup must be reported");
                check(alive.get() && removals.get() == 0,
                        "native entity removal must wait for failed entity-specific cleanup to succeed");
                check(scope.closeResources().success(), "entity-specific cleanup must succeed on retry");
                check(attempts.get() == 2 && completed.get() == 1,
                        "retry must retain failed callbacks and never replay completed callbacks");
                check(!alive.get() && removals.get() == 1,
                        "native removal runs once after every entity-specific callback succeeds");
                check(scope.closeResources().success() && removals.get() == 1,
                        "a completed entity cleanup must remain idempotent");
            } finally {
                serverField.set(null, previousServer);
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("cannot install detached Bukkit entity fixture", error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
