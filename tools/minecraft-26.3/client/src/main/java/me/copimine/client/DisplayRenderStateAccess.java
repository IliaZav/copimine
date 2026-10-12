package me.copimine.client;

/** Carries only the visibility decision needed by the deferred display renderer. */
public interface DisplayRenderStateAccess {
    boolean copimine$hideVanillaCarrier();
    void copimine$setHideVanillaCarrier(boolean hide);
}
