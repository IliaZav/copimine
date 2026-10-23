package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded server policy for the permanent guardians used by the boss's
 * LAST_SEAL stage.
 *
 * The policy deliberately contains no Bukkit types.  The plugin adapter owns
 * entities and effects; this class owns the health budget, shield window and
 * deterministic scheduling rules that must survive a restart or a change in
 * the number of active players.
 */
public final class TentacleGuardianPolicy {
    public static final int DAMAGE_WINDOW_TICKS = 300; // 15 seconds
    public static final int RESPAWN_DELAY_TICKS = 800; // 40 seconds
    public static final int ATTACK_INTERVAL_TICKS = 220;
    public static final int ATTACK_STAGGER_TICKS = 20;
    public static final int MIN_TOTAL_HEALTH = 120;
    public static final int MAX_TOTAL_HEALTH = 336;
    public static final String SNAPSHOT_PREFIX = "tentacle-guardian.";
    private static final int MAX_SNAPSHOT_SLOTS = TentacleScalingPolicy.MAX_PERMANENT;

    private TentacleGuardianPolicy() {
    }

    /**
     * Keep a small two-player budget while adding bounded pressure for a full
     * party.  The cap is independent of the number of entities in the room.
     */
    public static int totalHealthBudget(int players) {
        int safePlayers = Math.max(0, Math.min(20, players));
        if (safePlayers == 0) {
            return 0;
        }
        return Math.min(MAX_TOTAL_HEALTH,
                MIN_TOTAL_HEALTH + Math.max(0, safePlayers - 2) * 12);
    }

    public static int perGuardianHealth(int players) {
        int count = TentacleScalingPolicy.permanentFor(players, BossPhase.LAST_SEAL);
        if (count <= 0) {
            return 0;
        }
        int total = totalHealthBudget(players);
        return (total + count - 1) / count;
    }

    public static boolean shielded(BossPhase stage, int livingGuardians,
                                   boolean damageWindowOpen) {
        return shielded(stage, livingGuardians, damageWindowOpen, false);
    }

    /**
     * Keep the seal closed during the single-tick guardian initialization
     * boundary.  Stage changes and the central runtime tick are both main
     * thread operations, but a player damage event can arrive between them;
     * allowing that gap would let a full party kill the boss before its
     * permanent guardians exist.
     */
    public static boolean shielded(BossPhase stage, int livingGuardians,
                                   boolean damageWindowOpen,
                                   boolean guardiansPendingInitialization) {
        return stage == BossPhase.LAST_SEAL
                && !damageWindowOpen
                && (livingGuardians > 0 || guardiansPendingInitialization);
    }

    public static boolean damageWindowOpen(BossPhase stage, int livingGuardians,
                                           long startedTick, long nowTick) {
        return damageWindowOpen(stage, livingGuardians, startedTick, nowTick,
                DAMAGE_WINDOW_TICKS);
    }

    public static boolean damageWindowOpen(BossPhase stage, int livingGuardians,
                                           long startedTick, long nowTick,
                                           int durationTicks) {
        int safeDuration = Math.max(1, durationTicks);
        return stage == BossPhase.LAST_SEAL
                && livingGuardians <= 0
                && startedTick >= 0L
                && nowTick >= startedTick
                && nowTick - startedTick < safeDuration;
    }

    public static boolean respawnDue(long nowTick, long respawnTick,
                                     boolean frozen) {
        return !frozen && respawnTick >= 0L && nowTick >= respawnTick;
    }

    /**
     * A slot with no respawn schedule is the initial Last Seal spawn and is
     * ready immediately. A slot with a schedule must wait for that schedule
     * unless the guardian is still alive.
     */
    public static boolean slotReadyForSpawn(double health, Long respawnTick,
                                            long nowTick, boolean frozen) {
        if (Double.isFinite(health) && health > 0.0D) {
            return true;
        }
        return respawnTick == null
                || respawnDue(nowTick, respawnTick, frozen);
    }

    public static long firstAttackTick(long spawnTick, int slot) {
        return spawnTick + 60L + Math.max(0, slot) * ATTACK_STAGGER_TICKS;
    }

    /**
     * Preserve a pending attack across a hit flinch. A normally completed
     * attack has no pending tick and starts a fresh staggered interval.
     */
    public static long nextAttackTickAfterRecovery(long pendingAttackTick,
                                                    long nowTick, int slot) {
        return pendingAttackTick >= 0L ? pendingAttackTick
                : nowTick + ATTACK_INTERVAL_TICKS
                + Math.max(0, slot) * ATTACK_STAGGER_TICKS;
    }

    public static boolean attackDue(long nowTick, long nextAttackTick,
                                    boolean busy) {
        return !busy && nextAttackTick >= 0L && nowTick >= nextAttackTick;
    }

    public static boolean canBeginAttack(TentacleAnimationPolicy.Kind kind,
                                         TentacleAnimationPolicy.State state) {
        return kind == TentacleAnimationPolicy.Kind.PERMANENT
                && (TentacleAnimationPolicy.canonical(state)
                == TentacleAnimationPolicy.State.SHIELD_CHANNEL
                || TentacleAnimationPolicy.canonical(state)
                == TentacleAnimationPolicy.State.READY);
    }

    /**
     * Restart-safe guardian state. Respawn and damage-window deadlines are
     * re-based onto the new tick counter so a restart cannot shorten either.
     */
    public record SnapshotState(Map<Integer, Double> health,
                                Map<Integer, Long> respawnAt,
                                long damageWindowStartedTick) {
    }

    public static Map<String, String> encodeSnapshot(Map<Integer, Double> health,
                                                     Map<Integer, Long> respawnAt,
                                                     long damageWindowStartedTick,
                                                     long nowTick) {
        Map<String, String> encoded = new LinkedHashMap<>();
        long safeNow = Math.max(0L, nowTick);
        if (damageWindowStartedTick >= 0L && damageWindowStartedTick <= safeNow) {
            long elapsed = safeNow - damageWindowStartedTick;
            if (elapsed < DAMAGE_WINDOW_TICKS) {
                encoded.put(SNAPSHOT_PREFIX + "damage-window-elapsed", Long.toString(elapsed));
            }
        }
        for (int slot = 0; slot < MAX_SNAPSHOT_SLOTS; slot++) {
            Double current = health.get(slot);
            Long respawn = respawnAt.get(slot);
            if (current == null && respawn == null) continue;
            String base = SNAPSHOT_PREFIX + slot + ".";
            if (current != null) {
                if (!Double.isFinite(current) || current < 0.0D) {
                    throw new IllegalArgumentException("INVALID_GUARDIAN_HEALTH");
                }
                encoded.put(base + "health", Double.toString(current));
            }
            if (respawn != null) {
                encoded.put(base + "respawn-in", Long.toString(Math.max(0L, respawn - safeNow)));
            }
        }
        return Map.copyOf(encoded);
    }

    /** Returns null when the snapshot predates guardian persistence. */
    public static SnapshotState decodeSnapshot(Map<String, String> progress, long nowTick) {
        if (progress == null || progress.keySet().stream()
                .noneMatch(key -> key.startsWith(SNAPSHOT_PREFIX))) {
            return null;
        }
        long safeNow = Math.max(0L, nowTick);
        Map<Integer, Double> health = new LinkedHashMap<>();
        Map<Integer, Long> respawn = new LinkedHashMap<>();
        long damageWindow = -1L;
        try {
            for (Map.Entry<String, String> entry : progress.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith(SNAPSHOT_PREFIX)) continue;
                String suffix = key.substring(SNAPSHOT_PREFIX.length());
                if (suffix.equals("damage-window-elapsed")) {
                    long elapsed = Long.parseLong(entry.getValue());
                    if (elapsed < 0L) throw new IllegalArgumentException("INVALID_DAMAGE_WINDOW");
                    if (elapsed < DAMAGE_WINDOW_TICKS) {
                        damageWindow = Math.max(0L, safeNow - elapsed);
                    }
                    continue;
                }
                int dot = suffix.indexOf('.');
                if (dot <= 0) throw new IllegalArgumentException("UNKNOWN_GUARDIAN_KEY");
                int slot = Integer.parseInt(suffix.substring(0, dot));
                if (slot < 0 || slot >= MAX_SNAPSHOT_SLOTS) {
                    throw new IllegalArgumentException("INVALID_GUARDIAN_SLOT");
                }
                String field = suffix.substring(dot + 1);
                if (field.equals("health")) {
                    double value = Double.parseDouble(entry.getValue());
                    if (!Double.isFinite(value) || value < 0.0D) {
                        throw new IllegalArgumentException("INVALID_GUARDIAN_HEALTH");
                    }
                    health.put(slot, value);
                } else if (field.equals("respawn-in")) {
                    long remaining = Long.parseLong(entry.getValue());
                    if (remaining < 0L) throw new IllegalArgumentException("INVALID_GUARDIAN_RESPAWN");
                    respawn.put(slot, Math.addExact(safeNow, remaining));
                } else {
                    throw new IllegalArgumentException("UNKNOWN_GUARDIAN_KEY");
                }
            }
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("INVALID_GUARDIAN_SNAPSHOT", invalid);
        }
        return new SnapshotState(Map.copyOf(health), Map.copyOf(respawn), damageWindow);
    }

    public static String healthVisualState(double current, double maximum) {
        if (!Double.isFinite(current) || !Double.isFinite(maximum)
                || maximum <= 0.0D || current <= 0.0D) {
            return "DEAD";
        }
        double ratio = Math.max(0.0D, Math.min(1.0D, current / maximum));
        if (ratio <= 1.0D / 3.0D) {
            return "CRITICAL";
        }
        if (ratio <= 2.0D / 3.0D) {
            return "DAMAGED";
        }
        return "FULL";
    }
}
