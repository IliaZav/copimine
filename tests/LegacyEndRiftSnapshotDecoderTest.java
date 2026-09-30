import me.copimine.endevent.EventSnapshot;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.migration.LegacyEndRiftSnapshotDecoder;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LegacyEndRiftSnapshotDecoderTest {
    public static void main(String[] args) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 3);
        yaml.set("event.event-id", "legacy");
        yaml.set("event.generation", 9L);
        yaml.set("event.phase", "FINAL_DRAIN");
        yaml.set("event.world", "CopiMine");
        yaml.set("event.required-players", 2);
        EventSnapshot migrated = LegacyEndRiftSnapshotDecoder.decode(yaml, 4);
        check(migrated.schemaVersion() == 4, "decoder must output current schema");
        check(migrated.eventPhase() == EventPhase.RECOVERY_REQUIRED,
                "ambiguous legacy combat must recover safely");
        check(migrated.generation() == 9L, "migration must preserve generation identity");
        boolean rejected = false;
        try {
            LegacyEndRiftSnapshotDecoder.decode(yaml, 3);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "decoder must reject a non-current target schema");
        System.out.println("LegacyEndRiftSnapshotDecoderTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
