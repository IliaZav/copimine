import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticEvent;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticJson;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticSink;
import me.copimine.endevent.diagnostics.EndRiftDiagnosticSinkStats;

public final class EndRiftDiagnosticSinkTest {
    private static final Logger LOGGER = Logger.getLogger("EndRiftDiagnosticSinkTest");

    public static void main(String[] args) throws Exception {
        testFlushAndRotation();
        testConcurrentPublicationAndClosePolicy();
        testDropAccountingUnderBurst();
        testSequenceContinuesAcrossWriterRestart();
        System.out.println("EndRiftDiagnosticSinkTest OK");
    }

    private static void testFlushAndRotation() throws Exception {
        Path directory = Files.createTempDirectory("end-rift-diagnostics-rotation");
        Path path = directory.resolve("events.jsonl");
        try (EndRiftDiagnosticSink sink = new EndRiftDiagnosticSink(
                path, LOGGER, 32, 380L)) {
            for (int i = 0; i < 24; i++) {
                check(sink.publish(event(i)), "normal publication must be accepted");
            }
            check(sink.awaitIdle(5L, TimeUnit.SECONDS), "sink must become idle before close");
            check(sink.stats().dropped() == 0L, "normal flush must not drop events");
            check(sink.stats().written() == 24L, "writer must account for every accepted event");
            check(sink.stats().rotations() > 0L, "small test limit must exercise rotation");
        }

        List<String> lines = new ArrayList<>();
        for (Path candidate : List.of(path, path.resolveSibling(path.getFileName() + ".1"))) {
            if (Files.exists(candidate)) {
                lines.addAll(Files.readAllLines(candidate, StandardCharsets.UTF_8));
            }
        }
        check(!lines.isEmpty(), "rotation must leave JSONL evidence on disk");
        for (String line : lines) {
            check(line.startsWith("{") && line.endsWith("}"),
                    "rotation must never split a JSONL line");
        }
    }

    private static void testConcurrentPublicationAndClosePolicy() throws Exception {
        Path directory = Files.createTempDirectory("end-rift-diagnostics-concurrent");
        Path path = directory.resolve("events.jsonl");
        EndRiftDiagnosticSink sink = new EndRiftDiagnosticSink(path, LOGGER, 4096, 1_000_000L);
        int workers = 8;
        int each = 125;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        AtomicInteger accepted = new AtomicInteger();
        for (int worker = 0; worker < workers; worker++) {
            int workerId = worker;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int index = 0; index < each; index++) {
                        if (sink.publish(event(workerId * each + index))) {
                            accepted.incrementAndGet();
                        }
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "diagnostic-publisher-" + worker);
            thread.start();
        }
        start.countDown();
        check(done.await(5L, TimeUnit.SECONDS), "all diagnostic publishers must finish");
        check(sink.awaitIdle(5L, TimeUnit.SECONDS), "concurrent sink must become idle");
        sink.closeAndFlush();
        check(accepted.get() == workers * each, "large concurrent queue must accept all events");
        check(sink.stats().accepted() == accepted.get(), "accepted accounting must be exact");
        check(sink.stats().written() == accepted.get(), "written accounting must be exact");
        check(!sink.publish(event(999_999)), "publication after close must be ignored");
    }

    private static void testDropAccountingUnderBurst() throws Exception {
        Path directory = Files.createTempDirectory("end-rift-diagnostics-drop");
        Path path = directory.resolve("events.jsonl");
        EndRiftDiagnosticSink sink = new EndRiftDiagnosticSink(path, LOGGER, 1, 1_000_000L);
        int workers = 8;
        int each = 2_000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        for (int worker = 0; worker < workers; worker++) {
            int workerId = worker;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int index = 0; index < each; index++) {
                        sink.publish(event(workerId * each + index));
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "diagnostic-drop-publisher-" + worker);
            thread.start();
        }
        start.countDown();
        check(done.await(5L, TimeUnit.SECONDS), "drop stress publishers must finish");
        check(sink.awaitIdle(5L, TimeUnit.SECONDS), "drop stress sink must become idle");
        EndRiftDiagnosticSinkStats beforeClose = sink.stats();
        check(beforeClose.submitted() == workers * (long) each,
                "submitted count must include every burst attempt");
        check(beforeClose.accepted() + beforeClose.dropped() == beforeClose.submitted(),
                "accepted plus dropped must equal submitted");
        check(beforeClose.dropped() >= 0L, "drop count must never be negative");
        sink.closeAndFlush();
        check(sink.stats().written() == beforeClose.accepted(),
                "every accepted burst event must be written or accounted before close");
        check(!sink.publish(event(100_000)), "closed sink must reject a new event");
        check(sink.stats().submitted() == beforeClose.submitted() + 1L,
                "post-close publication must remain observable in submitted accounting");
        check(sink.stats().dropped() == beforeClose.dropped() + 1L,
                "post-close publication must be counted as dropped");
    }

    private static void testSequenceContinuesAcrossWriterRestart() throws Exception {
        Path directory = Files.createTempDirectory("end-rift-diagnostics-sequence");
        Path path = directory.resolve("events.jsonl");
        Files.writeString(path, EndRiftDiagnosticJson.toJson(event(17).withSequence(17L))
                + System.lineSeparator(), StandardCharsets.UTF_8);
        try (EndRiftDiagnosticSink sink = new EndRiftDiagnosticSink(
                path, LOGGER, 8, 100_000L)) {
            check(sink.publish(event(18)), "post-restart event must be accepted");
            check(sink.awaitIdle(5L, TimeUnit.SECONDS), "post-restart sink must become idle");
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        check(lines.size() == 2, "sequence restart fixture must contain both records");
        check(lines.get(1).contains("\"sequence\":18"),
                "sink must continue after the existing highest sequence");
    }

    private static EndRiftDiagnosticEvent event(int index) {
        return new EndRiftDiagnosticEvent(
                0L, Instant.parse("2026-09-17T20:00:00Z"), index, 1L,
                "sink-test", "EVENT", "SNAPSHOT", "INFO", 6,
                "WAVE_6", null, null, null, "event:1:test:" + index,
                "TEST", Map.of("index", index, "text", "bounded"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
