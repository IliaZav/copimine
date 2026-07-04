package me.copimine.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

public final class IrisShaderpackRuntime {
    private static final int APPLY_POLL_ATTEMPTS = 20;
    private static final long APPLY_POLL_SLEEP_MILLIS = 25L;
    public record ApplyResult(boolean applied, String route, String reason) {
    }

    private static final String STATE_FILE_NAME = "copimineclient-shader-runtime.properties";

    private final ClientConfig config;
    private final ShaderpackRegistry registry;
    private final ShaderpackExporter exporter;
    private final Path stateFile;

    private volatile PreviousState previousState;
    private volatile String activeShaderpack = "";
    private volatile String status = "idle";
    private volatile String lastFailureReason = "";

    public IrisShaderpackRuntime(ClientConfig config, ShaderpackRegistry registry, ShaderpackExporter exporter) {
        this.config = config;
        this.registry = registry;
        this.exporter = exporter;
        this.stateFile = FabricLoader.getInstance().getConfigDir().resolve(STATE_FILE_NAME);
    }

    public static boolean runtimeAvailable() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return false;
        }
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Class<?> irisConfig = Class.forName("net.irisshaders.iris.config.IrisConfig");
            Class<?> irisApiConfig = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
            iris.getMethod("getIrisConfig");
            iris.getMethod("reload");
            iris.getMethod("loadShaderpack");
            iris.getMethod("isValidShaderpack", Path.class);
            irisConfig.getMethod("getShaderPackName");
            irisConfig.getMethod("setShaderPackName", String.class);
            irisApiConfig.getMethod("setShadersEnabledAndApply", boolean.class);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void recoverIfNeeded() {
        if (!Files.isRegularFile(stateFile)) {
            status = runtimeAvailable() ? "idle" : "iris-missing";
            return;
        }
        if (!runtimeAvailable()) {
            deleteStateFile();
            status = "recovery-skipped-iris-missing";
            return;
        }
        try {
            Properties properties = loadState();
            PreviousState previous = new PreviousState(
                    properties.getProperty("previous_pack", ""),
                    Boolean.parseBoolean(properties.getProperty("previous_enabled", "false"))
            );
            restoreState(previous);
            deleteStateFile();
            previousState = null;
            activeShaderpack = "";
            status = "recovered-previous-shaderpack";
            lastFailureReason = "";
        } catch (Exception error) {
            status = "recovery-failed:" + error.getClass().getSimpleName();
            lastFailureReason = "iris-recovery-failed";
        }
    }

    public ApplyResult apply(ShaderpackRegistry.ShaderpackProfile profile) {
        if (!config.allowServerShaderpackRuntime()) {
            lastFailureReason = "client-shaderpack-runtime-disabled";
            status = "disabled-by-config";
            return new ApplyResult(false, "NONE", lastFailureReason);
        }
        if (!runtimeAvailable()) {
            lastFailureReason = "iris-runtime-unavailable";
            status = lastFailureReason;
            return new ApplyResult(false, "NONE", lastFailureReason);
        }
        if (profile == null) {
            lastFailureReason = "missing-shaderpack-profile";
            status = lastFailureReason;
            return new ApplyResult(false, "NONE", lastFailureReason);
        }
        ShaderpackExporter.ExportResult export = exporter.result(profile.zipName());
        if (export == null || !export.validZip()) {
            lastFailureReason = "missing-exported-zip:" + profile.zipName();
            status = lastFailureReason;
            return new ApplyResult(false, "NONE", lastFailureReason);
        }
        if (!export.irisCompatible()) {
            lastFailureReason = "zip-not-iris-compatible:" + profile.zipName();
            status = lastFailureReason;
            return new ApplyResult(false, "NONE", lastFailureReason);
        }
        if (!irisAcceptsRuntimeTarget(export.runtimeTarget())) {
            lastFailureReason = "iris-rejected-runtime-zip:" + export.runtimeName();
            status = lastFailureReason;
            return new ApplyResult(false, "NONE", lastFailureReason);
        }
        try {
            PreviousState current = snapshotCurrentState();
            if (previousState == null) {
                if (current.enabled() && !config.allowOverrideExistingShaderpack() && !isCopiMineRuntimeName(current.packName())) {
                    lastFailureReason = "override-existing-shaderpack-disabled:" + current.packName();
                    status = lastFailureReason;
                    return new ApplyResult(false, "NONE", lastFailureReason);
                }
                previousState = current;
                saveState(previousState, export.runtimeName());
            }
            switchToShaderpack(export.runtimeName(), true);
            activeShaderpack = profile.zipName();
            lastFailureReason = "";
            status = "iris-shaderpack-active:" + profile.zipName();
            return new ApplyResult(true, "IRIS_SHADERPACK", status);
        } catch (Exception error) {
            lastFailureReason = "iris-switch-failed:" + error.getClass().getSimpleName();
            status = lastFailureReason;
            return new ApplyResult(false, "NONE", lastFailureReason);
        }
    }

    public void restore() {
        if (!runtimeAvailable()) {
            deleteStateFile();
            previousState = null;
            activeShaderpack = "";
            status = "restore-skipped-iris-missing";
            return;
        }
        if (previousState == null && !Files.isRegularFile(stateFile)) {
            activeShaderpack = "";
            status = "idle";
            return;
        }
        try {
            PreviousState previous = previousState;
            if (previous == null) {
                Properties properties = loadState();
                previous = new PreviousState(
                        properties.getProperty("previous_pack", ""),
                        Boolean.parseBoolean(properties.getProperty("previous_enabled", "false"))
                );
            }
            restoreState(previous);
            previousState = null;
            activeShaderpack = "";
            deleteStateFile();
            lastFailureReason = "";
            status = "restored-previous-shaderpack";
        } catch (Exception error) {
            lastFailureReason = "iris-restore-failed:" + error.getClass().getSimpleName();
            status = lastFailureReason;
        }
    }

    public boolean active() {
        return !activeShaderpack.isBlank();
    }

    public String activeShaderpack() {
        return activeShaderpack;
    }

    public String statusLine() {
        return "irisAvailable=" + yesNo(runtimeAvailable())
                + ", active=" + (activeShaderpack.isBlank() ? "-" : activeShaderpack)
                + ", previous=" + (previousState == null || previousState.packName().isBlank() ? "-" : previousState.packName())
                + ", status=" + status;
    }

    public String lastFailureReason() {
        return lastFailureReason == null || lastFailureReason.isBlank() ? "iris-runtime-unavailable" : lastFailureReason;
    }

    private PreviousState snapshotCurrentState() throws Exception {
        Object publicConfig = irisApiConfig();
        boolean enabled = invokeBoolean(publicConfig, "areShadersEnabled");
        Object config = irisConfig();
        String packName = invokeOptionalString(config, "getShaderPackName");
        return new PreviousState(packName, enabled);
    }

    private void restoreState(PreviousState previous) throws Exception {
        switchToShaderpack(previous.packName(), previous.enabled());
    }

    private void switchToShaderpack(String runtimeName, boolean enabled) throws Exception {
        Object config = irisConfig();
        invokeIfPresent(config, "load");
        invokeString(config, "setShaderPackName", runtimeName == null ? "" : runtimeName);
        invokeIfPresent(config, "save");
        Object publicConfig = irisApiConfig();
        Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
        Method reload = irisClass.getMethod("reload");
        invokeIfPresentStatic(irisClass, "loadShaderpack");
        reload.invoke(null);
        invokeBooleanArg(publicConfig, "setShadersEnabledAndApply", enabled);
        if (enabled) {
            confirmEnabledShaderpack(irisClass, runtimeName);
        } else {
            confirmDisabledShaderpack(irisClass);
        }
    }

    private void confirmEnabledShaderpack(Class<?> irisClass, String runtimeName) throws Exception {
        for (int attempt = 0; attempt < APPLY_POLL_ATTEMPTS; attempt++) {
            String storedError = storedErrorSummary(irisClass);
            if (!storedError.isBlank()) {
                throw new IllegalStateException("iris-stored-error:" + storedError);
            }
            String currentPackName = invokeStaticString(irisClass, "getCurrentPackName");
            if (runtimeNameMatches(runtimeName, currentPackName) && isShaderPackInUse()) {
                return;
            }
            Thread.sleep(APPLY_POLL_SLEEP_MILLIS);
        }
        String currentPackName = invokeStaticString(irisClass, "getCurrentPackName");
        if (!runtimeNameMatches(runtimeName, currentPackName)) {
            throw new IllegalStateException("iris-pack-not-active:" + currentPackName);
        }
        throw new IllegalStateException("iris-pack-reported-disabled");
    }

    private void confirmDisabledShaderpack(Class<?> irisClass) throws Exception {
        for (int attempt = 0; attempt < APPLY_POLL_ATTEMPTS; attempt++) {
            String storedError = storedErrorSummary(irisClass);
            if (!storedError.isBlank()) {
                throw new IllegalStateException("iris-stored-error:" + storedError);
            }
            if (!isShaderPackInUse()) {
                return;
            }
            Thread.sleep(APPLY_POLL_SLEEP_MILLIS);
        }
        throw new IllegalStateException("iris-pack-remained-enabled:" + invokeStaticString(irisClass, "getCurrentPackName"));
    }

    private boolean irisAcceptsRuntimeTarget(Path runtimeTarget) {
        if (runtimeTarget == null || !Files.isRegularFile(runtimeTarget)) {
            return false;
        }
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Object result = irisClass.getMethod("isValidShaderpack", Path.class).invoke(null, runtimeTarget);
            return result instanceof Boolean valid && valid;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private Object irisConfig() throws Exception {
        Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
        return irisClass.getMethod("getIrisConfig").invoke(null);
    }

    private Object irisApiConfig() throws Exception {
        Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        Object irisApi = irisApiClass.getMethod("getInstance").invoke(null);
        return irisApiClass.getMethod("getConfig").invoke(irisApi);
    }

    private void saveState(PreviousState previous, String requestedRuntimeName) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("previous_pack", previous.packName());
        properties.setProperty("previous_enabled", Boolean.toString(previous.enabled()));
        properties.setProperty("requested_runtime", requestedRuntimeName == null ? "" : requestedRuntimeName);
        Files.createDirectories(stateFile.getParent());
        try (OutputStream output = Files.newOutputStream(stateFile)) {
            properties.store(output, "CopiMineClient shader runtime state");
        }
    }

    private Properties loadState() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(stateFile)) {
            properties.load(input);
        }
        return properties;
    }

    private void deleteStateFile() {
        try {
            Files.deleteIfExists(stateFile);
        } catch (Exception ignored) {
        }
    }

    private boolean invokeBoolean(Object target, String method) throws Exception {
        Object value = target.getClass().getMethod(method).invoke(target);
        return value instanceof Boolean enabled && enabled;
    }

    private String invokeOptionalString(Object target, String method) throws Exception {
        Object value = target.getClass().getMethod(method).invoke(target);
        if (value instanceof Optional<?> optional) {
            Object unwrapped = optional.isPresent() ? optional.get() : "";
            return unwrapped == null ? "" : unwrapped.toString();
        }
        return value == null ? "" : value.toString();
    }

    private void invokeString(Object target, String method, String value) throws Exception {
        target.getClass().getMethod(method, String.class).invoke(target, value == null ? "" : value);
    }

    private void invokeBooleanArg(Object target, String method, boolean value) throws Exception {
        target.getClass().getMethod(method, boolean.class).invoke(target, value);
    }

    private void invokeIfPresent(Object target, String method) throws Exception {
        try {
            target.getClass().getMethod(method).invoke(target);
        } catch (NoSuchMethodException ignored) {
        }
    }

    private void invokeIfPresentStatic(Class<?> type, String method) throws Exception {
        try {
            type.getMethod(method).invoke(null);
        } catch (NoSuchMethodException ignored) {
        }
    }

    private String invokeStaticString(Class<?> type, String method) throws Exception {
        Object value = type.getMethod(method).invoke(null);
        return value == null ? "" : value.toString();
    }

    private boolean isShaderPackInUse() throws Exception {
        Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        Object irisApi = irisApiClass.getMethod("getInstance").invoke(null);
        Object value = irisApiClass.getMethod("isShaderPackInUse").invoke(irisApi);
        return value instanceof Boolean enabled && enabled;
    }

    private String storedErrorSummary(Class<?> irisClass) throws Exception {
        Object value = irisClass.getMethod("getStoredError").invoke(null);
        if (!(value instanceof Optional<?> optional) || optional.isEmpty() || optional.get() == null) {
            return "";
        }
        Object error = optional.get();
        return error.getClass().getSimpleName();
    }

    private boolean isCopiMineRuntimeName(String runtimeName) {
        return runtimeName != null && runtimeName.toLowerCase(Locale.ROOT).startsWith("copimine_");
    }

    private boolean runtimeNameMatches(String requested, String actual) {
        String left = normalizeRuntimeName(requested);
        String right = normalizeRuntimeName(actual);
        return !left.isBlank() && left.equals(right);
    }

    private String normalizeRuntimeName(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private record PreviousState(String packName, boolean enabled) {
        private PreviousState {
            packName = packName == null ? "" : packName.trim();
        }
    }
}
