import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticMode;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticService;

public final class EndRiftDiagnosticServiceTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("end-rift-diagnostic-service");
        EndRiftDiagnosticService service = new EndRiftDiagnosticService(
                directory.resolve("events.jsonl"), Logger.getLogger("EndRiftDiagnosticServiceTest"),
                "event-1", EndRiftDiagnosticMode.ESSENTIAL, 64, 100_000L);

        check(service.emit(10L, 4L, 6, "WAVE_6", "EVENT", "START", "INFO",
                null, null, null, "attempt:event-1:4", "test", Map.of()),
                "essential lifecycle event must be accepted");
        check(!service.emit(11L, 4L, 6, "WAVE_6", "RITUAL_PROJECTILE", "STEER_SAMPLE", "INFO",
                null, null, null, "projectile:4:p1", "test", Map.of()),
                "trace detail must be filtered in ESSENTIAL mode");
        check(service.stateTransition(12L, 4L, 6, "WAVE_6", "WAITING", "CAPTURED",
                "PHYSICAL_SEAL_ENTRY", true, "test", "ritual:4"),
                "state transition must be accepted");
        check(!service.invariant(false, 13L, 4L, 6, "WAVE_6", "TEST_INVARIANT",
                "attempt:event-1:4", Map.of("observed", false)),
                "invariant failure must remain observational");
        check(service.invariants().failureCount() == 1,
                "service must expose invariant failure count");
        service.closeAndFlush();
        check(service.stats().written() == 3L,
                "service must flush lifecycle, transition and invariant records");
        testStackTraceEscaping(directory);
        System.out.println("EndRiftDiagnosticServiceTest OK");
    }

    private static void testStackTraceEscaping(Path directory) throws Exception {
        Path path = directory.resolve("stack-events.jsonl");
        EndRiftDiagnosticService service = new EndRiftDiagnosticService(
                path, Logger.getLogger("EndRiftDiagnosticStackTest"),
                "event-stack", EndRiftDiagnosticMode.ESSENTIAL, 8, 100_000L);
        service.exception(14L, 4L, 6, "WAVE_6", "attempt:event-stack:4",
                "stack-escaping", new IllegalStateException("line1\nline2\"slash\\"));
        service.closeAndFlush();
        List<String> lines = Files.readAllLines(path);
        check(lines.size() == 1, "escaped stack trace must remain one JSONL record");
        check(lines.get(0).contains("\\n") && lines.get(0).contains("\\\""),
                "stack trace control characters and quotes must be escaped");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
