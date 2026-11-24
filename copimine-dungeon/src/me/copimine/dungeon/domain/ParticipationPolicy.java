package me.copimine.dungeon.domain;

import java.util.Objects;

/**
 * Participation policy value (spec OV4.4, config example
 * {@code participation: { policy: JOIN_SAFE, max_players: null }}).
 *
 * <p>{@code maxPlayers == null} means unlimited: there is no artificial count
 * rejection. If a cap is set for operational reasons it is advisory metadata,
 * never a mechanism that drops legitimate eligible players.</p>
 */
public record ParticipationPolicy(ParticipationMode mode, Integer maxPlayers) {

    public ParticipationPolicy {
        Objects.requireNonNull(mode, "mode");
        if (maxPlayers != null && maxPlayers < 1) {
            throw new IllegalArgumentException("max_players должен быть >= 1 или пустым (без ограничения).");
        }
    }

    public static ParticipationPolicy joinSafeUnlimited() {
        return new ParticipationPolicy(ParticipationMode.JOIN_SAFE, null);
    }

    public boolean isUnlimited() {
        return maxPlayers == null;
    }
}
