package me.copimine.endevent.bridge;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Local boundary so the event plugin can keep running when its soft dependency is absent. */
public interface EndEventBridgeV4Access extends AutoCloseable {
    default boolean send(UUID recipient, String type, String instanceId, long durationMillis,
            String subjectOrMode, String visualId, int health, int maxHealth,
            float progress, String clearPolicy, String status) {
        return send(recipient, type, instanceId, durationMillis, subjectOrMode, visualId,
                health, maxHealth, progress, clearPolicy, status, "");
    }

    boolean send(UUID recipient, String type, String instanceId, long durationMillis,
            String subjectOrMode, String visualId, int health, int maxHealth,
            float progress, String clearPolicy, String status, String source);

    /** Sends the server-authored Wave 2 phantom identifier in the reserved reason field. */
    default boolean send(UUID recipient, String type, String instanceId, long durationMillis,
            String subjectOrMode, String visualId, int health, int maxHealth,
            float progress, String clearPolicy, String status, String source, String reason) {
        if (reason != null && !reason.isEmpty()) return false;
        return send(recipient, type, instanceId, durationMillis, subjectOrMode, visualId,
                health, maxHealth, progress, clearPolicy, status, source);
    }

    boolean bindContext(UUID recipient, EventContext context);

    boolean onRequest(Player player, byte[] requestBytes);

    void register(Plugin plugin);

    void registerPlayer(Player player);

    boolean isReady(UUID playerUuid);

    /** Safe diagnostic for why the authenticated V4 mutation gate is currently closed. */
    default ReadinessReason readinessReason(UUID playerUuid) {
        return isReady(playerUuid) ? ReadinessReason.READY : ReadinessReason.READINESS_UNAVAILABLE;
    }

    enum ReadinessReason {
        READY,
        SIGNER_UNAVAILABLE,
        SENDER_CLOSED,
        PLAYER_OFFLINE,
        NO_VERIFIED_SESSION,
        STREAM_NOT_READY,
        STALE_READINESS,
        READINESS_UNAVAILABLE
    }

    boolean isCurrentVerifiedSession(UUID playerUuid, String sessionUuid);

    void unbindContext(UUID playerUuid);

    void requestFreshChallenges(Collection<UUID> onlinePlayers);

    /** Advances the optional sender's main-thread maintenance and heartbeat work. */
    default void tick() {
    }

    @Override
    void close();

    record EventContext(UUID eventUuid, long generation, String dimension,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    record PrisonerAbilityRequest(UUID eventUuid, long generation, String abilityId,
            Optional<UUID> targetUuid) {
    }
}
