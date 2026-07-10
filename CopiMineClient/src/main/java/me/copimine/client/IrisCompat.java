package me.copimine.client;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

final class IrisCompat {
    private static boolean detectionFailureLogged;

    private IrisCompat() {
    }

    static boolean irisLoaded() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    static boolean shaderPackActive() {
        if (!irisLoaded()) {
            return false;
        }
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method getInstance = irisApiClass.getMethod("getInstance");
            Object irisApi = getInstance.invoke(null);
            Method isShaderPackInUse = irisApiClass.getMethod("isShaderPackInUse");
            Object result = isShaderPackInUse.invoke(irisApi);
            return result instanceof Boolean value && value;
        } catch (Throwable error) {
            if (!detectionFailureLogged) {
                detectionFailureLogged = true;
                CopiMineClientLogger.warn("IrisCompat could not query shader-pack state", error);
            }
            return false;
        }
    }

    static String statusLine() {
        if (!irisLoaded()) {
            return "iris=absent";
        }
        return shaderPackActive() ? "iris=loaded, shaderpack=active" : "iris=loaded, shaderpack=inactive";
    }
}
