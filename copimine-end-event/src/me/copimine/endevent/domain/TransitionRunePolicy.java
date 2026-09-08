package me.copimine.endevent.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure validation and hold timing for a V2 transition-rune check.
 *
 * <p>The Bukkit adapter decides where runes live and which players are inside
 * their small occupancy volumes. This policy deliberately knows only stable
 * UUIDs and rune ids, so a disconnect, death, duplicated pad, or clock edge
 * cannot be hidden by a visual update.</p>
 */
public final class TransitionRunePolicy {
    private TransitionRunePolicy() {
    }

    public enum Reason {
        IDLE,
        HOLDING,
        READY,
        EMPTY_ROSTER,
        MISSING_ROSTER_MEMBER,
        INELIGIBLE_ROSTER_MEMBER,
        DUPLICATE_RUNE
    }

    public record RuneOccupancy(UUID playerId, String runeId, boolean connected,
                                boolean alive, boolean insideArena) {
        public boolean eligible() {
            return playerId != null && runeId != null && !runeId.isBlank()
                    && connected && alive && insideArena;
        }
    }

    public record Check(boolean complete, Set<UUID> missingPlayers, Reason reason) {
        public Check {
            missingPlayers = Set.copyOf(missingPlayers == null ? Set.of() : missingPlayers);
            reason = reason == null ? Reason.IDLE : reason;
        }
    }

    public record HoldState(long startedAtMillis, boolean complete, Reason reason) {
        public HoldState {
            startedAtMillis = Math.max(0L, startedAtMillis);
            reason = reason == null ? Reason.IDLE : reason;
        }

        public static HoldState idle() {
            return new HoldState(0L, false, Reason.IDLE);
        }
    }

    public static Check evaluate(Set<UUID> roster, Collection<RuneOccupancy> occupancies) {
        Set<UUID> expected = new LinkedHashSet<>(roster == null ? Set.of() : roster);
        expected.remove(null);
        if (expected.isEmpty()) {
            return new Check(false, Set.of(), Reason.EMPTY_ROSTER);
        }

        Collection<RuneOccupancy> observed = occupancies == null ? Set.of() : occupancies;
        Set<UUID> validPlayers = new LinkedHashSet<>();
        Set<UUID> invalidPlayers = new LinkedHashSet<>();
        Set<String> usedRunes = new LinkedHashSet<>();
        boolean duplicateRune = false;
        for (RuneOccupancy occupancy : observed) {
            if (occupancy == null || !expected.contains(occupancy.playerId())) {
                continue;
            }
            if (!occupancy.eligible()) {
                invalidPlayers.add(occupancy.playerId());
                continue;
            }
            if (!usedRunes.add(occupancy.runeId())) {
                duplicateRune = true;
                invalidPlayers.add(occupancy.playerId());
                continue;
            }
            validPlayers.add(occupancy.playerId());
        }

        Set<UUID> missing = new LinkedHashSet<>(expected);
        missing.removeAll(validPlayers);
        if (!missing.isEmpty()) {
            Reason reason = duplicateRune ? Reason.DUPLICATE_RUNE
                    : intersects(missing, invalidPlayers)
                    ? Reason.INELIGIBLE_ROSTER_MEMBER
                    : Reason.MISSING_ROSTER_MEMBER;
            return new Check(false, missing, reason);
        }
        return new Check(true, Set.of(), Reason.READY);
    }

    public static HoldState advance(HoldState previous, Check check, long nowMillis, long holdMillis) {
        long now = Math.max(0L, nowMillis);
        long required = Math.max(1L, holdMillis);
        if (check == null || !check.complete()) {
            return new HoldState(0L, false, check == null ? Reason.IDLE : check.reason());
        }
        HoldState prior = previous == null ? HoldState.idle() : previous;
        if (prior.complete()) {
            return new HoldState(prior.startedAtMillis(), true, Reason.READY);
        }
        long started = prior.startedAtMillis();
        if (started <= 0L || now < started) {
            return new HoldState(now, false, Reason.HOLDING);
        }
        if (now - started >= required) {
            return new HoldState(started, true, Reason.READY);
        }
        return new HoldState(started, false, Reason.HOLDING);
    }

    private static boolean intersects(Set<UUID> left, Set<UUID> right) {
        return left.stream().anyMatch(right::contains);
    }
}
