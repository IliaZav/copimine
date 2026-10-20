import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import me.copimine.endevent.EventSnapshot;
import me.copimine.endevent.domain.EventPhase;

/** Persistence boundaries only; no Bukkit or gameplay. */
public final class EventSnapshotGenerationBoundaryTest {
    public static void main(String[] args) throws Exception {
        for (long invalid : new long[]{Long.MIN_VALUE, -1L, Long.MAX_VALUE}) {
            rejects(Map.of("generation", invalid), "invalid or reserved snapshot generation " + invalid);
        }
        rejects(Map.of("eventId", "configured"), "zero cannot identify a configured event");
        rejects(Map.of("worldName", "world"), "zero cannot identify an event world");
        rejects(Map.of("requiredPlayers", 2), "zero cannot identify a roster");
        rejects(Map.of("phase", "RECOVERY_REQUIRED"), "zero cannot identify an active phase");
        EventSnapshot empty = EventSnapshot.empty(4);
        check(empty.generation() == 0L && !empty.configured(), "empty snapshots retain generation zero");
        check(empty.withSchemaAndPhase(4, EventPhase.UNCONFIGURED).generation() == 0L,
                "an empty unconfigured snapshot does not allocate a generation");
        check(empty.withSchemaAndPhase(4, EventPhase.RECOVERY_REQUIRED).generation() == 1L,
                "recovery of an empty snapshot allocates its first valid identity");
        EventSnapshot last = copy(Map.of("generation", Long.MAX_VALUE - 1L));
        check(last.withSchemaAndPhase(4, EventPhase.RECOVERY_REQUIRED).generation() == Long.MAX_VALUE - 1L,
                "phase changes retain the last supported generation without wrapping");
        System.out.println("EventSnapshotGenerationBoundaryTest OK");
    }

    private static EventSnapshot copy(Map<String, Object> overrides) throws Exception {
        RecordComponent[] components = EventSnapshot.class.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        EventSnapshot empty = EventSnapshot.empty(4);
        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            types[index] = component.getType();
            values[index] = overrides.containsKey(component.getName())
                    ? overrides.get(component.getName()) : component.getAccessor().invoke(empty);
        }
        return EventSnapshot.class.getDeclaredConstructor(types).newInstance(values);
    }

    private static void rejects(Map<String, Object> overrides, String message) throws Exception {
        try {
            copy(overrides);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof IllegalArgumentException) return;
            throw failure;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
