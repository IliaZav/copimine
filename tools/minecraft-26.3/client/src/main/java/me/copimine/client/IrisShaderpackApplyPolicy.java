package me.copimine.client;

import java.util.Locale;

final class IrisShaderpackApplyPolicy {
    private IrisShaderpackApplyPolicy() {
    }

    static boolean isBlockedByExistingPack(
            boolean hasPreviousState,
            boolean currentPackEnabled,
            String currentPackName,
            boolean allowOverride
    ) {
        return !hasPreviousState
                && currentPackEnabled
                && !allowOverride
                && !isCopiMineRuntimeName(currentPackName);
    }

    private static boolean isCopiMineRuntimeName(String runtimeName) {
        return runtimeName != null && runtimeName.toLowerCase(Locale.ROOT).startsWith("copimine_");
    }
}
