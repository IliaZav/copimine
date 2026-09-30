package me.copimine.endevent.domain;

import java.util.Map;
import java.util.UUID;

/**
 * The single pressure gate for hostile encounter work.
 *
 * <p>Wave reserve is deliberately not represented by this class. A wave may
 * have a large reserve, while this controller only grants a bounded number of
 * simultaneous pressure slots. The Bukkit controller releases a slot when an
 * owned entity or hazard is cleaned up and asks for another allowance when it
 * is ready to admit the next reserve entry.</p>
 */
public final class PressureBudgetController {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final int HARD_CAP = 56;

    private PressureBudgetController() {
    }

    /** Return the bounded party profile used by every current wave. */
    public static Profile profileForPlayers(int livingPlayers) {
        int players = Math.max(0, Math.min(MAX_PLAYERS, livingPlayers));
        if (players < MIN_PLAYERS) {
            return Profile.EMPTY;
        }
        if (players <= 3) {
            return new Profile(players, 6, 4, 1, 1, 2);
        }
        if (players <= 5) {
            return new Profile(players, 9, 6, 1, 2, 2);
        }
        if (players <= 10) {
            return new Profile(players, 16, 10, 2, 3, 2);
        }
        if (players <= 15) {
            return new Profile(players, 22, 14, 3, 3, 3);
        }
        return new Profile(players, 30, 12, 3, 3, 3);
    }

    /**
     * Compute the next admission without allowing live pressure to exceed the
     * profile. Categories consume free slots in a deterministic order and do
     * not overwrite one another's accounting.
     */
    public static SpawnAllowance allowanceFor(int livingPlayers,
                                               int currentCommon,
                                               int currentElite,
                                               int currentHeavy,
                                               int requestedCommon,
                                               int requestedElite,
                                               int requestedHeavy) {
        Profile profile = profileForPlayers(livingPlayers);
        int common = clamp(currentCommon, 0, profile.commonCap());
        int elite = clamp(currentElite, 0, profile.eliteCap());
        int heavy = clamp(currentHeavy, 0, profile.heavyCap());
        int occupied = common + elite + heavy;
        int room = Math.max(0, Math.min(HARD_CAP, profile.activePressure()) - occupied);

        int addCommon = Math.min(Math.max(0, requestedCommon),
                Math.min(Math.max(0, profile.commonCap() - common), room));
        room -= addCommon;
        int addElite = Math.min(Math.max(0, requestedElite),
                Math.min(Math.max(0, profile.eliteCap() - elite), room));
        room -= addElite;
        int addHeavy = Math.min(Math.max(0, requestedHeavy),
                Math.min(Math.max(0, profile.heavyCap() - heavy), room));

        return new SpawnAllowance(addCommon, addElite, addHeavy,
                occupied + addCommon + addElite + addHeavy,
                Math.min(HARD_CAP, profile.activePressure()));
    }

    public static int clampTotal(int requested, int livingPlayers) {
        Profile profile = profileForPlayers(livingPlayers);
        return Math.max(0, Math.min(Math.min(HARD_CAP, profile.activePressure()), requested));
    }

    /** Return whether one more attacker may focus the same participant. */
    public static boolean mayFocusPlayer(int currentFocused, int livingPlayers) {
        Profile profile = profileForPlayers(livingPlayers);
        return currentFocused >= 0 && currentFocused < profile.maxFocusedAttackers();
    }

    /** Bounded diagnostics suitable for a log line or a local probe. */
    public static Diagnostics diagnostics(int livingPlayers,
                                          int activeCommon,
                                          int activeElites,
                                          int activeHeavy,
                                          int activeProjectiles,
                                          int activeMajorHazards,
                                          Map<UUID, Integer> focusedAttackersByPlayer,
                                          int queuedReserve,
                                          String objectiveId,
                                          long generation) {
        Profile profile = profileForPlayers(livingPlayers);
        int total = Math.max(0, activeCommon) + Math.max(0, activeElites)
                + Math.max(0, activeHeavy);
        boolean exceeded = total > profile.activePressure()
                || total > HARD_CAP
                || Math.max(0, activeProjectiles) > profile.projectileCap()
                || Math.max(0, activeMajorHazards) > profile.heavyCap();
        return new Diagnostics(Math.max(0, activeCommon), Math.max(0, activeElites),
                Math.max(0, activeHeavy), Math.max(0, activeProjectiles),
                Math.max(0, activeMajorHazards), focusedAttackersByPlayer == null
                ? Map.of() : Map.copyOf(focusedAttackersByPlayer), Math.max(0, queuedReserve),
                objectiveId == null ? "" : objectiveId, Math.max(0L, generation), exceeded);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Profile(int players,
                          int activePressure,
                          int commonCap,
                          int eliteCap,
                          int heavyCap,
                          int maxFocusedAttackers) {
        public static final Profile EMPTY = new Profile(0, 0, 0, 0, 0, 0);

        public Profile {
            players = Math.max(0, Math.min(MAX_PLAYERS, players));
            activePressure = Math.max(0, Math.min(HARD_CAP, activePressure));
            commonCap = Math.max(0, commonCap);
            eliteCap = Math.max(0, eliteCap);
            heavyCap = Math.max(0, heavyCap);
            maxFocusedAttackers = Math.max(0, maxFocusedAttackers);
            if (commonCap + eliteCap + heavyCap > HARD_CAP) {
                throw new IllegalArgumentException("pressure profile exceeds hard cap");
            }
        }

        public int reserve() {
            return Math.min(HARD_CAP, activePressure + commonCap + eliteCap + heavyCap);
        }

        public int specialCap() {
            return heavyCap;
        }

        public int projectileCap() {
            return Math.max(4, Math.min(16, 4 + players / 3));
        }
    }

    public record SpawnAllowance(int common, int elite, int heavy,
                                 int resultingActive, int activePressure) {
        public SpawnAllowance {
            common = Math.max(0, common);
            elite = Math.max(0, elite);
            heavy = Math.max(0, heavy);
            resultingActive = Math.max(0, Math.min(HARD_CAP, resultingActive));
            activePressure = Math.max(0, Math.min(HARD_CAP, activePressure));
            if (resultingActive > activePressure) {
                throw new IllegalArgumentException("spawn allowance exceeds pressure budget");
            }
        }

        public int special() {
            return heavy;
        }

        public int total() {
            return common + elite + heavy;
        }
    }

    public record Diagnostics(int activeCommon,
                              int activeElites,
                              int activeHeavy,
                              int activeProjectiles,
                              int activeMajorHazards,
                              Map<UUID, Integer> focusedAttackersByPlayer,
                              int queuedReserve,
                              String objectiveId,
                              long generation,
                              boolean hardCapExceeded) {
    }
}
