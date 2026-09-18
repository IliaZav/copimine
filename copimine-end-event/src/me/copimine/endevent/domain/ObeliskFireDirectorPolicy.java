package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded, deterministic fire sequencing for the physical Wave 4 towers. */
public final class ObeliskFireDirectorPolicy {
    public static final int DEFAULT_FIRE_INTERVAL_TICKS = 80;
    public static final int MIN_TELEGRAPH_TICKS = 20;
    public static final int MIN_STAGGER_TICKS = 6;
    public static final int MAX_STAGGER_TICKS = 10;

    private ObeliskFireDirectorPolicy() {
    }

    public static int maxSimultaneousSequences(int livingPlayers) {
        return ObeliskScalingPolicy.profileForPlayers(livingPlayers).fireballCap();
    }

    public static int staggerTicks(int fireIntervalTicks, int totalObelisks) {
        int interval = Math.max(MIN_TELEGRAPH_TICKS, fireIntervalTicks);
        int total = Math.max(1, Math.min(ObeliskScalingPolicy.MAX_OBELISKS, totalObelisks));
        return Math.max(MIN_STAGGER_TICKS,
                Math.min(MAX_STAGGER_TICKS, interval / total));
    }

    public static int fireInterval(int baseIntervalTicks, boolean channelerAccelerated,
                                   int minimumTelegraphTicks) {
        int minimum = Math.max(MIN_TELEGRAPH_TICKS, minimumTelegraphTicks);
        int base = Math.max(minimum, baseIntervalTicks);
        if (!channelerAccelerated) {
            return base;
        }
        return Math.max(minimum, (int) Math.round(base * 0.75D));
    }

    /** A ready candidate is already filtered for phase, health and charge state by the adapter. */
    public record Candidate(UUID obeliskId, boolean ready, boolean charging,
                            boolean firing, boolean alive, UUID recentTarget,
                            long lastFireTick) {
    }

    public static List<Candidate> selectReady(List<Candidate> candidates,
                                              int maximumSequences) {
        if (candidates == null || maximumSequences <= 0) {
            return List.of();
        }
        return candidates.stream()
                .filter(candidate -> candidate != null && candidate.obeliskId() != null
                        && candidate.ready() && !candidate.charging()
                        && !candidate.firing() && candidate.alive())
                .sorted(Comparator.comparingLong(Candidate::lastFireTick)
                        .thenComparing(candidate -> candidate.obeliskId().toString()))
                .limit(maximumSequences)
                .toList();
    }

    /** Pick a target without repeatedly selecting the same player when another is available. */
    public static UUID chooseTarget(List<UUID> livingParticipants,
                                    Set<UUID> recentTargets, int cursor) {
        if (livingParticipants == null || livingParticipants.isEmpty()) {
            return null;
        }
        List<UUID> ids = livingParticipants.stream().filter(id -> id != null).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        if (ids.isEmpty()) {
            return null;
        }
        Set<UUID> recent = recentTargets == null ? Set.of() : new HashSet<>(recentTargets);
        int start = Math.floorMod(cursor, ids.size());
        for (int offset = 0; offset < ids.size(); offset++) {
            UUID candidate = ids.get((start + offset) % ids.size());
            if (!recent.contains(candidate)) {
                return candidate;
            }
        }
        return ids.get(start);
    }
}
