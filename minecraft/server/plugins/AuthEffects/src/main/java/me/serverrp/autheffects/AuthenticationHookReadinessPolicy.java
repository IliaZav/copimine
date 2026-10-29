package me.serverrp.autheffects;

/** Startup gate for AuthEffects' authentication state reconciliation. */
public final class AuthenticationHookReadinessPolicy {
    private AuthenticationHookReadinessPolicy() {
    }

    /**
     * AuthEffects needs logout events to clear state. Login state may come from either
     * the login event or AuthMeApi, so startup fails closed when both are unavailable.
     */
    public static boolean canEnable(boolean loginHookRegistered,
                                    boolean logoutHookRegistered,
                                    boolean authApiAvailable) {
        return logoutHookRegistered && (loginHookRegistered || authApiAvailable);
    }
}
