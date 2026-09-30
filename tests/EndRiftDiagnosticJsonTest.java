import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticEvent;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticJson;

public final class EndRiftDiagnosticJsonTest {
    public static void main(String[] args) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("originSource", "SPHERE");
        fields.put("targetExcludedPrisoner", true);
        fields.put("damage", 6.0D);
        fields.put("escaped", "quote\" slash\\ line\n tab\t unicode-Привет");
        EndRiftDiagnosticEvent event = new EndRiftDiagnosticEvent(
                7L, Instant.parse("2026-09-17T20:00:00Z"), 1234L, 42L,
                "end-rift", "RITUAL_PROJECTILE", "SPAWN", "INFO", 6,
                "WAVE_6", null, null, null, "projectile:42:test",
                "ABILITY_CAST", fields);

        String json = EndRiftDiagnosticJson.toJson(event);

        check(json.contains("\"sequence\":7"), "sequence must be serialized");
        check(json.contains("\"serverTick\":1234"), "server tick must be serialized");
        check(json.contains("\"generation\":42"), "generation must be serialized");
        check(json.contains("\"category\":\"RITUAL_PROJECTILE\""),
                "category must be serialized");
        check(json.contains("\"correlationId\":\"projectile:42:test\""),
                "correlation id must be serialized");
        check(json.contains("\"originSource\":\"SPHERE\""),
                "custom fields must be serialized");
        check(json.contains("\\n") && json.contains("\\t") && json.contains("\\\\"),
                "JSON control characters must be escaped");
        check(!json.contains("quote\" slash"), "raw quotes must not break JSON");

        EndRiftDiagnosticEvent resequenced = event.withSequence(8L);
        check(resequenced.sequence() == 8L, "withSequence must preserve all other event data");
        check(resequenced.correlationId().equals(event.correlationId()),
                "withSequence must preserve correlation");
        System.out.println("EndRiftDiagnosticJsonTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
