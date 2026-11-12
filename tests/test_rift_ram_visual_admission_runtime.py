"""Execute actual Ram armour/pillar builders and the tick's visual admission.

Paper's display creation is detached; geometry and section selection are the
production methods, including their actual 3-vs-4 pillar count.
"""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
RUNTIME = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java"


@pytest.fixture(scope="module")
def visual_probe(tmp_path_factory):
    folder = tmp_path_factory.mktemp("ram-actual-visual-builders")
    source = RUNTIME.read_text("utf-8")
    gate = extract(source, "void advance()")
    # Execute the complete production admission before independent AI decisions.
    gate = gate[:gate.index("        List<Player> players=")] + "\n    }"
    methods = "\n".join(extract(source, signature) for signature in (
        "void createArmor()", "void addArmor(", "boolean buildPillar()",
    )) + gate
    probe = folder / "RamVisualAdmissionProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.RiftRamPolicy;
public class RamVisualAdmissionProbe {
    enum Material {NETHERITE_BLOCK,OBSIDIAN,CRYING_OBSIDIAN,MAGMA_BLOCK}
    enum Sound {BLOCK_DEEPSLATE_PLACE}
    record Vector(double x,double y,double z){}
    record Vector3f(float x,float y,float z){}
    record Piece(BlockDisplay display,Vector offset,Vector3f size,int section){}
    static class World {void playSound(Location l,Sound s,float v,float p){}}
    static class Location {
        World world;double x,y,z;
        Location(World w){world=w;}
        World getWorld(){return world;}
        public Location clone(){Location n=new Location(world);n.x=x;n.y=y;n.z=z;return n;}
        Location add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}
    }
    static class BlockDisplay {boolean valid=true;boolean isValid(){return valid;}}
    static class Ravager {Location at;Ravager(World w){at=new Location(w);}
        boolean isValid(){return true;}boolean isDead(){return false;}
        Location getLocation(){return at;}}
    static class Host {boolean inArena(Location l){return true;}List<Object> players(){return List.of();}}
    final Host host=new Host();final World world=new World();
    Ravager ram=new Ravager(world);int tick,impacts,pillarImpacts,nextPillarTick,skeletonSpawns;
    UUID pillarId=UUID.randomUUID();Location pillar=new Location(world);
    final List<Piece> armor=new ArrayList<>();final List<BlockDisplay> pillarDisplays=new ArrayList<>();
    boolean pillarSkeletonSpawned, pillarSkeletonDefeated, ramDefeated;
    void tickPillarSkeleton(List<Object> players){}
    String failure;
    boolean skeletonAlive(){return pillarSkeletonSpawned;}
    void fail(String reason){failure=reason;}
    BlockDisplay block(Location l,Material m,Vector3f size){return new BlockDisplay();}
    void clearPillar(){pillarDisplays.clear();pillar=null;pillarId=null;}
    void spawnPillarSkeleton(){skeletonSpawns++;pillarSkeletonSpawned=true;}
''' + methods + r'''
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        int hit=Integer.parseInt(args[0]);String mode=args[1];
        var p=new RamVisualAdmissionProbe();p.impacts=hit;p.createArmor();
        check(p.armor.size()==RiftRamPolicy.remainingPlates(hit),
                "actual armour builder has "+p.armor.size()+" plates, expected "+RiftRamPolicy.remainingPlates(hit));
        Set<Integer> sections=new HashSet<>();
        for(Piece piece:p.armor) check(piece.section()>hit && sections.add(piece.section()),"duplicate/already-shed plate");
        if(hit==7){p.pillarId=null;p.pillar=null;}
        else {
            check(p.buildPillar(),"pillar builder failed");
            check(p.pillarDisplays.size()==(hit<4?3:4),"actual pillar geometry differs from its type");
            check(p.skeletonSpawns==(hit<4?0:1),"marksman appears on wrong pillar");
        }
        if(mode.equals("missing") && !p.pillarDisplays.isEmpty())p.pillarDisplays.removeLast();
        if(mode.equals("invalid") && !p.pillarDisplays.isEmpty())p.pillarDisplays.getFirst().valid=false;
        p.advance();
        if(mode.equals("ok"))check(p.failure==null,"valid pillar rejected after impact "+hit+": "+p.failure);
        else check("active-pillar-visual-missing".equals(p.failure),"broken pillar was admitted");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(folder),
        str(DOMAIN / "RiftRamPolicy.java"), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return folder


@pytest.mark.parametrize("impacts", range(8))
def test_actual_builders_survive_every_armour_and_pillar_stage(visual_probe, impacts):
    result = subprocess.run(["java", "-cp", str(visual_probe), "RamVisualAdmissionProbe", str(impacts), "ok"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("impacts", [0, 4, 5, 6])
@pytest.mark.parametrize("mode", ["missing", "invalid"])
def test_incomplete_actual_geometry_still_fails_closed(visual_probe, impacts, mode):
    result = subprocess.run(["java", "-cp", str(visual_probe), "RamVisualAdmissionProbe", str(impacts), mode],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
