import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Logger;
import me.copimine.endevent.diagnostics.BoundedDiagnosticBurstCapture;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticEvent;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticInvariantMonitor;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticSink;

public final class EndRiftDiagnosticInvariantMonitorTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("end-rift-invariants");
        EndRiftDiagnosticSink sink = new EndRiftDiagnosticSink(
                directory.resolve("events.jsonl"), Logger.getLogger("EndRiftInvariant"));
        BoundedDiagnosticBurstCapture burst = new BoundedDiagnosticBurstCapture(8, 50L);
        EndRiftDiagnosticInvariantMonitor monitor =
                new EndRiftDiagnosticInvariantMonitor(sink, burst);

        check(monitor.require(true, "GOOD", "attempt:1", Map.of()),
                "satisfied invariant must return true");
        check(!monitor.require(false, "BROKEN", "attempt:1", Map.of("reason", "test")),
                "failed invariant must return false");
        check(monitor.failureCount() == 1, "failure count must be observable");
        check(burst.isCapturing(), "invariant failure must trigger bounded burst capture");
        check(monitor.lastFailure().orElseThrow().equals("BROKEN"),
                "last invariant failure must be retained");

        EndRiftDiagnosticEvent sample = new EndRiftDiagnosticEvent(
                0L, Instant.now(), 12L, 1L, "event", "EVENT", "SNAPSHOT", "INFO",
                6, "WAVE_6", null, null, null, "attempt:1", "sample", Map.of());
        burst.sample(sample);
        sink.closeAndFlush();
        check(sink.stats().written() == 1L, "invariant failure must be written once");
        System.out.println("EndRiftDiagnosticInvariantMonitorTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
