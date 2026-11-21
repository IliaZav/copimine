"""Execute the Wave 6 pillar site admission on the real collider dimensions.

The old ``safeVolume`` probed a 3x3x4 box for every pillar, so the final
monument (four blocks wide and about seven tall) could be admitted into an
obstacle, a player or an unloaded chunk. The probe runs the production method
with the production ``RiftRamPolicy`` dimensions and a minimal world fake.
"""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java"

PROBE = r'''
import java.util.*;
import me.copimine.endevent.domain.RiftRamPolicy;

public final class PillarSiteAdmissionProbe {
    enum Material {
        AIR, STONE, WATER, CHEST;
        boolean isSolid() { return this == STONE || this == CHEST; }
        boolean isLiquid() { return this == WATER; }
    }
    static class BlockState { }
    static final class TileState extends BlockState { }
    static final class Block {
        Material type = Material.AIR; boolean tile;
        Material getType() { return type; }
        boolean isLiquid() { return type.isLiquid(); }
        boolean isPassable() { return type == Material.AIR; }
        Object getState() { return tile ? new TileState() : null; }
    }
    static final class World {
        final Map<String, Block> blocks = new HashMap<>();
        final Set<String> unloaded = new HashSet<>();
        boolean isChunkLoaded(int cx, int cz) { return !unloaded.contains(cx + ":" + cz); }
        Block getBlockAt(int x, int y, int z) {
            String key = x + ":" + y + ":" + z;
            Block block = blocks.get(key);
            if (block == null) {
                block = new Block();
                // The probe's arena floor sits in the block below the pillar
                // base; everything above it starts as open air.
                block.type = y == 67 ? Material.STONE : Material.AIR;
                blocks.put(key, block);
            }
            return block;
        }
        Block at(int x, int y, int z) { return getBlockAt(x, y, z); }
    }
    static final class Location {
        final World world; final double x, y, z;
        Location(World w, double x, double y, double z) { world = w; this.x = x; this.y = y; this.z = z; }
        World getWorld() { return world; }
        double getX() { return x; }
        double getY() { return y; }
        double getZ() { return z; }
        int getBlockY() { return (int) Math.floor(y); }
    }
    static final class Host {
        boolean occupied;
        boolean inArena(Location at) { return true; }
        boolean volumeFree(Location centre, int halfWidth, int height) { return !occupied; }
    }
    final Host host = new Host();
{METHOD}
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    boolean admit(World world, int impacts) {
        return pillarSiteSafe(new Location(world, 10.0D, 68.0D, 0.0D), impacts);
    }

    public static void main(String[] args) {
        // Dimensions the collider will really journal.
        check(RiftRamPolicy.pillarColliderHalfWidth(0) == 1, "ordinary half width");
        check(RiftRamPolicy.pillarColliderHalfWidth(4) == 2, "final half width");
        check(RiftRamPolicy.pillarColliderHeight(4) == 7, "final height");

        // Open ground is admitted for both.
        var ordinary = new PillarSiteAdmissionProbe();
        check(ordinary.admit(new World(), 0), "open ordinary site");
        var finale = new PillarSiteAdmissionProbe();
        check(finale.admit(new World(), 4), "open final site");

        // The final pillar is wider: a block at x=8 is outside the ordinary
        // 3x3 probe but inside the real 4-wide collider.
        var wide = new PillarSiteAdmissionProbe();
        World wideWorld = new World();
        wideWorld.at(8, 70, 0).type = Material.STONE;
        check(!wide.admit(wideWorld, 4), "final footprint must reject a cell at x=8");
        check(wide.admit(new World(), 0), "same x is outside the ordinary footprint");

        // The final pillar is taller: a block above the ordinary four-block
        // probe must still be refused.
        var tall = new PillarSiteAdmissionProbe();
        World tallWorld = new World();
        tallWorld.at(10, 74, 0).type = Material.STONE;
        check(!tall.admit(tallWorld, 4), "final height must reject a cell at the crown");
        check(tall.admit(new World(), 0), "the same height is outside an ordinary pillar");

        // Every column must be in a loaded chunk.
        var chunks = new PillarSiteAdmissionProbe();
        World chunkWorld = new World();
        chunkWorld.unloaded.add((8 >> 4) + ":" + (-2 >> 4));
        check(!chunks.admit(chunkWorld, 4), "unloaded corner chunk must refuse the whole site");

        // Terrain, liquid, container floors and covered cells refuse atomically.
        var noFloor = new PillarSiteAdmissionProbe();
        World airFloor = new World();
        airFloor.at(10, 67, 0).type = Material.AIR;
        check(!noFloor.admit(airFloor, 4), "an air floor must refuse");

        var liquid = new PillarSiteAdmissionProbe();
        World liquidWorld = new World();
        liquidWorld.at(10, 67, 0).type = Material.WATER;
        check(!liquid.admit(liquidWorld, 4), "a liquid floor must refuse");

        var container = new PillarSiteAdmissionProbe();
        World containerWorld = new World();
        containerWorld.at(10, 67, 0).tile = true;
        check(!container.admit(containerWorld, 4), "a container floor must refuse");

        var covered = new PillarSiteAdmissionProbe();
        World coveredWorld = new World();
        coveredWorld.at(11, 73, 1).type = Material.STONE;
        check(!covered.admit(coveredWorld, 4), "a covered cap cell must refuse");

        // A body occupying the volume refuses atomically even when blocks pass.
        var occupied = new PillarSiteAdmissionProbe();
        occupied.host.occupied = true;
        check(!occupied.admit(new World(), 4), "an occupied volume must refuse");

        System.out.println("OK");
    }
}
'''


def test_pillar_site_admission_uses_the_real_full_volume(tmp_path):
    source = RUNTIME.read_text("utf-8")
    method = extract(source, "boolean pillarSiteSafe(Location at, int acceptedImpacts)")
    probe = tmp_path / "PillarSiteAdmissionProbe.java"
    probe.write_text(PROBE.replace("{METHOD}", method), encoding="utf-8")
    domain = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
    built = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(domain / "RiftRamPolicy.java"), str(probe)],
        capture_output=True, text=True,
    )
    assert built.returncode == 0, built.stdout + built.stderr
    ran = subprocess.run(
        ["java", "-cp", str(tmp_path), "PillarSiteAdmissionProbe"],
        capture_output=True, text=True,
    )
    assert ran.returncode == 0, ran.stdout + ran.stderr