package me.copimine.endevent.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Keeps a player UUID from one network connection from suppressing the visual
 * binding required by that player's next connection.
 */
public final class ClientBindingReconnectPolicy {
    private static final long POST_JOIN_RETRY_DELAY_TICKS = 40L;

    private ClientBindingReconnectPolicy() {
    }

    public static boolean prepareForPlayerJoin(Set<UUID> readyPlayers, UUID player,
                                               boolean combatPhase, boolean musicPhase,
                                               boolean localTestBoss, boolean bossPresent) {
        if (readyPlayers != null && player != null) {
            readyPlayers.remove(player);
        }
        return combatPhase || musicPhase || localTestBoss || bossPresent;
    }

    /**
     * PlayerJoinEvent can happen before a Fabric client has registered its
     * custom payload receiver.  Retry once after that receiver is available.
     */
    public static long postJoinRetryDelayTicks(boolean bindingRequired) {
        return bindingRequired ? POST_JOIN_RETRY_DELAY_TICKS : 0L;
    }
}
