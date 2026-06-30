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
            iris.getMethod("getIrisConfig");
            iris.getMethod("reload");
            irisConfig.getMethod("getShaderPackName");
            irisConfig.getMethod("setShaderPackName", String.class);
            irisConfig.getMethod("setShadersEnabled", boolean.class);
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
        try {
            PreviousState current = snapshotCurrentState();
            if (previousState == null) {
                if (current.enabled() && !config.allowOverrideExistingShaderpack() && !isCopiMineRuntimeName(current.packName())) {
                    lastFailureReason = "override-existing-shaderpack-disabled:" + current.packName();
                    status = lastFailureReason;
                    return new ApplyResult(false, "NONE", lastFailureReason);
                }
                previousState = current;
                saveState(previousState, registry.shaderpackRuntimeName(profile.zipName()));
            }
            switchToShaderpack(registry.shaderpackRuntimeName(profile.zipName()), true);
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
        invokeBooleanArg(config, "setShadersEnabled", enabled);
        invokeIfPresent(config, "save");
        Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
        Method reload = irisClass.getMethod("reload");
        reload.invoke(null);
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

    private boolean isCopiMineRuntimeName(String runtimeName) {
        return runtimeName != null && runtimeName.toLowerCase(Locale.ROOT).startsWith("copimine/");
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
