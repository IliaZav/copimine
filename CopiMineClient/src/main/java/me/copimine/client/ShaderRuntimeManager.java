package me.copimine.client;

public final class ShaderRuntimeManager {
    public enum RuntimeError {
        NONE,
        IRIS_NOT_FOUND,
        CANVAS_NOT_FOUND,
        OPTIFINE_NOT_FOUND,
        ZIP_CORRUPTED,
        ZIP_NOT_SUPPORTED,
        PIPELINE_FAILED,
        SWITCH_FAILED,
        RESTORE_FAILED,
        INVALID_SHADER,
        RUNTIME_UNAVAILABLE
    }

    public enum State {
        IDLE,
        PREPARE,
        VALIDATE,
        LOAD_SHADER,
        WAIT_PIPELINE,
        ACTIVE,
        RESTORE,
        FAILED
    }

    public enum Route {
        NONE,
        IRIS_SHADERPACK,
        CANVAS_UNAVAILABLE,
        OPTIFINE_UNAVAILABLE,
        CUSTOM_UNAVAILABLE,
        FALLBACK_POST_PROCESS
    }

    public record RuntimeResult(boolean applied, Route route, RuntimeError error, String reason, String shaderpack) {
    }

    private final ShaderpackRegistry registry;
    private final ShaderpackExporter exporter;
    private final IrisShaderpackRuntime irisRuntime;
    private final FallbackPostProcessRuntime fallbackRuntime;

    private volatile Route activeRoute = Route.NONE;
    private volatile State state = State.IDLE;
    private volatile String activeShaderpack = "";
    private volatile String status = "idle";
    private volatile String lastFailureReason = "";
    private volatile RuntimeError lastError = RuntimeError.NONE;

    public ShaderRuntimeManager(
            ShaderpackRegistry registry,
            ShaderpackExporter exporter,
            IrisShaderpackRuntime irisRuntime,
            FallbackPostProcessRuntime fallbackRuntime
    ) {
        this.registry = registry;
        this.exporter = exporter;
        this.irisRuntime = irisRuntime;
        this.fallbackRuntime = fallbackRuntime;
    }

    public void initialize() {
        state = State.PREPARE;
        exporter.initialize();
        irisRuntime.recoverIfNeeded();
        state = State.IDLE;
        status = "shaderpack-first initialized, exports=" + exporter.statusLine();
        lastError = RuntimeError.NONE;
        CopiMineClientLogger.info("ShaderRuntimeManager initialized: " + status);
    }

    public RuntimeResult apply(ShaderEffectRequest request) {
        state = State.PREPARE;
        CopiMineClientLogger.info("Shader apply request: effect=" + request.effectId() + ", shaderpack=" + request.shaderpack() + ", durationMs=" + request.durationMillis());
        ShaderpackRegistry.ShaderpackProfile profile = registry.resolveForEffect(request.effectId(), request.shaderpack());
        if (profile != null) {
            state = State.VALIDATE;
            if (!profile.irisCompatible()) {
                lastError = RuntimeError.ZIP_NOT_SUPPORTED;
                lastFailureReason = "profile-not-iris-runtime:" + profile.id();
                status = lastFailureReason;
                CopiMineClientLogger.warn("Shader profile is not Iris runtime capable: " + profile.id() + " / " + profile.note());
            } else {
            IrisShaderpackRuntime.ApplyResult iris = irisRuntime.apply(profile);
            if (iris.applied()) {
                state = State.ACTIVE;
                fallbackRuntime.clear();
                activeRoute = Route.IRIS_SHADERPACK;
                activeShaderpack = profile.zipName();
                status = "shaderpack-first:" + iris.reason();
                lastFailureReason = "";
                lastError = RuntimeError.NONE;
                CopiMineClientLogger.info("Shader activated through Iris: " + profile.id() + " -> " + status);
                return new RuntimeResult(true, activeRoute, RuntimeError.NONE, status, activeShaderpack);
            }
            ShaderpackExporter.ExportResult export = exporter.result(profile.zipName());
            lastFailureReason = export == null
                    ? iris.reason()
                    : iris.reason() + ":" + export.status();
            status = "fallback-after-" + iris.reason();
            lastError = mapIrisError(iris.reason(), export);
            CopiMineClientLogger.warn("Iris route failed for " + profile.id() + ": " + lastFailureReason);
            }
        } else {
            lastFailureReason = "missing-shaderpack-profile";
            status = lastFailureReason;
            lastError = RuntimeError.INVALID_SHADER;
            CopiMineClientLogger.warn("Shader profile missing for request: " + request.effectId() + " / " + request.shaderpack());
        }
        state = State.LOAD_SHADER;
        if (fallbackRuntime.apply(request)) {
            state = State.ACTIVE;
            activeRoute = Route.FALLBACK_POST_PROCESS;
            activeShaderpack = profile == null ? "" : profile.zipName();
            status = "fallback-post-process:" + request.effectId();
            CopiMineClientLogger.info("Fallback post-process activated: effect=" + request.effectId() + ", shaderpack=" + activeShaderpack);
            return new RuntimeResult(true, activeRoute, lastError == RuntimeError.NONE ? RuntimeError.RUNTIME_UNAVAILABLE : lastError, status, activeShaderpack);
        }
        activeRoute = Route.NONE;
        state = State.FAILED;
        activeShaderpack = "";
        status = "runtime-unavailable:" + request.effectId();
        if (lastFailureReason.isBlank()) {
            lastFailureReason = fallbackRuntime.lastFailureReason();
        }
        if (lastError == RuntimeError.NONE) {
            lastError = RuntimeError.RUNTIME_UNAVAILABLE;
        }
        CopiMineClientLogger.warn("Shader apply failed completely: " + status + " / " + lastFailureReason);
        return new RuntimeResult(false, activeRoute, lastError, status, activeShaderpack);
    }

    public void clear(String reason) {
        state = State.RESTORE;
        irisRuntime.restore();
        fallbackRuntime.clear();
        activeRoute = Route.NONE;
        activeShaderpack = "";
        state = State.IDLE;
        status = "idle" + (reason == null || reason.isBlank() ? "" : ":" + reason);
        lastError = RuntimeError.NONE;
        CopiMineClientLogger.info("Shader runtime restored: " + status);
    }

    public boolean shaderpackRuntimeAvailable() {
        return IrisShaderpackRuntime.runtimeAvailable()
                && registry.profiles().stream().anyMatch(profile -> {
                    ShaderpackExporter.ExportResult result = exporter.result(profile.zipName());
                    return profile.irisCompatible() && result != null && result.validZip() && result.irisCompatible();
                });
    }

    public Route activeRoute() {
        return activeRoute;
    }

    public String activeShaderpack() {
        return activeShaderpack;
    }

    public String activeRouteName() {
        return activeRoute.name();
    }

    public String statusLine() {
        return "state=" + state.name()
                + ", route=" + activeRoute.name()
                + ", shaderpack=" + (activeShaderpack.isBlank() ? "-" : activeShaderpack)
                + ", detected=" + detectedRuntimeLine()
                + ", lastError=" + lastError.name()
                + ", exporter=" + exporter.statusLine()
                + ", iris=" + irisRuntime.statusLine()
                + ", fallback=" + fallbackRuntime.statusLine()
                + ", status=" + status;
    }

    public String lastFailureReason() {
        return lastFailureReason == null || lastFailureReason.isBlank() ? fallbackRuntime.lastFailureReason() : lastFailureReason;
    }

    public ShaderpackRegistry registry() {
        return registry;
    }

    public java.util.List<String> exporterSummaryLines() {
        return exporter.summaryLines();
    }

    public java.util.List<String> diagnosticLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add("Client version: " + CopiMineClient.CLIENT_VERSION);
        lines.add("Fabric: " + yesNo(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("fabricloader")));
        lines.add("Fabric API: " + yesNo(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("fabric-api")));
        lines.add("Sodium: " + yesNo(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")));
        lines.add("Iris: " + yesNo(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")));
        lines.add("Canvas: " + yesNo(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("canvas")));
        lines.add("OptiFine: " + yesNo(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("optifine")));
        lines.add("Detected shader runtime: " + detectedRuntimeLine());
        lines.add("Current runtime: " + activeRoute.name());
        lines.add("Current shader: " + (activeShaderpack.isBlank() ? "-" : activeShaderpack));
        lines.add("Restore state: " + status);
        lines.add("Last error: " + lastError.name() + " / " + lastFailureReason());
        lines.add("Validation:");
        for (ShaderpackRegistry.ShaderDefinition definition : registry.definitions(exporter.results())) {
            lines.add(" - " + definition.id()
                    + " [" + definition.runtime().name() + "] "
                    + definition.status()
                    + " fallback=" + definition.fallback()
                    + " validation=" + definition.validationResult());
        }
        return lines;
    }

    public java.util.List<String> runtimeLines() {
        return java.util.List.of(
                "Iris: " + (IrisShaderpackRuntime.runtimeAvailable() ? "available" : "unavailable"),
                "Canvas: " + (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("canvas") ? "detected-no-runtime-switching-api" : "not-installed"),
                "OptiFine: " + (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("optifine") ? "detected-no-supported-fabric-runtime-switching-api" : "not-installed"),
                "Custom: unavailable",
                "PostProcess fallback: available"
        );
    }

    public RuntimeError lastError() {
        return lastError;
    }

    private RuntimeError mapIrisError(String reason, ShaderpackExporter.ExportResult export) {
        String normalized = reason == null ? "" : reason.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("iris-runtime-unavailable")) {
            return RuntimeError.IRIS_NOT_FOUND;
        }
        if (normalized.contains("missing-exported-zip") || (export != null && !export.validZip())) {
            return RuntimeError.ZIP_CORRUPTED;
        }
        if (normalized.contains("not-iris-compatible") || normalized.contains("zip-not-iris-compatible") || (export != null && !export.irisCompatible())) {
            return RuntimeError.ZIP_NOT_SUPPORTED;
        }
        if (normalized.contains("rejected") || normalized.contains("invalid")) {
            return RuntimeError.INVALID_SHADER;
        }
        if (normalized.contains("switch") || normalized.contains("pack-not-active") || normalized.contains("pipeline")) {
            return RuntimeError.SWITCH_FAILED;
        }
        return RuntimeError.RUNTIME_UNAVAILABLE;
    }

    private String detectedRuntimeLine() {
        if (IrisShaderpackRuntime.runtimeAvailable()) {
            return "IRIS_SHADERPACK";
        }
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("canvas")) {
            return "CANVAS_DETECTED_NO_SWITCHING_API";
        }
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("optifine")) {
            return "OPTIFINE_DETECTED_NO_FABRIC_SWITCHING_API";
        }
        return "FALLBACK_POST_PROCESS";
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
