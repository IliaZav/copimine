package me.copimine.client;

/** Pure fallback rules for the server ItemDisplay tentacle carrier. */
public final class EndRiftTentacleCarrierPolicy {
    private EndRiftTentacleCarrierPolicy() {
    }

    /**
     * Vanilla ItemDisplay rendering may be suppressed only after the custom
     * renderer has a bridge binding.  CMD alone is still a valid candidate
     * for the custom world pass, but it is not proof that the vanilla carrier
     * can be hidden safely.
     */
    public static boolean suppressVanillaCarrier(boolean bridgeBound,
                                                  boolean customRendererReady) {
        return bridgeBound && customRendererReady;
    }
}
