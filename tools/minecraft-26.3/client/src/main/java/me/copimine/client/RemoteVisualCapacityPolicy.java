package me.copimine.client;

final class RemoteVisualCapacityPolicy {
    static final int MAX_ACTIVE_VISUALS = 4;

    private RemoteVisualCapacityPolicy() {
    }

    static boolean accepts(int activeCount, boolean sequenceAlreadyActive) {
        return sequenceAlreadyActive || activeCount < MAX_ACTIVE_VISUALS;
    }
}
