"""Run actual saved-actor adoption and pillar building before any respawn."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
RUNTIME = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java"


@pytest.fixture(scope="module")
def restore_probe(tmp_path_factory):
    folder = tmp_path_factory.mktemp("ram-skeleton-adoption")
    source = RUNTIME.read_text("utf-8").replace("    public ", "    private ")
    methods = "\n".join(extract(source, signature) for signature in (
        "void restore(Map<String,String> data)", "boolean buildPillar()",
    ))
    path = folder / "RamSkeletonRestoreProbe.java"
    path.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.*;
public class RamSkeletonRestoreProbe {
    enum Action {CHASE,CHARGE,CHARGE_WARNING,STUN,STOMP_WARNING,STOMP,RECOVER,DEAD}
    enum Material {OBSIDIAN,CRYING_OBSIDIAN,MAGMA_BLOCK}
    enum Sound {BLOCK_DEEPSLATE_PLACE}
    record Vector3f(float x,float y,float z){}
    static class World {void playSound(Location l,Sound s,float a,float b){}}
    static final World WORLD=new World();
    static class Location {
        double x,y,z;World world;
        Location(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}
        public Location clone(){return new Location(world,x,y,z);}
        Location add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}
        World getWorld(){return world;}void setY(double value){y=value;}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
    }
    static class Entity {
        UUID id=UUID.randomUUID();boolean valid=true,dead,owned=true;Location at=new Location(WORLD,10,69,0);
        UUID getUniqueId(){return id;}boolean isValid(){return valid;}boolean isDead(){return dead;}
        Location getLocation(){return at;}World getWorld(){return at.world;}
    }
    static class Ravager extends Entity {}
    static class Skeleton extends Entity {void setGravity(boolean b){}void setAI(boolean b){}void setAware(boolean b){}}
    static class BlockDisplay extends Entity {}
    static class Bukkit {static Map<UUID,Entity> actors=new HashMap<>();static Entity getEntity(UUID id){return actors.get(id);}}
    static class Host {
        boolean owns(Entity e){return e.owned;}boolean inArena(Location l){return l.y>=65&&l.y<=71&&Math.abs(l.x)<=30&&Math.abs(l.z)<=30;}
        int floorY(){return 68;}
        Location core(){return new Location(WORLD,0,69,0);}
        void visualsChanged(){}boolean checkpoint(){return true;}
    }
    final Host host=new Host();Ravager ram;UUID persistentId,pillarId,pillarSkeletonId;
    Skeleton pillarSkeleton;Location pillar;Action action;
    int impacts,tick,actionTick,lastCharge,lastStomp,nextPillarTick,pillarImpacts,newSkeletons,armourBuilds;
    int skeletonLastTriple=-1,skeletonLastBone=-1,skeletonLastMove=-1,skeletonStep;
    boolean completed,closed,skeletonFallen,pillarSkeletonSpawned,pillarSkeletonDefeated,ramDefeated;
    String failure;final List<BlockDisplay> pillarDisplays=new ArrayList<>();
    boolean safeVolume(Location l){return true;}
    void createArmor(){armourBuilds++;}
    void hold(){}
    void dropPillarSkeleton(){skeletonFallen=true;}
    void fail(String reason){failure=reason;closed=true;}
    BlockDisplay block(Location l,Material m,Vector3f s){return new BlockDisplay();}
    void clearPillar(){pillarDisplays.clear();pillar=null;pillarId=null;}
    void spawnPillarSkeleton(){
        newSkeletons++;pillarSkeleton=new Skeleton();pillarSkeletonId=pillarSkeleton.id;
        pillarSkeletonSpawned=true;Bukkit.actors.put(pillarSkeletonId,pillarSkeleton);
    }
    boolean skeletonAlive(){return pillarSkeleton!=null&&pillarSkeleton.valid&&!pillarSkeleton.dead;}
''' + methods + r'''
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        int impacts=Integer.parseInt(args[0]);String scenario=args[1];
        var p=new RamSkeletonRestoreProbe();var body=new Ravager();var saved=new Skeleton();
        Bukkit.actors.clear();Bukkit.actors.put(body.id,body);
        boolean dead=scenario.equals("dead"),missing=scenario.equals("missing");
        if(!missing&&!dead)Bukkit.actors.put(saved.id,saved);
        if(scenario.equals("platform"))saved.at.y=76.6;
        if(scenario.equals("outside"))saved.at.x=60;
        if(scenario.equals("sky"))saved.at.y=100;
        if(scenario.equals("foreign"))saved.owned=false;
        if(scenario.equals("wrong-kind")){var wrong=new Entity();wrong.id=saved.id;Bukkit.actors.put(saved.id,wrong);}
        if(scenario.equals("other-world"))saved.at.world=new World();
        Map<String,String> data=new HashMap<>();
        data.put("ram.schema",dead?"3":"2");data.put("ram.uuid",body.id.toString());
        data.put("ram.impacts",Integer.toString(impacts));data.put("ram.complete","false");
        data.put("ram.tick","80");data.put("ram.last-charge","40");data.put("ram.last-stomp","0");
        data.put("ram.pillar-impacts",Integer.toString(impacts<7?impacts-4:0));
        data.put("ram.skeleton",saved.id.toString());
        data.put("ram.skeleton-spawned","true");data.put("ram.skeleton-defeated",Boolean.toString(dead));
        data.put("ram.skeleton-fallen",Boolean.toString(impacts==7));data.put("ram.defeated","false");
        if(impacts<7){data.put("ram.pillar-id",UUID.randomUUID().toString());data.put("ram.pillar","10,69,0");}
        if(scenario.equals("bad-local"))data.put("ram.pillar-impacts","oops");
        if(scenario.equals("wrong-local"))data.put("ram.pillar-impacts","3");
        p.restore(data);
        check(p.newSkeletons==0,"restoring a saved guard spawned an untracked duplicate: "+scenario+" at "+impacts);
        if(missing||scenario.equals("outside")||scenario.equals("sky")||scenario.equals("bad-local")||scenario.equals("wrong-local")||scenario.equals("foreign")||scenario.equals("wrong-kind")||scenario.equals("other-world")){
            check(p.failure!=null,"invalid saved guard accepted: "+scenario);
            check(p.armourBuilds==0&&p.pillarDisplays.isEmpty(),"invalid guard discovered after mutation");
        }else{
            check(p.failure==null,"valid saved guard refused: "+p.failure);
            if(dead)check(p.pillarSkeletonDefeated&&!p.skeletonAlive(),"dead guard was resurrected");
            else check(p.pillarSkeleton==saved&&p.pillarSkeletonId.equals(saved.id),"saved guard was not adopted");
            check(p.skeletonFallen==(impacts==7),"saved fall state was lost");
            if(impacts<7)check(p.pillarDisplays.size()==4,"final pillar was not rebuilt");
        }
    }
}
''', encoding="utf-8")
    built = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(DOMAIN.parents[3]),
                            "-d", str(folder), str(DOMAIN / "RiftRamPolicy.java"), str(path)],
                           capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    return folder


@pytest.mark.parametrize("impacts", [4, 5, 6, 7])
@pytest.mark.parametrize("scenario", ["alive", "dead", "missing", "foreign", "wrong-kind", "other-world", "platform", "outside", "sky", "bad-local", "wrong-local"])
def test_saved_guard_is_resolved_before_building(restore_probe, impacts, scenario):
    ran = subprocess.run(["java", "-cp", str(restore_probe), "RamSkeletonRestoreProbe", str(impacts), scenario],
                         capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
