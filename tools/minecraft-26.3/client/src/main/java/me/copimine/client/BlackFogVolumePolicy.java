package me.copimine.client;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Bounded discovery of only the occupied server-published emerald platform. */
public final class BlackFogVolumePolicy {
    public static final float EXTINCTION_PER_BLOCK = 12.0F;
    public static final float FOG_HEIGHT = 4.0F;
    public static final float CHAMBER_HEIGHT = 3.0F;
    private BlackFogVolumePolicy() {}
    public record Cell(int x, int z) {}
    public record Box(int minX, int minZ, int maxX, int maxZ, int cells) {}
    @FunctionalInterface public interface Floor { boolean safe(int x, int z); }
    public static Box find(int x, int z, Floor floor) {
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        Set<Cell> visited = new HashSet<>();
        pending.add(new Cell(x, z));
        int minX = x, maxX = x, minZ = z, maxZ = z, accepted = 0;
        while (!pending.isEmpty() && visited.size() < 25 && accepted < 9) {
            Cell p = pending.removeFirst();
            if (Math.abs(p.x - x) > 2 || Math.abs(p.z - z) > 2 || !visited.add(p)
                    || !floor.safe(p.x, p.z)) continue;
            accepted++;
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
            pending.add(new Cell(p.x - 1, p.z)); pending.add(new Cell(p.x + 1, p.z));
            pending.add(new Cell(p.x, p.z - 1)); pending.add(new Cell(p.x, p.z + 1));
        }
        return new Box(minX, minZ, maxX + 1, maxZ + 1, accepted);
    }
}
