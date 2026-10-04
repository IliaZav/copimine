from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def test_runtime_checks_actual_accepted_wall_cells_before_journal_or_ready():
    source = (ROOT / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    body = source[source.index('private boolean spawnRealitySplitBarriers('):source.index('private boolean ensureRealitySplitChamberAssignment(')]
    assert 'RealitySplitBarrierCoveragePolicy.missing(' in body
    assert body.index('RealitySplitBarrierCoveragePolicy.missing(') < body.index('hazardJournal.prepare(')
    assert 'incomplete-collision-coverage' in body


def test_actual_coverage_accepts_full_glass_and_refuses_partial_or_unplaced_cells(tmp_path):
    import subprocess

    source = (ROOT / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    start = source.index('            final int barrierCoreX = core.getBlockX();')
    coverage = source[start:source.index('            if (!uncovered.isEmpty())', start)]
    helper = ''
    signature = 'private boolean isRealitySplitExistingWallCoverage('
    if signature in source:
        offset = source.index(signature)
        brace = source.index('{', offset)
        depth = 1
        end = brace + 1
        while depth:
            depth += (source[end] == '{') - (source[end] == '}')
            end += 1
        helper = source[offset:end]
    fixture = tmp_path / 'BarrierCoverageProbe.java'
    fixture.write_text('''
import java.util.*;
import me.copimine.endevent.domain.RealitySplitBarrierPolicy;
public class BarrierCoverageProbe {
    record BoundingBox(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        double getMinX(){return minX;} double getMinY(){return minY;} double getMinZ(){return minZ;}
        double getMaxX(){return maxX;} double getMaxY(){return maxY;} double getMaxZ(){return maxZ;}
    }
    record Shape(List<BoundingBox> boxes){List<BoundingBox> getBoundingBoxes(){return boxes;}}
    record Material(boolean occluding){boolean isOccluding(){return occluding;}}
    record Block(boolean passable,boolean liquid,Material type,Shape shape){
        boolean isPassable(){return passable;} boolean isLiquid(){return liquid;}
        Material getType(){return type;} Shape getCollisionShape(){return shape;}
    }
    static class World { Block wall; Block getBlockAt(int x,int y,int z){return wall;} }
    record Location(World world,int x,int y,int z){int getBlockX(){return x;} int getBlockZ(){return z;}}
    boolean isRealitySplitBarrierPlacementLocation(Location location){return true;}
    Set<RealitySplitBarrierPolicy.Cell> check(World world, boolean scheduled){
        var core=new Location(world,13,68,-34); int floorY=68;
        var requiredCell=new RealitySplitBarrierPolicy.Cell(1,0,1);
        var plannedCells=List.of(requiredCell);
        Map<RealitySplitBarrierPolicy.Cell,String> plannedOriginals=new HashMap<>();
        if(scheduled)plannedOriginals.put(requiredCell,"minecraft:air");
''' + coverage + '''
        return uncovered;
    }
    static void require(boolean result,String message){if(!result)throw new AssertionError(message);}
    public static void main(String[] args){
        var probe=new BarrierCoverageProbe(); var world=new World();
        var full=new Shape(List.of(new BoundingBox(0,0,0,1,1,1)));
        var opaque=new Material(true); var transparent=new Material(false);
        world.wall=new Block(false,false,opaque,full);
        require(probe.check(world,false).isEmpty(),"existing opaque full block closes the wall");
        world.wall=new Block(true,false,transparent,new Shape(List.of()));
        require(probe.check(world,true).isEmpty(),"journal-planned barrier covers passable air");
        require(!probe.check(world,false).isEmpty(),"unplanned air, including protected gate/floor skips, must fail closed");
        world.wall=new Block(false,true,transparent,full);
        require(!probe.check(world,false).isEmpty(),"liquid cannot substitute for wall collision");
        world.wall=new Block(false,false,transparent,new Shape(List.of(new BoundingBox(0,0,0,1,.5,1))));
        require(!probe.check(world,false).isEmpty(),"slab leaves a wall gap");
        world.wall=new Block(false,false,transparent,new Shape(List.of(new BoundingBox(0,0,0,.1875,1,1))));
        require(!probe.check(world,false).isEmpty(),"open door remains non-passable but is not full containment");
        world.wall=new Block(false,false,transparent,new Shape(List.of(new BoundingBox(0,0,0,1,1,1))));
        require(probe.check(world,false).isEmpty(),"full glass must count as physical coverage despite being non-occluding");
    }
''' + helper + '\n}', encoding='utf-8')
    compiled = subprocess.run(['javac', '-J-Xmx96m', '-encoding', 'UTF-8', '-d', str(tmp_path), str(fixture),
                               str(ROOT / 'copimine-end-event/src/me/copimine/endevent/domain/RealitySplitBarrierPolicy.java'),
                               str(ROOT / 'copimine-end-event/src/me/copimine/endevent/domain/RealitySplitBarrierCoveragePolicy.java')],
                              capture_output=True, text=True, timeout=30)
    assert compiled.returncode == 0, compiled.stderr
    executed = subprocess.run(['java', '-Xms8m', '-Xmx96m', '-XX:+UseSerialGC', '-cp', str(tmp_path),
                               'BarrierCoverageProbe'], capture_output=True, text=True, timeout=30)
    assert executed.returncode == 0, executed.stdout + executed.stderr
