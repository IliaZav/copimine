package me.copimine.client;

/** Input routing rules for the client-only prisoner control lock. */
public final class PrisonerKeyboardPolicy {
    private static final int RELEASE_ACTION = 0;

    private PrisonerKeyboardPolicy() {
    }

    public static boolean shouldCaptureKeyEvent(boolean prisonerModeActive,
                                                boolean screenOpen,
                                                boolean exemptKey,
                                                int action) {
        return prisonerModeActive && !screenOpen && !exemptKey && action != RELEASE_ACTION;
    }
}
