"""Execute the host-owned, journaled Wave 6 pillar collider.

The pillars used to be display-only geometry: the party could walk through a
solid obsidian tower and only the Ram's own distance test "collided". The
collider is now a real barrier volume whose original cells are journaled before
any write, applied only after the journal commits, restored on clear, and never
placed over real terrain or a container. These probes run the production
``placeRiftRamPillarCollider`` / ``clearRiftRamPillarCollider``.
"""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method(text, signature):
    start = text.index(signature)
    brace = text.index("{", start)
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[start:i + 1]
    raise AssertionError("unterminated " + signature)


PROBE = r'''
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.copimine.endevent.domain.RiftRamPolicy;

public final class RamPillarColliderProbe {
    enum Material {
        AIR, BARRIER, STONE, WATER, DIAMOND_BLOCK;
        boolean isLiquid() { return this == WATER; }
        boolean isAir() { return this == AIR; }
        BlockData createBlockData() { return new BlockData("minecraft:" + name().toLowerCase(Locale.ROOT)); }
    }
    static final class BlockData {
        final String value; BlockData(String v) { value = v; }
        String getAsString() { return value; }
        public boolean equals(Object other) { return other instanceof BlockData data && value.equals(data.value); }
    }
    static final class Block {
        final World world; final int x, y, z;
        Material type; BlockData data; boolean tile;
        Block(World w, int x, int y, int z) { world = w; this.x = x; this.y = y; this.z = z;
            type = Material.AIR; data = new BlockData("minecraft:air"); }
        Material getType() { return type; }
        boolean isLiquid() { return type.isLiquid(); }
        boolean isPassable() { return type == Material.AIR; }
        Object getState() { return tile ? new org.bukkit.block.TileState() { } : null; }
        BlockData getBlockData() { return data; }
        void setBlockData(BlockData value, boolean physics) {
            world.writes++;
            type = switch (value.value) {
                case "minecraft:barrier", "BARRIER" -> Material.BARRIER;
                case "minecraft:air", "AIR" -> Material.AIR;
                case "minecraft:diamond_block" -> Material.DIAMOND_BLOCK;
                default -> Material.STONE;
            };
            data = value;
        }
    }
    static final class World {
        final Map<String, Block> blocks = new HashMap<>();
        int writes;
        boolean loaded = true, occupied;
        int getMinHeight() { return -64; }
        int getMaxHeight() { return 320; }
        boolean isChunkLoaded(int x, int z) { return loaded; }
        WorldBorder getWorldBorder() { return new WorldBorder(); }
        List<Entity> getNearbyEntities(BoundingBox bounds, java.util.function.Predicate<Entity> filter) {
            return occupied ? List.of(new LivingEntity()).stream().map(e -> (Entity)e).filter(filter).toList() : List.of();
        }
        Block getBlockAt(int x, int y, int z) {
            return blocks.computeIfAbsent(x+":"+y+":"+z, k -> new Block(this, x, y, z));
        }
        String getName() { return "world"; }
        UUID getUID() { return UUID.fromString("00000000-0000-0000-0000-0000000000aa"); }
    }
    record BoundingBox(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) { }
    static class WorldBorder { boolean isInside(Location at) { return true; } }
    static class Entity { boolean isValid() { return true; } boolean isDead() { return false; } }
    static class LivingEntity extends Entity { }
    static final class Location {
        World world; int bx, by, bz; double x, y, z;
        Location(World w, double x, double y, double z) { this.world=w; this.x=x; this.y=y; this.z=z;
            bx=(int)Math.floor(x); by=(int)Math.floor(y); bz=(int)Math.floor(z); }
        World getWorld() { return world; }
        double getX() { return x; } double getY() { return y; } double getZ() { return z; }
        int getBlockX() { return bx; } int getBlockY() { return by; } int getBlockZ() { return bz; }
        public Location clone() { return new Location(world,x,y,z); }
    }
    static final class Bukkit {
        static World world;
        static World getWorld(String name) { return world; }
        static BlockData createBlockData(String value) { return new BlockData(value); }
    }
    static final class HazardMutationJournal {
        enum Status { EMPTY, PREPARED, APPLIED, RESTORED }
        static final class Entry {
            final int x, floorY, z; final String original, mutation; final long generation;
            Entry(int x, int floorY, int z, String original, String web, String mutation,
                  String world, String eventId, long generation) {
                this.x=x; this.floorY=floorY; this.z=z; this.original=original;
                this.mutation=mutation; this.generation=generation; }
            boolean isRiftPillarMutation() { return "RIFT_PILLAR".equals(mutation); }
        }
        static final class Snapshot {
            final boolean valid; final Status status; final List<Entry> entries;
            Snapshot(boolean valid, Status status, List<Entry> entries) { this.valid=valid; this.status=status; this.entries=entries; }
            boolean valid() { return valid; } Status status() { return status; } List<Entry> entries() { return entries; }
        }
        Snapshot snap = new Snapshot(true, Status.EMPTY, List.of());
        boolean prepareSucceeds = true, markAppliedSucceeds = true, markRestoredSucceeds = true;
        int prepares, applies, restores;
        Snapshot load() { return snap; }
        boolean prepare(String eventId, long generation, String world, List<Entry> entries) {
            prepares++;
            if (!prepareSucceeds) return false;
            snap = new Snapshot(true, Status.PREPARED, List.copyOf(entries));
            return true;
        }
        boolean markApplied() {
            applies++;
            if (!markAppliedSucceeds) return false;
            snap = new Snapshot(true, Status.APPLIED, snap.entries());
            return true;
        }
        boolean markRestored() {
            restores++;
            if (!markRestoredSucceeds) return false;
            snap = new Snapshot(true, Status.RESTORED, List.of());
            return true;
        }
    }

    // Production fields mirrored by the probe.
    static final Material REALITY_SPLIT_WALL_MATERIAL = Material.BARRIER;
    String worldName = "world";
    String eventId = "event";
    long generation = 4L;
    HazardMutationJournal hazardJournal = new HazardMutationJournal();
    final Map<String, String> riftRamPillarColliderOriginals = new LinkedHashMap<>();
    boolean riftRamPillarColliderActive;
    Logger getLogger() { return Logger.getAnonymousLogger(); }
    void restoreBlock(Block block, String original) {
        block.world.writes++;
        String value = original == null ? "minecraft:stone" : original;
        block.setBlockData(new BlockData(value), false);
    }
''' + "\n" + "{METHODS}" + r'''
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }
    public static void main(String[] args) {
        switch (args[0]) {
            case "place-and-clear" -> {
                var p = new RamPillarColliderProbe();
                var world = new World(); Bukkit.world = world;
                Location centre = new Location(world, 10.0D, 68.0D, 0.0D);
                check(p.placeRiftRamPillarCollider("e", 4L, centre, 1, 4), "collider refused on open ground");
                // 2x2 footprint (halfWidth 1) over 4 blocks high.
                check(p.riftRamPillarColliderOriginals.size() == 4 * 4, "journaled cell count, was "
                        + p.riftRamPillarColliderOriginals.size());
                check(p.hazardJournal.prepares == 1 && p.hazardJournal.applies == 1, "journal not prepared/applied");
                for (var e : p.hazardJournal.snap.entries()) check(e.isRiftPillarMutation(), "wrong mutation type");
                int barriers = 0;
                for (var b : world.blocks.values()) if (b.getType() == Material.BARRIER) barriers++;
                check(barriers == 4 * 4, "barrier volume not written, was " + barriers);
                check(p.clearRiftRamPillarCollider("test"), "clear refused");
                check(p.hazardJournal.restores == 1, "clear did not mark the journal restored");
                for (var b : world.blocks.values()) check(b.getType() == Material.AIR, "cell left mutated: " + b.getType());
                check(!p.riftRamPillarColliderActive && p.riftRamPillarColliderOriginals.isEmpty(), "clear left state behind");
            }
            case "busy-journal-refused" -> {
                var p = new RamPillarColliderProbe();
                var world = new World(); Bukkit.world = world;
                p.hazardJournal.snap = new HazardMutationJournal.Snapshot(true,
                        HazardMutationJournal.Status.APPLIED, List.of());
                check(!p.placeRiftRamPillarCollider("e", 4L, new Location(world, 10, 68, 0), 1, 4),
                        "a busy hazard journal must refuse the collider");
                check(p.hazardJournal.prepares == 0 && world.writes == 0, "refusal must precede journal/world writes");
            }
            case "unsafe-cell-refused" -> {
                var p = new RamPillarColliderProbe();
                var world = new World(); Bukkit.world = world;
                // The volume for centre (10,68,0) with halfWidth 1 spans x 9..10,
                // z -1..0, y 68..71, so this cell is inside the footprint.
                world.getBlockAt(10, 69, 0).type = Material.DIAMOND_BLOCK;
                check(!p.placeRiftRamPillarCollider("e", 4L, new Location(world, 10, 68, 0), 1, 4),
                        "solid terrain must refuse the collider");
                check(p.hazardJournal.prepares == 0, "refusal must precede the journal");
                check(world.writes == 0, "refusal must not write any block");
            }
            case "container-refused" -> {
                var p = new RamPillarColliderProbe();
                var world = new World(); Bukkit.world = world;
                world.getBlockAt(10, 68, 0).tile = true;
                check(!p.placeRiftRamPillarCollider("e", 4L, new Location(world, 10, 68, 0), 1, 4),
                        "a container floor must refuse the collider");
                check(world.writes == 0, "container refusal must not write any block");
            }
            case "apply-failure-rolls-back" -> {
                var p = new RamPillarColliderProbe();
                var world = new World(); Bukkit.world = world;
                p.hazardJournal.markAppliedSucceeds = false;
                check(!p.placeRiftRamPillarCollider("e", 4L, new Location(world, 10, 68, 0), 1, 4),
                        "a failed journal commit must refuse the collider");
                check(p.riftRamPillarColliderOriginals.isEmpty() && !p.riftRamPillarColliderActive,
                        "a failed commit must not retain collider state");
                for (var b : world.blocks.values()) check(b.getType() != Material.BARRIER, "rollback left a barrier");
                check(p.hazardJournal.restores == 1, "rollback must restore the journal");
            }
            case "foreign-edit-preserved" -> {
                var p = new RamPillarColliderProbe();
                var world = new World(); Bukkit.world = world;
                check(p.placeRiftRamPillarCollider("e", 4L, new Location(world, 10, 68, 0), 1, 4), "place");
                // A player or another owner replaces one cell after the fact.
                world.getBlockAt(10, 68, 0).setBlockData(Material.DIAMOND_BLOCK.createBlockData(), false);
                check(!p.clearRiftRamPillarCollider("test"), "foreign edit must leave exact restoration unresolved");
                check(world.getBlockAt(10, 68, 0).getType() == Material.DIAMOND_BLOCK,
                        "clear must not overwrite a cell it no longer owns");
                check(p.riftRamPillarColliderActive && p.hazardJournal.restores == 0, "lost unresolved ownership");
            }
            case "unloaded-and-commit-retry" -> {
                var p = new RamPillarColliderProbe();
                var world = new World(); Bukkit.world = world;
                check(p.placeRiftRamPillarCollider("e", 4L, new Location(world, 10, 68, 0), 2, 7), "place");
                world.loaded = false;
                check(!p.clearRiftRamPillarCollider("unloaded"), "unloaded chunk claimed restored");
                world.loaded = true;
                p.hazardJournal.markRestoredSucceeds = false;
                check(!p.clearRiftRamPillarCollider("disk-error"), "failed disk commit claimed restored");
                check(p.riftRamPillarColliderActive && p.riftRamPillarColliderOriginals.size() == 112, "lost ownership");
                p.hazardJournal.markRestoredSucceeds = true;
                check(p.clearRiftRamPillarCollider("retry"), "already-correct cells should reconcile on retry");
                check(!p.riftRamPillarColliderActive && p.riftRamPillarColliderOriginals.isEmpty(), "retry left ownership");
            }
            case "occupied-tower-refused" -> {
                var p = new RamPillarColliderProbe();
                var world = new World(); Bukkit.world = world;
                world.occupied = true;
                check(!p.placeRiftRamPillarCollider("e", 4L, new Location(world, 10, 68, 0), 2, 7), "occupied collider");
                check(p.hazardJournal.prepares == 0 && world.writes == 0, "occupied volume was mutated");
            }
            case "rebuild-supersedes" -> {
                var p = new RamPillarColliderProbe();
                var world = new World(); Bukkit.world = world;
                check(p.placeRiftRamPillarCollider("e", 4L, new Location(world, 10, 68, 0), 1, 4), "first");
                check(p.placeRiftRamPillarCollider("e", 4L, new Location(world, 20, 68, 0), 2, 5), "rebuild");
                // halfWidth 2 => a 4x4 footprint (two cells either side of the
                // centre), 5 blocks tall.
                check(p.riftRamPillarColliderOriginals.size() == 4 * 4 * 5,
                        "rebuild did not journal the new volume, was "
                        + p.riftRamPillarColliderOriginals.size());
                check(p.hazardJournal.restores >= 1, "rebuild must restore the superseded collider");
            }
            case "corrupt-input-refused" -> {
                var p = new RamPillarColliderProbe();
                var world = new World(); Bukkit.world = world;
                check(!p.placeRiftRamPillarCollider("e", 4L, new Location(world, 10, 68, 0), 0, 4), "zero half-width");
                check(!p.placeRiftRamPillarCollider("e", 4L, new Location(world, 10, 68, 0), 1, 0), "zero height");
                check(p.hazardJournal.prepares == 0 && world.writes == 0, "degenerate input must be refused first");
            }
            case "final-pillar-collider-follows-cracks" -> {
                // An ordinary pillar keeps a 2x2 footprint and reaches its crown.
                check(RiftRamPolicy.pillarColliderHalfWidth(0) == 1, "ordinary half width");
                check(RiftRamPolicy.pillarColliderHeight(0) == 4, "ordinary height");
                // The final pillar is wider, and its collider tracks the visible
                // silhouette through the two crack stages instead of keeping the
                // original seven blocks while the model shrinks.
                int first = RiftRamPolicy.MINOR_PILLARS;
                check(RiftRamPolicy.pillarColliderHalfWidth(first) == 2, "final half width");
                double prevPlatform = Double.MAX_VALUE;
                int prevHeight = Integer.MAX_VALUE;
                for (int impact = first; impact <= first + 2; impact++) {
                    int height = RiftRamPolicy.pillarColliderHeight(impact);
                    double platform = RiftRamPolicy.pillarPlatformHeight(impact);
                    double silhouette = RiftRamPolicy.pillarSilhouetteHeight(impact);
                    check(height == (int) Math.floor(silhouette),
                            "collider height must equal the floored silhouette at impact " + impact);
                    check(height < prevHeight, "the collider must shrink at every crack, impact " + impact);
                    check(platform < prevPlatform, "the platform must drop with the pillar, impact " + impact);
                    // The topmost collider block is base+height-1 and the marksman
                    // stands in block floor(platform): a collider that reached it
                    // would encase the elite on its own pillar.
                    check(Math.floor(platform) >= height,
                            "collider reaches the marksman's feet at impact " + impact);
                    prevHeight = height;
                    prevPlatform = platform;
                }
                check(RiftRamPolicy.pillarColliderHeight(first) == 7, "fresh final pillar is seven blocks");
                check(RiftRamPolicy.pillarColliderHeight(first + 1) == 6, "first crack is six blocks");
                check(RiftRamPolicy.pillarColliderHeight(first + 2) == 5, "second crack is five blocks");
                // Nothing else changed for the ordinary pillars.
                check(RiftRamPolicy.pillarColliderHeight(RiftRamPolicy.MINOR_PILLARS - 1) == 4,
                        "last ordinary pillar height");
            }
            default -> throw new AssertionError("unknown " + args[0]);
        }
        System.out.println("OK");
    }
}
'''


def _build(tmp_path):
    source = SOURCE.read_text("utf-8")
    methods = "\n".join(_method(source, sig) for sig in (
        "private boolean placeRiftRamPillarCollider(",
        "private boolean clearRiftRamPillarCollider(",
        "private boolean restoreRiftRamColliderCell(",
    ))
    probe = tmp_path / "RamPillarColliderProbe.java"
    probe.write_text(PROBE.replace("{METHODS}", methods), encoding="utf-8")
    api = tmp_path / "org/bukkit/block/TileState.java"
    api.parent.mkdir(parents=True)
    api.write_text("package org.bukkit.block; public interface TileState {}", encoding="utf-8")
    domain = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
    built = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(domain / "RiftRamPolicy.java"), str(api), str(probe)],
        capture_output=True, text=True,
    )
    assert built.returncode == 0, built.stdout + built.stderr
    return tmp_path


import pytest


@pytest.fixture(scope="module")
def collider_probe(tmp_path_factory):
    return _build(tmp_path_factory.mktemp("ram-pillar-collider"))


@pytest.mark.parametrize("scenario", [
    "place-and-clear", "busy-journal-refused", "unsafe-cell-refused", "container-refused",
    "apply-failure-rolls-back", "foreign-edit-preserved", "rebuild-supersedes", "corrupt-input-refused",
    "final-pillar-collider-follows-cracks", "unloaded-and-commit-retry", "occupied-tower-refused",
])
def test_pillar_collider_is_solid_and_crash_safe(collider_probe, scenario):
    ran = subprocess.run(["java", "-cp", str(collider_probe), "RamPillarColliderProbe", scenario],
                         capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
