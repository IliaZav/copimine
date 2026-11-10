package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Bounded, deterministic fire sequencing for the physical Wave 4 towers. */
public final class ObeliskFireDirectorPolicy {
    public static final int DEFAULT_FIRE_INTERVAL_TICKS = 26;
    public static final int MIN_TELEGRAPH_TICKS = 20;
    public static final int MAX_RELEASE_GAP_TICKS = 60;
    public static final int MAX_BASE_FIRE_INTERVAL_TICKS = MAX_RELEASE_GAP_TICKS - MIN_TELEGRAPH_TICKS;
    public static final int POST_RELEASE_RECOVERY_TICKS = 4;
    public static final int MIN_STAGGER_TICKS = 6;
    public static final int MAX_STAGGER_TICKS = 10;
    public static final int MAX_FIREBALL_LIFETIME_TICKS = 160;
    public static final int MIN_FIREBALL_RELEASE_GAP_TICKS = 40;

    private ObeliskFireDirectorPolicy() {
    }

    public static int maxSimultaneousSequences(int livingPlayers) {
        return ObeliskScalingPolicy.profileForPlayers(livingPlayers).fireballCap();
    }

    /**
     * Bound live projectiles plus one reserved warning sequence per active
     * tower. A projectile can remain for 160 ticks; the fastest release cycle
     * is the 20-tick rest plus the 20-tick warning.
     */
    public static int fireballAdmissionSlots(int activeObelisks) {
        int count = Math.max(0, Math.min(ObeliskScalingPolicy.MAX_OBELISKS, activeObelisks));
        int livePerObelisk = (MAX_FIREBALL_LIFETIME_TICKS + MIN_FIREBALL_RELEASE_GAP_TICKS - 1)
                / MIN_FIREBALL_RELEASE_GAP_TICKS;
        return count * (livePerObelisk + 1);
    }

    public record FireSequence(String eventId, long generation, UUID obeliskId,
                               UUID targetId, long startedTick, long releaseTick,
                               long expiresTick) {
        public FireSequence {
            if (eventId == null || eventId.isBlank() || generation <= 0L
                    || obeliskId == null || targetId == null || startedTick < 0L
                    || releaseTick <= startedTick || expiresTick <= releaseTick) {
                throw new IllegalArgumentException("invalid Wave 4 fire sequence");
            }
        }
    }

    public static FireSequence beginFireSequence(String eventId, long generation,
                                                 UUID obeliskId, UUID targetId,
                                                 long startedTick, int warningTicks) {
        if (eventId == null || eventId.isBlank() || generation <= 0L
                || obeliskId == null || targetId == null || startedTick < 0L
                || warningTicks < MIN_TELEGRAPH_TICKS
                || startedTick > Long.MAX_VALUE - warningTicks - POST_RELEASE_RECOVERY_TICKS) {
            return null;
        }
        long releaseTick = startedTick + warningTicks;
        return new FireSequence(eventId, generation, obeliskId, targetId,
                startedTick, releaseTick, releaseTick + POST_RELEASE_RECOVERY_TICKS);
    }

    public static boolean validFireSequence(FireSequence sequence, String eventId,
                                            long generation, UUID obeliskId, long nowTick) {
        return sequence != null && eventId != null && eventId.equals(sequence.eventId())
                && generation > 0L && generation == sequence.generation()
                && obeliskId != null && obeliskId.equals(sequence.obeliskId())
                && nowTick >= sequence.startedTick() && nowTick < sequence.expiresTick();
    }

    public static int staggerTicks(int fireIntervalTicks, int totalObelisks) {
        int interval = Math.max(MIN_TELEGRAPH_TICKS, fireIntervalTicks);
        int total = Math.max(1, Math.min(ObeliskScalingPolicy.MAX_OBELISKS, totalObelisks));
        return Math.max(MIN_STAGGER_TICKS,
                Math.min(MAX_STAGGER_TICKS, interval / total));
    }

    public static int fireInterval(int baseIntervalTicks, boolean channelerAccelerated,
                                   int minimumTelegraphTicks) {
        int minimum = Math.min(MAX_BASE_FIRE_INTERVAL_TICKS,
                Math.max(MIN_TELEGRAPH_TICKS, minimumTelegraphTicks));
        int base = Math.min(MAX_BASE_FIRE_INTERVAL_TICKS, Math.max(minimum, baseIntervalTicks));
        if (!channelerAccelerated) {
            return base;
        }
        return Math.max(minimum, (int) Math.round(base * 0.75D));
    }

    /**
     * Party-scaled fire cadence, W4-02.
     *
     * <p>The towers fired on the same fixed cadence for two players as for
     * twenty, so a small squad standing in front of four obelisks ate the same
     * barrage a full raid needs to stay busy. The cadence now stretches for a
     * small party and tightens for a large one. The telegraph floor is never
     * crossed: a slower tower is still a readable tower, and the ceiling keeps
     * the release gap inside {@link #MAX_BASE_FIRE_INTERVAL_TICKS} so projectile
     * admission math stays valid.</p>
     */
    public static int fireInterval(int baseIntervalTicks, boolean channelerAccelerated,
                                   int minimumTelegraphTicks, int livingPlayers) {
        int minimum = Math.min(MAX_BASE_FIRE_INTERVAL_TICKS,
                Math.max(MIN_TELEGRAPH_TICKS, minimumTelegraphTicks));
        int base = Math.min(MAX_BASE_FIRE_INTERVAL_TICKS, Math.max(minimum, baseIntervalTicks));
        int scaled = (int) Math.round(base * intervalMultiplier(livingPlayers));
        scaled = Math.min(MAX_BASE_FIRE_INTERVAL_TICKS, Math.max(minimum, scaled));
        if (!channelerAccelerated) {
            return scaled;
        }
        return Math.max(minimum, (int) Math.round(scaled * 0.75D));
    }

    /**
     * Cadence multiplier for a living party, W4-02.
     *
     * <p>{@code 1.0} is the tuned full-raid cadence. A two-player squad gets
     * roughly half the incoming fire; a large raid gets somewhat more pressure
     * than the old flat value rather than the same absolute amount.</p>
     */
    public static double intervalMultiplier(int livingPlayers) {
        int players = Math.max(1, livingPlayers);
        if (players <= 2) return 2.0D;
        if (players <= 4) return 1.65D;
        if (players <= 7) return 1.3D;
        if (players <= 10) return 1.0D;
        if (players <= 15) return 0.85D;
        return 0.72D;
    }

    /**
     * How many towers may hold a live sequence at once, W4-02.
     *
     * <p>The old bound let every obelisk fire simultaneously regardless of
     * party size. A two-player squad facing four towers saw four overlapping
     * warnings and four projectiles for two dodges. The limit grows with the
     * room it has to threaten, never below one, so the assault still reads as
     * incoming fire instead of a silent arena.</p>
     */
    public static int simultaneousSequenceLimit(int livingPlayers, int obeliskCount) {
        int towers = Math.max(1, obeliskCount);
        int players = Math.max(1, livingPlayers);
        int limit;
        if (players <= 2) limit = 1;
        else if (players <= 4) limit = 2;
        else if (players <= 7) limit = 3;
        else if (players <= 10) limit = 4;
        else limit = ObeliskScalingPolicy.MAX_OBELISKS;
        return Math.min(towers, Math.min(limit, ObeliskScalingPolicy.MAX_OBELISKS));
    }

    /** A ready candidate is already filtered for phase, health and charge state by the adapter. */
    public record Candidate(UUID obeliskId, boolean ready, boolean charging,
                            boolean firing, boolean alive, UUID recentTarget,
                            long lastFireTick) {
    }

    public enum SpecialKind { FIRE, PULSE }

    public record SpecialCandidate(UUID obeliskId, SpecialKind kind, long readyTick) { }

    /** Oldest due action wins the existing single reservation; map order cannot monopolize it. */
    public static Optional<SpecialCandidate> selectSpecial(List<SpecialCandidate> candidates, long nowTick) {
        if (candidates == null || nowTick < 0L) return Optional.empty();
        return candidates.stream()
                .filter(candidate -> candidate != null && candidate.obeliskId() != null
                        && candidate.kind() != null && candidate.readyTick() <= nowTick)
                .min(Comparator.comparingLong(SpecialCandidate::readyTick)
                        .thenComparing(candidate -> candidate.obeliskId().toString())
                        .thenComparing(SpecialCandidate::kind));
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
