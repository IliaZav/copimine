package me.copimine.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class CopiMineClientLogger {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object LOCK = new Object();
    private static final RemoteDiagnosticRateLimiter REMOTE_LOG_LIMITER =
            new RemoteDiagnosticRateLimiter(5_000_000_000L);
    private static final ThreadLocal<Boolean> SERVER_CALLBACK = new ThreadLocal<>();

    private CopiMineClientLogger() {
    }

    public static void info(String message) {
        if (!allowCurrentContext()) return;
        write("INFO", message, null);
    }

    public static void warn(String message) {
        if (!allowCurrentContext()) return;
        write("WARN", message, null);
    }

    public static void warn(String message, Throwable error) {
        if (!allowCurrentContext()) return;
        write("WARN", message, error);
    }

    public static void error(String message, Throwable error) {
        if (!allowCurrentContext()) return;
        write("ERROR", message, error);
    }

    public static void runFromServer(Runnable callback) {
        boolean nested = Boolean.TRUE.equals(SERVER_CALLBACK.get());
        SERVER_CALLBACK.set(true);
        try {
            callback.run();
        } finally {
            if (nested) {
                SERVER_CALLBACK.set(true);
            } else {
                SERVER_CALLBACK.remove();
            }
        }
    }

    static void runFromServerIf(boolean serverDerived, Runnable callback) {
        if (serverDerived) {
            runFromServer(callback);
        } else {
            callback.run();
        }
    }

    public static void infoFromServer(String message) {
        if (REMOTE_LOG_LIMITER.tryAcquire(System.nanoTime())) write("INFO", message, null);
    }

    public static void warnFromServer(String message) {
        warnFromServer(message, null);
    }

    public static void warnFromServer(String message, Throwable error) {
        if (REMOTE_LOG_LIMITER.tryAcquire(System.nanoTime())) write("WARN", message, error);
    }

    private static boolean allowCurrentContext() {
        return !Boolean.TRUE.equals(SERVER_CALLBACK.get())
                || REMOTE_LOG_LIMITER.tryAcquire(System.nanoTime());
    }

    static boolean isInsideServerCallback() {
        return Boolean.TRUE.equals(SERVER_CALLBACK.get());
    }

    private static void write(String level, String message, Throwable error) {
        String line = "[" + TS.format(LocalDateTime.now()) + "] [" + level + "] " + String.valueOf(message == null ? "" : message);
        Path logPath;
        try {
            // Unit tests exercise the packet state machine without booting
            // Fabric. Resolve the game directory at call time so a malformed
            // optional visual can be logged without crashing class loading.
            logPath = FabricLoader.getInstance().getGameDir()
                    .resolve("logs").resolve("copimineclient.log");
        } catch (RuntimeException notRunningInsideFabric) {
            return;
        }
        synchronized (LOCK) {
            try {
                String trace = stackTrace(error);
                BoundedClientLog.append(logPath, trace.isEmpty() ? line : line + "\\n" + trace);
            } catch (IOException ioError) {
                System.err.println("CopiMineClient log write failed: " + ioError.getMessage());
            }
        }
    }

    private static String stackTrace(Throwable error) {
        if (error == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(error).append(System.lineSeparator());
        for (StackTraceElement element : error.getStackTrace()) {
            builder.append("    at ").append(element).append(System.lineSeparator());
        }
        return builder.toString();
    }
}
