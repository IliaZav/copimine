package me.copimine.endevent.domain;

/** Recovery rules for the durable victory saga. */
public final class VictorySagaRecoveryPolicy {
    private VictorySagaRecoveryPolicy() {
    }

    /**
     * A persisted REQUESTED status means the issuing process died before the
     * callback ran; no request is alive after a restart, so it must be retried.
     */
    public static String restoredReturnStoneStatus(String persisted) {
        return "REQUESTED".equals(persisted) ? "PENDING_RETRY" : persisted;
    }

    /** A reward callback is stale once the event or its attempt generation changed. */
    public static boolean callbackIsCurrent(String capturedEventId, long capturedGeneration,
                                            String currentEventId, long currentGeneration) {
        return capturedEventId != null && capturedEventId.equals(currentEventId)
                && capturedGeneration == currentGeneration;
    }
}
