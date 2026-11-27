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

        default boolean mutable(int x, int y, int z) {
            return true;
        }

        default boolean blocked(Cuboid bounds) {
            return false;
        }
    }

    public interface Journal {
        List<String> load() throws java.io.IOException;
        void write(List<String> keys) throws java.io.IOException;
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
    private final Journal storage;
    private final List<String> journal = new ArrayList<>();
    private GateState state = GateState.CLOSED;
    private boolean journaled;
    private boolean loaded;

    public PhysicalGateTransaction(GateDefinition gate, GateWorld world) {
        this(gate, world, new Journal() {
            private List<String> keys = List.of();

            @Override public List<String> load() {
                return keys;
            }

            @Override public void write(List<String> snapshot) {
                keys = List.copyOf(snapshot);
            }
        });
    }

    public PhysicalGateTransaction(GateDefinition gate, GateWorld world, Journal storage) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.world = Objects.requireNonNull(world, "world");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.mask = PhysicalGatePolicy.doorMask(gate);
        this.state = gate.initialOpen() ? GateState.RECOVERY_REQUIRED : GateState.CLOSED;
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

    public Result open() {
        return apply(true, List.of());
    }

    public Result close(List<int[]> occupiedBlocks) {
        return apply(false, Objects.requireNonNull(occupiedBlocks, "occupiedBlocks"));
    }

    public Result recover() {
        return close(List.of());
    }

    private Result apply(boolean open, List<int[]> occupied) {
        if (!chunksLoaded()) {
            state = GateState.RECOVERY_REQUIRED;
            return Result.CHUNK_UNLOADED;
        }
        try {
            List<String> before = readMask();
            if (!loaded) {
                List<String> saved = storage.load();
                if (!saved.isEmpty()) {
                    if (saved.size() != mask.size() || saved.stream().allMatch(PhysicalGateTransaction::isAir)) {
                        throw new IllegalStateException("Invalid gate journal for " + gate.id());
                    }
                    journal.addAll(saved);
                    journaled = true;
                }
                loaded = true;
            }
            if (!journaled) {
                if (before.stream().allMatch(PhysicalGateTransaction::isAir)) {
                    throw new IllegalStateException("Missing solid builder mask for " + gate.id());
                }
                if (!open) {
                    state = GateState.CLOSED;
                    return Result.ALREADY_CLOSED;
                }
                storage.write(before);
                journal.addAll(before);
                journaled = true;
            }
            if (!matchesJournal(before)) {
                throw new IllegalStateException("Builder blocks changed in gate " + gate.id());
            }
            List<String> after = open ? java.util.Collections.nCopies(mask.size(), AIR) : journal;
            if (before.equals(after)) {
                state = open ? GateState.OPEN : GateState.CLOSED;
                return open ? Result.ALREADY_OPEN : Result.ALREADY_CLOSED;
            }
            if (!open && (world.blocked(gate.bounds()) || PhysicalGatePolicy.occupied(mask, occupied))) {
                state = GateState.BLOCKED_BY_ENTITY;
                return Result.BLOCKED_BY_ENTITY;
            }
            replace(before, after);
            state = open ? GateState.OPEN : GateState.CLOSED;
            return Result.APPLIED;
        } catch (java.io.IOException | RuntimeException error) {
            state = GateState.RECOVERY_REQUIRED;
            return Result.RECOVERY_REQUIRED;
        }
    }

    private List<String> readMask() {
        List<String> keys = new ArrayList<>(mask.size());
        for (int[] cell : mask) {
            if (!world.mutable(cell[0], cell[1], cell[2])) {
                throw new IllegalStateException("Protected block in gate " + gate.id());
            }
            keys.add(world.blockKey(cell[0], cell[1], cell[2]));
        }
        return keys;
    }

    private boolean matchesJournal(List<String> before) {
        for (int i = 0; i < mask.size(); i++) {
            if (!before.get(i).equals(AIR) && !before.get(i).equals(journal.get(i))) {
                return false;
            }
        }
        return true;
    }

    private void replace(List<String> before, List<String> after) {
        List<Integer> attempted = new ArrayList<>();
        try {
            for (int index = 0; index < mask.size(); index++) {
                if (!before.get(index).equals(after.get(index))) {
                    attempted.add(index);
                    set(index, after.get(index));
                }
            }
        } catch (RuntimeException error) {
            for (int index : attempted.reversed()) {
                try {
                    set(index, before.get(index));
                } catch (RuntimeException rollback) {
                    error.addSuppressed(rollback);
                }
            }
            throw error;
        }
    }

    private void set(int index, String key) {
        int[] cell = mask.get(index);
        world.setBlock(cell[0], cell[1], cell[2], key);
    }

    private static boolean isAir(String key) {
        return AIR.equals(key) || "minecraft:cave_air".equals(key) || "minecraft:void_air".equals(key);
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
