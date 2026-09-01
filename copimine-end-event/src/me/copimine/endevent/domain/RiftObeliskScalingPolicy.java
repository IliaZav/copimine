package me.copimine.endevent.domain;

/** Deterministic, bounded obelisk count for valid living boss participants. */
public final class RiftObeliskScalingPolicy {
    public static final int MAX_ACTIVE = 4;

    private RiftObeliskScalingPolicy() {
    }

    public static int countForPlayers(int livingParticipants) {
        if (livingParticipants < 2) {
            return 0;
        }
        if (livingParticipants <= 5) {
            return 1;
        }
        if (livingParticipants <= 10) {
            return 2;
        }
        if (livingParticipants <= 15) {
            return 3;
        }
        return MAX_ACTIVE;
    }
}
