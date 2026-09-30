package me.copimine.endevent.domain;

/** Controls the official and explicitly disposable Wave 7 trial lifecycles. */
public final class RealitySplitRuntimePolicy {
    private RealitySplitRuntimePolicy() {
    }

    public static boolean allowsRuntime(int activeWave, EventPhase phase,
                                         boolean disposableTestWave) {
        if (activeWave != 7 || phase == null) return false;
        return phase == EventPhase.WAVE_7
                || disposableTestWave && phase == EventPhase.COLLECTING;
    }
}
