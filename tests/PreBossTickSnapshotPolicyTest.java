import java.util.Map;
import me.copimine.endevent.domain.PreBossTickSnapshotPolicy;

public final class PreBossTickSnapshotPolicyTest {
    public static void main(String[] args) {
        Map<String, String> saved = PreBossTickSnapshotPolicy.encode("event", 7L, 300L);
        check(PreBossTickSnapshotPolicy.decode(saved, "event", 7L) == 300L, "matching run restores elapsed server ticks");
        check(PreBossTickSnapshotPolicy.decode(Map.of(), "event", 7L) == 0L,
                "legacy wall-clock deadline must not instantly start a boss after restart");
        fails(() -> PreBossTickSnapshotPolicy.decode(saved, "event", 8L));
        fails(() -> PreBossTickSnapshotPolicy.decode(saved, "other", 7L));
        fails(() -> PreBossTickSnapshotPolicy.decode(Map.of("pre-boss-clock", "1"), "event", 7L));
        fails(() -> PreBossTickSnapshotPolicy.encode("event", 7L, 801L));
        fails(() -> PreBossTickSnapshotPolicy.encode("event", 7L, -1L));
        Map<String, String> interruptedHandoff = new java.util.HashMap<>(saved);
        interruptedHandoff.put("pre-boss-handoff", "STARTED");
        fails(() -> PreBossTickSnapshotPolicy.decode(interruptedHandoff, "event", 7L));
        System.out.println("PreBossTickSnapshotPolicyTest OK");
    }
    private static void fails(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid or stale timer data must fail closed");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
