package me.copimine.endevent.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded same-tick gate for the physical Core interaction.
 *
 * <p>Paper can deliver more than one hand/interact callback for one client
 * click.  This gate is deliberately per player/event/generation so two real
 * players may deposit in the same server tick while one click cannot consume
 * two stacks.</p>
 */
public final class CoreInteractionGuard {
    private long tick = Long.MIN_VALUE;
    private final Set<Key> accepted = new HashSet<>();

    public boolean accept(long serverTick, String eventId, long generation, UUID playerUuid) {
        if (eventId == null || eventId.isBlank() || playerUuid == null) {
            return false;
        }
        if (serverTick != tick) {
            tick = serverTick;
            accepted.clear();
        }
        return accepted.add(new Key(eventId, generation, playerUuid));
    }

    public void clear() {
        tick = Long.MIN_VALUE;
        accepted.clear();
    }

    private record Key(String eventId, long generation, UUID playerUuid) {
    }
}
