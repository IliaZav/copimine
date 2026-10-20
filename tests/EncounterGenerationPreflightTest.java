import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import javax.tools.ToolProvider;

/** Executes the production preflight guards with cleanup behind a counted boundary. */
public final class EncounterGenerationPreflightTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath();
        String source = Files.readString(root.resolve(
                "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"));
        String[][] transitions = {
                {"recoverTransientSession", "void", "cancelSessionTasks("},
                {"configureCore", "void", "advanceWaveTwoCleanupFence("},
                {"completeStartRitual", "void", "cancelSessionTasks("},
                {"spawnInspectionWave", "boolean", "closeEncounterResourceScope("},
                {"spawnTestWave", "boolean", "cancelSessionTasks("}
        };
        StringBuilder wrappers = new StringBuilder();
        for (String[] transition : transitions) {
            String body = method(source, transition[0]);
            int start = body.indexOf("Long nextGeneration =");
            int cleanup = body.indexOf(transition[2]);
            check(start >= 0 && cleanup > start,
                    transition[0] + " must preflight generation before cleanup");
            int guard = body.indexOf("if (", start);
            int lineEnd = body.indexOf('\n', guard);
            int guardEnd = body.substring(guard, lineEnd).contains("{")
                    ? closingBrace(body, body.indexOf('{', guard)) + 1 : body.indexOf(';', guard) + 1;
            check(guardEnd <= cleanup, transition[0] + " refusal must precede cleanup");
            int assignment = body.indexOf("generation =", guardEnd);
            check(assignment > guardEnd, transition[0] + " must publish a generation after preflight");
            String publish = body.substring(assignment, body.indexOf(';', assignment) + 1);
            wrappers.append("public ").append(transition[1]).append(' ').append(transition[0])
                    .append("() { boolean creativeRun = false; Object feedback = null;\n")
                    .append(body, start, guardEnd)
                    .append("\ncleanupCalls++; ").append(publish)
                    .append(transition[1].equals("boolean") ? "return true;" : "")
                    .append("}\n");
        }
        String helper = method(source, "preflightNextGeneration");
        String probe = "import me.copimine.protocol.EncounterGenerationPolicy;"
                + "import java.util.logging.*; public class GenerationProbe {"
                + "public long generation; public int cleanupCalls; String eventId = \"test\";"
                + "Logger getLogger(){Logger log=Logger.getAnonymousLogger(); log.setUseParentHandlers(false); return log;}"
                + "void message(Object target,String message){} void enterGenerationAdvanceRecovery(String operation){}"
                + helper + wrappers + "}";
        Path output = Files.createTempDirectory("copimine-generation-preflight-");
        try {
            Path javaFile = output.resolve("GenerationProbe.java");
            Files.writeString(javaFile, probe);
            int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-encoding", "UTF-8", "-cp", System.getProperty("java.class.path"),
                    "-d", output.toString(), javaFile.toString());
            check(compiled == 0, "the production preflight guards must compile in the boundary probe");
            try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()},
                    EncounterGenerationPreflightTest.class.getClassLoader())) {
                Class<?> type = loader.loadClass("GenerationProbe");
                for (String[] transition : transitions) {
                    for (long exhausted : new long[]{Long.MIN_VALUE, -1L, Long.MAX_VALUE - 1L, Long.MAX_VALUE}) {
                        Object instance = type.getConstructor().newInstance();
                        type.getField("generation").setLong(instance, exhausted);
                        type.getMethod(transition[0]).invoke(instance);
                        check(type.getField("cleanupCalls").getInt(instance) == 0
                                        && type.getField("generation").getLong(instance) == exhausted,
                                transition[0] + " must refuse before cleanup and retain generation " + exhausted);
                    }
                    long[] current = {0L, 1L, Long.MAX_VALUE - 2L};
                    long[] next = {1L, 2L, Long.MAX_VALUE - 1L};
                    for (int index = 0; index < current.length; index++) {
                        Object instance = type.getConstructor().newInstance();
                        type.getField("generation").setLong(instance, current[index]);
                        type.getMethod(transition[0]).invoke(instance);
                        check(type.getField("cleanupCalls").getInt(instance) == 1
                                        && type.getField("generation").getLong(instance) == next[index],
                                transition[0] + " accepts safe advance from " + current[index]);
                        if (index == current.length - 1) {
                            type.getMethod(transition[0]).invoke(instance);
                            check(type.getField("cleanupCalls").getInt(instance) == 1
                                            && type.getField("generation").getLong(instance) == Long.MAX_VALUE - 1L,
                                    transition[0] + " cannot reuse or wrap the final generation");
                        }
                    }
                }
            }
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("EncounterGenerationPreflightTest OK (five transitions)");
    }

    private static String method(String source, String name) {
        var match = java.util.regex.Pattern.compile("private (?:void|boolean|Long) " + name + "\\(")
                .matcher(source);
        check(match.find(), "missing generation boundary: " + name);
        int open = source.indexOf('{', match.end());
        return source.substring(match.start(), closingBrace(source, open) + 1);
    }

    private static int closingBrace(String source, int open) {
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return index;
        }
        throw new AssertionError("unterminated method");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
