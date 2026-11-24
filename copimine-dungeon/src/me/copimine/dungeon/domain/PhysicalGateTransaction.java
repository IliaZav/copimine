package me.copimine.dungeon.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure core of the physical gate controller (spec OV4.10
 * {@code PhysicalGateController}; verification G01-G09).
 *
 * <p>A gate is a real block mask, never a client illusion. Opening snapshots the
 * exact doorway block data, replaces the mask with air and keeps that journal so
 * the original builder decor can be restored verbatim. Closing refuses to entomb
 * a player ({@link Result#BLOCKED_BY_ENTITY}), refuses to guess when the journal
 * is missing ({@link Result#RECOVERY_REQUIRED}) and refuses to write into an
 * unloaded chunk ({@link Result#CHUNK_UNLOADED}). Restoring is journal-driven and
 * idempotent, so a crash mid-animation can be reconciled without losing blocks.</p>
 *
 * <p>The block storage is an injected {@link GateWorld} so the whole transaction
 * is unit-testable without a running Paper server; the server adapter is the only
 * class that touches the live world.</p>
 */
public final class PhysicalGateTransaction {

    /** The block key every opened doorway cell becomes. */
    public static final String AIR = "minecraft:air";

    /** The minimal block access the transaction needs; no Bukkit types. */
    public interface GateWorld {
        boolean chunkLoaded(int x, int y, int z);

        String blockKey(int x, int y, int z);

        void setBlock(int x, int y, int z, String key);
    }

    /** Outcome of an open/close/recover request. */
    public enum Result {
        APPLIED,
        ALREADY_OPEN,
        ALREADY_CLOSED,
        BLOCKED_BY_ENTITY,
        CHUNK_UNLOADED,
        RECOVERY_REQUIRED
    }

    private final GateDefinition gate;
    private final GateWorld world;
    private final List<int[]> mask;
    private final List<String> journal = new ArrayList<>();
    private GateState state;
    private boolean journaled;

    public PhysicalGateTransaction(GateDefinition gate, GateWorld world) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.world = Objects.requireNonNull(world, "world");
        this.mask = PhysicalGatePolicy.doorMask(gate);
        this.state = gate.initialState();
        if (this.state == GateState.OPEN && chunksLoaded()) {
            // An initially-open gate has no snapshot yet; capture it now so a
            // later close can restore the builder's original doorway.
            snapshot();
        }
    }

    public GateDefinition gate() {
        return gate;
    }

    public GateState state() {
        return state;
    }

    public String animationId() {
        return PhysicalGatePolicy.animationId(gate.style());
    }

    public boolean journaled() {
        return journaled;
    }

    /** The recorded original block keys, in mask scan order. */
    public List<String> journalKeys() {
        return List.copyOf(journal);
    }

    public int blockCount() {
        return mask.size();
    }

    /** Opens the doorway, snapshotting the original blocks on first use. */
    public Result open() {
        if (state == GateState.OPEN) {
            return Result.ALREADY_OPEN;
        }
        if (!chunksLoaded()) {
            state = GateState.RECOVERY_REQUIRED;
            return Result.CHUNK_UNLOADED;
        }
        if (!journaled) {
            snapshot();
        }
        for (int[] block : mask) {
            world.setBlock(block[0], block[1], block[2], AIR);
        }
        state = GateState.OPEN;
        return Result.APPLIED;
    }

    /**
     * Closes the doorway and restores the original builder blocks. A player,
     * mob or other occupant inside the mask defers closure instead of being
     * suffocated, leaving the gate open in {@code BLOCKED_BY_ENTITY}.
     */
    public Result close(List<int[]> occupiedBlocks) {
        Objects.requireNonNull(occupiedBlocks, "occupiedBlocks");
        if (state == GateState.CLOSED) {
            return Result.ALREADY_CLOSED;
        }
        if (!journaled) {
            state = GateState.RECOVERY_REQUIRED;
            return Result.RECOVERY_REQUIRED;
        }
        if (PhysicalGatePolicy.occupied(mask, occupiedBlocks)) {
            state = GateState.BLOCKED_BY_ENTITY;
            return Result.BLOCKED_BY_ENTITY;
        }
        if (!chunksLoaded()) {
            state = GateState.RECOVERY_REQUIRED;
            return Result.CHUNK_UNLOADED;
        }
        return restore();
    }

    /**
     * Reconciles a gate from its durable journal after a restart or abort. A gate
     * with no journal is only safe when it is already closed.
     */
    public Result recover() {
        if (!journaled) {
            return state == GateState.CLOSED ? Result.ALREADY_CLOSED : Result.RECOVERY_REQUIRED;
        }
        if (!chunksLoaded()) {
            state = GateState.RECOVERY_REQUIRED;
            return Result.CHUNK_UNLOADED;
        }
        return restore();
    }

    private Result restore() {
        for (int i = 0; i < mask.size(); i++) {
            int[] block = mask.get(i);
            world.setBlock(block[0], block[1], block[2], journal.get(i));
        }
        journal.clear();
        journaled = false;
        state = GateState.CLOSED;
        return Result.APPLIED;
    }

    private void snapshot() {
        journal.clear();
        for (int[] block : mask) {
            journal.add(world.blockKey(block[0], block[1], block[2]));
        }
        journaled = true;
    }

    private boolean chunksLoaded() {
        for (int[] block : mask) {
            if (!world.chunkLoaded(block[0], block[1], block[2])) {
                return false;
            }
        }
        return true;
    }
}
