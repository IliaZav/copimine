import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticSnapshot;

public final class EndRiftDiagnosticSnapshotTest {
    public static void main(String[] args) {
        UUID boss = UUID.nameUUIDFromBytes("boss".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        EndRiftDiagnosticSnapshot snapshot = new EndRiftDiagnosticSnapshot(
                "event", 42L, "WAVE_7", 7, "REALITY_SPLIT", boss, 500.0D, 5000.0D,
                null, 4, 12, 3, 2, 1, 22, 7, 11, 152, 152);
        check(snapshot.semanticHash().length() == 64, "semantic state hash must be SHA-256");
        check(snapshot.toFields().equals(snapshot.toFields()), "snapshot fields must be stable");
        check(snapshot.toFields().get("wave7TemporaryBlockCount").equals(152),
                "snapshot must expose Wave 7 resource counts");
        check(snapshot.toJson().contains("\"generation\":42"),
                "snapshot JSON must include identity fields");
        System.out.println("EndRiftDiagnosticSnapshotTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
