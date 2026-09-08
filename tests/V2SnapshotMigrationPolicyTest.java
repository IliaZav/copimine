import me.copimine.endevent.EventSnapshot;
import me.copimine.endevent.V2SnapshotMigrationPolicy;
import me.copimine.endevent.domain.EventPhase;

public final class V2SnapshotMigrationPolicyTest {
    public static void main(String[] args) {
        check(V2SnapshotMigrationPolicy.canonicalPhase(EventPhase.FINAL_DRAIN)
                        == EventPhase.READY_FOR_PLAYERS,
                "a legacy active final-drain snapshot must recover safely to V2 ready");
        check(V2SnapshotMigrationPolicy.canonicalPhase(EventPhase.FINAL_RITUAL)
                        == EventPhase.READY_FOR_PLAYERS,
                "a legacy ritual snapshot must recover safely to V2 ready");
        check(V2SnapshotMigrationPolicy.canonicalPhase(EventPhase.VICTORY)
                        == EventPhase.VICTORY_PROCESSING,
                "legacy victory must retain only its durable V2 reward-processing hand-off");

        EventSnapshot original = EventSnapshot.empty(1);
        EventSnapshot migrated = V2SnapshotMigrationPolicy.migrate(original, 2);
        check(migrated.schemaVersion() == 2, "migration must stamp the target schema");
        check(migrated.eventPhase() == EventPhase.UNCONFIGURED,
                "migration must preserve non-active state rather than manufacture an attempt");
        check(migrated.resourceRequirements().equals(original.resourceRequirements()),
                "migration must not discard durable resource requirements");
        check(migrated.generation() == original.generation(),
                "migration must not advance generation and invalidate the live map");
        System.out.println("V2SnapshotMigrationPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
