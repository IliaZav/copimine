"""Run the Bukkit entrance adapter against bounded synthetic collision shapes."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_entrance_requires_loaded_safe_floor_and_actual_clearance(tmp_path):
    resolver = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/ArenaEntranceResolver.java"
    assert resolver.exists(), "Arena-relative geometry has no physical floor/collision adapter"
    sources = {
        "org/bukkit/Material.java": "package org.bukkit; public enum Material { STONE,AIR,MAGMA_BLOCK,WATER,SLAB }",
        "org/bukkit/Location.java": "package org.bukkit; public record Location(World world,double x,double y,double z,float yaw,float pitch) {public World getWorld(){return world;} public double getX(){return x;} public double getY(){return y;} public double getZ(){return z;}public int getBlockX(){return (int)Math.floor(x);} public int getBlockY(){return (int)Math.floor(y);}public int getBlockZ(){return (int)Math.floor(z);}public float getYaw(){return yaw;}}",
        "org/bukkit/World.java": "package org.bukkit; import org.bukkit.block.Block; public interface World {String getName(); int getMinHeight();int getMaxHeight();boolean isChunkLoaded(int x,int z);WorldBorder getWorldBorder();Block getBlockAt(int x,int y,int z);}",
        "org/bukkit/WorldBorder.java": "package org.bukkit; public interface WorldBorder {boolean isInside(Location point);}",
        "org/bukkit/block/data/BlockData.java": "package org.bukkit.block.data; public interface BlockData {}",
        "org/bukkit/block/data/Waterlogged.java": "package org.bukkit.block.data; public interface Waterlogged extends BlockData {boolean isWaterlogged();}",
        "org/bukkit/block/Block.java": "package org.bukkit.block; import org.bukkit.*;import org.bukkit.util.*;import org.bukkit.block.data.*;public interface Block {Material getType();boolean isLiquid();BlockData getBlockData();VoxelShape getCollisionShape();}",
        "org/bukkit/util/VoxelShape.java": "package org.bukkit.util; import java.util.*;public interface VoxelShape {Collection<BoundingBox> getBoundingBoxes();boolean overlaps(BoundingBox box);}",
        "org/bukkit/util/BoundingBox.java": "package org.bukkit.util; public record BoundingBox(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {public double getMinX(){return minX;} public double getMinY(){return minY;}public double getMinZ(){return minZ;} public double getMaxX(){return maxX;} public double getMaxY(){return maxY;}public double getMaxZ(){return maxZ;} public boolean overlaps(BoundingBox b){return maxX>b.minX&&minX<b.maxX&&maxY>b.minY&&minY<b.maxY&&maxZ>b.minZ&&minZ<b.maxZ;}}",
    }
    for relative, source in sources.items():
        target = tmp_path / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source, encoding="utf-8")
    probe = tmp_path / "EntranceResolverProbe.java"
    probe.write_text(r'''
import java.util.*;import org.bukkit.*;import org.bukkit.block.*;import org.bukkit.block.data.*;import org.bukkit.util.*;
import me.copimine.endevent.domain.ArenaEntranceLocator.*;
import me.copimine.endevent.runtime.ArenaEntranceResolver;
public class EntranceResolverProbe {
    record Shape(List<BoundingBox> boxes) implements VoxelShape {
        public Collection<BoundingBox> getBoundingBoxes(){return boxes;}
        public boolean overlaps(BoundingBox b){return boxes.stream().anyMatch(box->box.overlaps(b));}
    }
    record B(Material type,Shape shape,boolean waterlogged) implements Block {
        public Material getType(){return type;}public boolean isLiquid(){return type==Material.WATER;}
        public BlockData getBlockData(){return (Waterlogged)()->waterlogged;}public VoxelShape getCollisionShape(){return shape;}
    }
    static final Shape FULL=new Shape(List.of(new BoundingBox(0,0,0,1,1,1))), EMPTY=new Shape(List.of());
    static class W implements World {
        int reads;boolean loaded=true,border=true,roof,hazard,waterlogged,slab;
        public String getName(){return "configured-arena";}public int getMinHeight(){return -64;}public int getMaxHeight(){return 320;}
        public boolean isChunkLoaded(int x,int z){return loaded;} public WorldBorder getWorldBorder(){return point->border;}
        public Block getBlockAt(int x,int y,int z){reads++;if(y==64)return new B(hazard?Material.MAGMA_BLOCK:Material.STONE,slab?new Shape(List.of(new BoundingBox(0,0,0,1,.5,1))):FULL,waterlogged);
            return new B(roof&&y==66?Material.STONE:Material.AIR,roof&&y==66?FULL:EMPTY,false);}
    }
    static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        var bounds=new Bounds("configured-arena",-20,62,-40,20,67,0);var world=new W();
        var found=ArenaEntranceResolver.find(world,bounds,65,block->false).orElseThrow();
        require(found.getWorld()==world&&found.getY()==65,"actual configured world and solid floor must be used");
        require(ArenaEntranceResolver.safe(found,block->false),"exact resolved site can be revalidated before committing return");
        world.roof=true;require(!ArenaEntranceResolver.safe(found,block->false),"a newly obstructed staging site is refused rather than selecting another entrance");world.roof=false;
        world.loaded=false;world.reads=0;
        require(ArenaEntranceResolver.find(world,bounds,65,block->false).isEmpty()&&world.reads==0,"return probing must not load chunks");world.loaded=true;
        world.border=false;world.reads=0;
        require(ArenaEntranceResolver.find(world,bounds,65,block->false).isEmpty()&&world.reads==0,"world border rejection precedes block reads");world.border=true;
        world.roof=true;var lowCeilingBounds=new Bounds("configured-arena",-20,62,-40,20,65,0);
        require(ArenaEntranceResolver.find(world,lowCeilingBounds,65,block->false).isEmpty(),"full standing-player height must fit; feet-only clearance is insufficient");world.roof=false;
        world.slab=true;require(ArenaEntranceResolver.find(world,bounds,65,block->false).isEmpty(),"integer feet position cannot float above a half slab");world.slab=false;
        world.hazard=true;require(ArenaEntranceResolver.find(world,bounds,65,block->false).isEmpty(),"solid magma is not a safe return floor");world.hazard=false;
        world.waterlogged=true;require(ArenaEntranceResolver.find(world,bounds,65,block->false).isEmpty(),"waterlogged floor is excluded");world.waterlogged=false;
        int before=world.reads;
        require(ArenaEntranceResolver.find(world,bounds,65,block->block.getType()==Material.STONE).isEmpty(),"event-owned temporary danger is checked through the caller predicate");
        require(world.reads-before<=540,"at most three block reads for each of 180 local candidates");
        require(ArenaEntranceResolver.find(world,new Bounds("foreign-world",-20,62,-40,20,67,0),65,block->false).isEmpty(),"a matching coordinate in another world cannot be admitted");
    }
}
''', encoding="utf-8")
    locator = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/ArenaEntranceLocator.java"
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(locator), str(resolver), *map(str, tmp_path.rglob("*.java"))], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "EntranceResolverProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
