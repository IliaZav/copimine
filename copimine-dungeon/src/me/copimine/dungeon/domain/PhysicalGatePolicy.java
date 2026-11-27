package me.copimine.dungeon.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure server-side core of the physical gate controller (spec OV4.10
 * {@code PhysicalGateController}; verification G01-G09).
 *
 * <p>A gate is a real block mask, never a client illusion. The controller
 * records the original block snapshot for the exact doorway occupancy, swaps
 * that mask when opening/closing, and restores the snapshot on close so builder
 * decor is never lost. This class holds the deterministic, Bukkit-free part of
 * that contract - the bounded mask, the cross-gate overlap test, the animation
 * id and the secret classification - so door geometry and cleanup are provable
 * without a running server. The cinematic client model is presentation only and
 * can never report a doorway as traversable while this mask still blocks it.</p>
 */
public final class PhysicalGatePolicy {

    /** Fail-closed cap: a doorway mask may never claim an unbounded volume. */
    public static final int MAX_GATE_BLOCKS = 4096;

    private PhysicalGatePolicy() {
    }

    /** The exact inclusive block mask of a gate, in a stable scan order. */
    public static List<int[]> doorMask(GateDefinition gate) {
        Objects.requireNonNull(gate, "gate");
        return doorMask(gate.bounds());
    }

    /** {@link #doorMask(GateDefinition)} over raw bounds (secrets reuse it). */
    public static List<int[]> doorMask(Cuboid bounds) {
        Objects.requireNonNull(bounds, "bounds");
        long sizeX = (long) bounds.maxX() - bounds.minX() + 1L;
        long sizeY = (long) bounds.maxY() - bounds.minY() + 1L;
        long sizeZ = (long) bounds.maxZ() - bounds.minZ() + 1L;
        if (sizeX > MAX_GATE_BLOCKS || sizeY > MAX_GATE_BLOCKS || sizeZ > MAX_GATE_BLOCKS
                || sizeX * sizeY * sizeZ > MAX_GATE_BLOCKS || bounds.maxX() == Integer.MAX_VALUE
                || bounds.maxY() == Integer.MAX_VALUE || bounds.maxZ() == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Маска ворот превышает допустимый объём "
                    + MAX_GATE_BLOCKS + " блоков.");
        }
        int volume = (int) (sizeX * sizeY * sizeZ);
        List<int[]> mask = new ArrayList<>(volume);
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    mask.add(new int[]{x, y, z});
                }
            }
        }
        return mask;
    }

    /** True when two gates share at least one doorway block in the same world. */
    public static boolean overlaps(GateDefinition a, GateDefinition b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.id().equals(b.id())) {
            return false;
        }
        if (!a.world().equalsIgnoreCase(b.world())) {
            return false;
        }
        return a.bounds().intersects(b.bounds());
    }

    /** Every conflicting gate pair, each unordered pair at most once. */
    public static List<String[]> overlapConflicts(List<GateDefinition> gates) {
        Objects.requireNonNull(gates, "gates");
        List<String[]> conflicts = new ArrayList<>();
        for (int i = 0; i < gates.size(); i++) {
            for (int j = i + 1; j < gates.size(); j++) {
                if (overlaps(gates.get(i), gates.get(j))) {
                    conflicts.add(new String[]{gates.get(i).id(), gates.get(j).id()});
                }
            }
        }
        return conflicts;
    }

    /** The animation id attached to the real door, keyed by style. */
    public static String animationId(GateStyle style) {
        Objects.requireNonNull(style, "style");
        return "gate." + style.token();
    }

    /** A secret gate is only ever revealed to an authorized builder wand. */
    public static boolean isSecret(GateDefinition gate) {
        Objects.requireNonNull(gate, "gate");
        return gate.style().isSecretFamily();
    }

    /** Public classification shown to ordinary players (never leaks a secret). */
    public static String classification(GateDefinition gate) {
        return isSecret(gate) ? "secret" : "public";
    }

    /** The ordered snapshot the controller writes and later restores verbatim. */
    public static List<int[]> sourceSnapshot(GateDefinition gate) {
        return doorMask(gate);
    }

    /**
     * Whether the doorway volume is occupied, in which case the controller must
     * defer closure into {@code BLOCKED_BY_ENTITY} instead of an unsafe close
     * that would entomb or suffocate a player.
     */
    public static boolean occupied(List<int[]> mask, List<int[]> occupiedBlocks) {
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(occupiedBlocks, "occupiedBlocks");
        for (int[] block : occupiedBlocks) {
            for (int[] door : mask) {
                if (block[0] == door[0] && block[1] == door[1] && block[2] == door[2]) {
                    return true;
                }
            }
        }
        return false;
    }
}
