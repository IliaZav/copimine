package me.copimine.client;

public final class ShaderRuntimeCleanupPolicy {
    private ShaderRuntimeCleanupPolicy() {
    }

    public static boolean shouldClear(boolean hasAppliedRuntime, boolean runtimeApplyAttempted) {
        return hasAppliedRuntime || runtimeApplyAttempted;
    }
}
