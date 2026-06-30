package me.copimine.client;

public final class ShaderRuntimeManager {
    public enum Route {
        NONE,
        IRIS_SHADERPACK,
        FALLBACK_POST_PROCESS
    }

    public record RuntimeResult(boolean applied, Route route, String reason, String shaderpack) {
    }

    private final ShaderpackRegistry registry;
    private final ShaderpackExporter exporter;
    private final IrisShaderpackRuntime irisRuntime;
    private final FallbackPostProcessRuntime fallbackRuntime;

    private volatile Route activeRoute = Route.NONE;
    private volatile String activeShaderpack = "";
    private volatile String status = "idle";
    private volatile String lastFailureReason = "";

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
        exporter.initialize();
        irisRuntime.recoverIfNeeded();
        status = "shaderpack-first initialized, exports=" + exporter.statusLine();
    }

    public RuntimeResult apply(ShaderEffectRequest request) {
        ShaderpackRegistry.ShaderpackProfile profile = registry.resolveForEffect(request.effectId(), request.shaderpack());
        if (profile != null) {
            IrisShaderpackRuntime.ApplyResult iris = irisRuntime.apply(profile);
            if (iris.applied()) {
                fallbackRuntime.clear();
                activeRoute = Route.IRIS_SHADERPACK;
                activeShaderpack = profile.zipName();
                status = "shaderpack-first:" + iris.reason();
                lastFailureReason = "";
                return new RuntimeResult(true, activeRoute, status, activeShaderpack);
            }
            lastFailureReason = iris.reason();
            status = "fallback-after-" + iris.reason();
        } else {
            lastFailureReason = "missing-shaderpack-profile";
            status = lastFailureReason;
        }
        if (fallbackRuntime.apply(request)) {
            activeRoute = Route.FALLBACK_POST_PROCESS;
            activeShaderpack = profile == null ? "" : profile.zipName();
            status = "fallback-post-process:" + request.effectId();
            return new RuntimeResult(true, activeRoute, status, activeShaderpack);
        }
        activeRoute = Route.NONE;
        activeShaderpack = "";
        status = "runtime-unavailable:" + request.effectId();
        if (lastFailureReason.isBlank()) {
            lastFailureReason = fallbackRuntime.lastFailureReason();
        }
        return new RuntimeResult(false, activeRoute, status, activeShaderpack);
    }

    public void clear(String reason) {
        irisRuntime.restore();
        fallbackRuntime.clear();
        activeRoute = Route.NONE;
        activeShaderpack = "";
        status = "idle" + (reason == null || reason.isBlank() ? "" : ":" + reason);
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

    public String statusLine() {
        return "route=" + activeRoute.name()
                + ", shaderpack=" + (activeShaderpack.isBlank() ? "-" : activeShaderpack)
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
}
