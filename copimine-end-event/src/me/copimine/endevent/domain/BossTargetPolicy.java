package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Bounded target hysteresis for the boss controller.  Navigation and spell
 * selection may refresh more often than this policy, but a valid target is
 * kept until its lock expires or the player leaves the eligible roster.
 */
public final class BossTargetPolicy {
    public static final long DEFAULT_LOCK_MILLIS = 5_000L;

    private BossTargetPolicy() {
    }

    public static TargetDecision chooseTarget(
            List<UUID> candidates,
            UUID current,
            List<UUID> recent,
            long nowMillis,
            long lockUntilMillis,
            long cursor) {
        return chooseTarget(candidates, current, recent, nowMillis, lockUntilMillis,
                cursor, DEFAULT_LOCK_MILLIS);
    }

    public static TargetDecision chooseTarget(
            List<UUID> candidates,
            UUID current,
            List<UUID> recent,
            long nowMillis,
            long lockUntilMillis,
            long cursor,
            long lockDurationMillis) {
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
        if (lockDurationMillis < 1L) {
            throw new IllegalArgumentException("target lock duration must be positive");
        }
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(
                candidates == null ? List.of() : candidates));
        unique.removeIf(uuid -> uuid == null);
        if (unique.isEmpty()) {
            return new TargetDecision(null, 0L);
        }
        if (current != null && unique.contains(current) && nowMillis < lockUntilMillis) {
            return new TargetDecision(current, lockUntilMillis);
        }

        EndRiftAiPolicy.TargetChoice choice = EndRiftAiPolicy.chooseFairTarget(
                unique, current, recent, safeCursor(cursor));
        UUID selected = choice.target();
        return new TargetDecision(selected, saturatingAdd(nowMillis, lockDurationMillis));
    }

    private static int safeCursor(long cursor) {
        return (int) Math.floorMod(Math.max(0L, cursor), Integer.MAX_VALUE);
    }

    private static long saturatingAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    public record TargetDecision(UUID target, long newLockUntilMillis) {
        public TargetDecision {
            if (newLockUntilMillis < 0L) {
                throw new IllegalArgumentException("target lock deadline must be non-negative");
            }
        }
    }
}
