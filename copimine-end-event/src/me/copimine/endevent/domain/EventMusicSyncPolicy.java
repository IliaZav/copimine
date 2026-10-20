package me.copimine.endevent.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks which players have received the current native event music track. */
public final class EventMusicSyncPolicy {
    private final Map<UUID, String> synchronizedTrackByPlayer = new HashMap<>();

    /** Returns true once per player and track, only when that track is currently active. */
    public boolean shouldSync(UUID playerUuid, String requestedTrack, String activeTrack) {
        if (playerUuid == null || requestedTrack == null || requestedTrack.isBlank()
                || !requestedTrack.equals(activeTrack)) {
            return false;
        }
        String previousTrack = synchronizedTrackByPlayer.put(playerUuid, requestedTrack);
        return !requestedTrack.equals(previousTrack);
    }

    /** Records that a player received the track as part of a global track start. */
    public void markSynchronized(UUID playerUuid, String trackId) {
        if (playerUuid != null && trackId != null && !trackId.isBlank()) {
            synchronizedTrackByPlayer.put(playerUuid, trackId);
        }
    }

    public void clearPlayer(UUID playerUuid) {
        if (playerUuid != null) {
            synchronizedTrackByPlayer.remove(playerUuid);
        }
    }

    public void clear() {
        synchronizedTrackByPlayer.clear();
    }
}
