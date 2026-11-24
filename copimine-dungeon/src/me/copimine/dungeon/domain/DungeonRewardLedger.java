package me.copimine.dungeon.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure reward receipt ledger (spec OV4.10 {@code DungeonRewardLedger};
 * verification R06).
 *
 * <p>Every grant is keyed by the room generation and the player, so a lag spike,
 * a duplicated kill callback, a relog or a restart between {@code clear} and
 * {@code reward} can never pay the same reward twice. A grant is idempotent, a
 * stale generation is rejected instead of resurrecting an old payout, and an
 * interrupted grant can be rolled back exactly once so its slot is claimable
 * again. The ledger owns no live gameplay HP or world state; it only records
 * receipts keyed by generation and player.</p>
 */
public final class DungeonRewardLedger {

    /** Outcome of a claim attempt. */
    public enum Decision {
        GRANTED,
        ALREADY_CLAIMED,
        STALE_GENERATION,
        INVALID
    }

    public enum Status {
        GRANTED,
        ROLLED_BACK
    }

    /** One durable receipt for a single room generation and player. */
    public record Receipt(String roomId, long generation, String playerId, RewardMode mode, Status status) {
    }

    private final Map<String, Receipt> receipts = new LinkedHashMap<>();
    private final Map<String, Long> latestGeneration = new LinkedHashMap<>();

    private static String key(String roomId, long generation, String playerId) {
        return roomId + '\u0000' + generation + '\u0000' + playerId;
    }

    /**
     * Attempts to grant the reward exactly once for this generation and player.
     * A repeated call with the same key returns {@link Decision#ALREADY_CLAIMED},
     * and a call fenced behind a newer generation returns
     * {@link Decision#STALE_GENERATION}.
     */
    public Decision claim(String roomId, long generation, String playerId, RewardMode mode) {
        if (isBlank(roomId) || isBlank(playerId) || mode == null || generation < 0) {
            return Decision.INVALID;
        }
        long latest = latestGeneration.getOrDefault(roomId, -1L);
        if (generation < latest) {
            return Decision.STALE_GENERATION;
        }
        if (generation > latest) {
            latestGeneration.put(roomId, generation);
        }
        Receipt existing = receipts.get(key(roomId, generation, playerId));
        if (existing != null && existing.status() == Status.GRANTED) {
            return Decision.ALREADY_CLAIMED;
        }
        receipts.put(key(roomId, generation, playerId),
                new Receipt(roomId, generation, playerId, mode, Status.GRANTED));
        return Decision.GRANTED;
    }

    /**
     * Rolls a grant back exactly once, leaving the receipt recorded so the same
     * player can legitimately be paid again after recovery. Returns whether a
     * live grant was actually reversed.
     */
    public boolean rollback(String roomId, long generation, String playerId) {
        if (isBlank(roomId) || isBlank(playerId)) {
            return false;
        }
        String key = key(roomId, generation, playerId);
        Receipt receipt = receipts.get(key);
        if (receipt == null || receipt.status() != Status.GRANTED) {
            return false;
        }
        receipts.put(key, new Receipt(roomId, generation, playerId, receipt.mode(), Status.ROLLED_BACK));
        return true;
    }

    public boolean isGranted(String roomId, long generation, String playerId) {
        Receipt receipt = receipts.get(key(roomId, generation, playerId));
        return receipt != null && receipt.status() == Status.GRANTED;
    }

    /** Number of live (not rolled back) grants, counted across all rooms. */
    public int grantedCount() {
        int count = 0;
        for (Receipt receipt : receipts.values()) {
            if (receipt.status() == Status.GRANTED) {
                count++;
            }
        }
        return count;
    }

    public int receiptCount() {
        return receipts.size();
    }

    public long latestGeneration(String roomId) {
        return latestGeneration.getOrDefault(roomId, -1L);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
