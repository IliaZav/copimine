package me.copimine.client;

final class RemoteVisualSequencePolicy {
    private RemoteVisualSequencePolicy() {
    }

    static boolean isValidServerSequence(long sequence) {
        return sequence > 0L;
    }
}
