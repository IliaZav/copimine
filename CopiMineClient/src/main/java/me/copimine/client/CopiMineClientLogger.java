package me.copimine.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class CopiMineClientLogger {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path LOG_PATH = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("copimineclient.log");
    private static final Object LOCK = new Object();

    private CopiMineClientLogger() {
    }

    public static void info(String message) {
        write("INFO", message, null);
    }

    public static void warn(String message) {
        write("WARN", message, null);
    }

    public static void warn(String message, Throwable error) {
        write("WARN", message, error);
    }

    public static void error(String message, Throwable error) {
        write("ERROR", message, error);
    }

    private static void write(String level, String message, Throwable error) {
        String line = "[" + TS.format(LocalDateTime.now()) + "] [" + level + "] " + String.valueOf(message == null ? "" : message);
        String trace = stackTrace(error);
        synchronized (LOCK) {
            try {
                Files.createDirectories(LOG_PATH.getParent());
                Files.writeString(
                        LOG_PATH,
                        trace.isEmpty()
                                ? line + System.lineSeparator()
                                : line + System.lineSeparator() + trace,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND
                );
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
