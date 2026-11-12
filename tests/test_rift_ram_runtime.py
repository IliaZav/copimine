"""Run production impact/death/cleanup methods with controlled Paper side effects."""
import subprocess
from pathlib import Path
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT/'copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java'


def test_pillar_receipts_bounded_adds_and_real_death(tmp_path):
    source = RUNTIME.read_text(encoding='utf-8').replace('    public ', '    private ')
    methods = '\n'.join(extract(source, signature) for signature in [
        'void impact()', 'void died(UUID entity)', 'void clearPillar()',
        'void cleanupDisplays()', 'void close()', 'void fail(String reason)',
        'void begin(Action next)',
        'Map<String,String> snapshot()', 'void restore(Map<String,String> data)',
    ])
    probe = r'''
import java.util.*;
import me.copimine.endevent.domain.RiftRamPolicy;
public class RamRuntimeProbe {
  enum Action { CHARGE, CHASE, STUN, DEAD, RECOVER, STOMP, STOMP_WARNING, CHARGE_WARNING }
  enum Sound { BLOCK_DEEPSLATE_BREAK, BLOCK_ANVIL_BREAK, BLOCK_DEEPSLATE_HIT, ENTITY_IRON_GOLEM_HURT }
  enum Material { NETHERITE_BLOCK }
  static class Entity { boolean valid=true; boolean isValid(){return valid;} }
  static class BlockDisplay extends Entity {}
  static class Vector { double x,z; Vector(double x,double y,double z){this.x=x;this.z=z;} double getX(){return x;} double getZ(){return z;} }
  static class Vector3f { Vector3f(float x,float y,float z){} }
  static class World {
    int spawned;
    void playSound(Location l,Sound s,float v,float p){}
    Endermite spawn(Location at,Class<Endermite> type){spawned++;return new Endermite();}
  }
  static class Location {
    World world; Location(World w){world=w;}
    Location(World w,double x,double y,double z){world=w;}
    public Location clone(){return new Location(world);}
    Location add(double x,double y,double z){return this;}
    World getWorld(){return world;}
    double getX(){return 1;}double getY(){return 65;}double getZ(){return 1;}
  }
  static class Ravager extends Entity {
    UUID uuid=UUID.randomUUID(); Location at;
    Ravager(World world){at=new Location(world);}
    Location getLocation(){return at;}
    World getWorld(){return at.world;}boolean isDead(){return !valid;}
  }
  static class Endermite extends Entity {
    void setPersistent(boolean b){} void setRemoveWhenFarAway(boolean b){} void setTarget(Object p){}
  }
  static class Skeleton extends Entity {
    UUID skeletonId=UUID.randomUUID(); boolean skeletonValid=true;
    Skeleton(World world){new Location(world);}
    UUID getUniqueId(){return skeletonId;} boolean isValid(){return skeletonValid;}
    boolean isDead(){return !skeletonValid;}
    void setPersistent(boolean b){} void setRemoveWhenFarAway(boolean b){}
    void setRotation(float yaw,float pitch){} void setGravity(boolean g){}
  }
  record Piece(BlockDisplay display,int section){}
  record Debris(BlockDisplay display,Location start,Vector velocity,int born){}
  static class Host {
    int removed,changes,failures;
    boolean storageHealthy=true;
    void remove(Entity e){e.valid=false;removed++;}
    void own(Entity e,boolean combat){}
    List<Object> players(){return List.of();}
    void visualsChanged(){changes++;}
    boolean checkpoint(){return storageHealthy;}
    boolean owns(Entity entity){return true;} boolean inArena(Location at){return true;}
    void fail(String s){failures++;}
  }
  final Host host=new Host(); final World world=new World();
  Ravager ram=new Ravager(world); Action action=Action.CHARGE;
  int tick,actionTick,impacts,pillarImpacts,lastCharge,lastStomp,nextPillarTick;
  void crackPillar(){}
  Skeleton pillarSkeleton; UUID pillarSkeletonId; boolean skeletonFallen;
  int skeletonLastTriple=-1,skeletonLastBone=-1,skeletonLastMove=-1,skeletonStep;
  void dropPillarSkeleton(){}
  boolean closed,completed,banked;
  UUID pillarId,persistentId,target; Location pillar;Vector direction;
  List<BlockDisplay> pillarDisplays=new ArrayList<>(); List<Piece> armor=new ArrayList<>();
  List<Debris> debris=new ArrayList<>(); Set<Endermite> mites=new HashSet<>();
  Set<UUID> stompHits=new HashSet<>(),stompRoster=new HashSet<>(),chargeHits=new HashSet<>();
  UUID id(){return ram==null?persistentId:ram.uuid;}
  static class Bukkit { static Entity loaded; static Entity getEntity(UUID id){return loaded;} }
  boolean safeVolume(Location at){return true;}
  boolean buildPillar(){pillarDisplays.add(new BlockDisplay());return true;}
  void createArmor(){armor.clear();for(int i=impacts+1;i<=RiftRamPolicy.ARMOUR_PLATES;i++)armor.add(new Piece(new BlockDisplay(),i));}
  void dust(Location at,int n,double a,double b){}
  BlockDisplay block(Location at,Material m,Vector3f size){return new BlockDisplay();}
  Object nearest(Location at,List<Object> players){return null;}
  void hold(){}
  void raiseTestPillar(){
    if(pillarId!=null)throw new AssertionError("second active pillar");
    pillarId=UUID.randomUUID();pillar=new Location(world);pillarDisplays.add(new BlockDisplay());
    // Mirrors production raisePillar(): a fresh pillar has absorbed nothing.
    pillarImpacts=0;
  }
  static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
  public static void main(String[] args){
    int plates=RiftRamPolicy.ARMOUR_PLATES;
    RamRuntimeProbe p=new RamRuntimeProbe(); Set<UUID> receipts=new HashSet<>();
    for(int i=1;i<=plates;i++)p.armor.add(new Piece(new BlockDisplay(),i));
    // SUPERSEDED: the retired model gave every impact its own pillar. The roster
    // now holds four ordinary one-hit pillars and one final pillar that absorbs
    // three collisions, so the pillar survives impacts MINOR_PILLARS..REQUIRED-1.
    for(int i=1;i<=plates;i++){
      if(p.pillarId==null) {
        p.raiseTestPillar(); check(receipts.add(p.pillarId),"unique pillar identity");
      }
      Object standing=p.pillarId;
      p.action=Action.CHASE;p.impact();check(p.impacts==i-1,"walking cannot hit");
      p.action=Action.CHARGE;p.banked=false;p.impact();
      check(p.impacts==i,"one impact");
      // The policy's ordinal has advanced after an ordinary collapse; asking
      // remaining hits alone now describes the NEXT pillar, not the one hit.
      boolean pillarCollapsed=i<=RiftRamPolicy.MINOR_PILLARS
              || RiftRamPolicy.impactsRemainingOnPillar(i)==0;
      if(pillarCollapsed) {
        check(p.pillarId==null,"a spent pillar retires");
        check(p.pillarDisplays.isEmpty(),"pillar displays removed");
      } else {
        // The final pillar keeps standing and keeps its own displays.
        check(Objects.equals(p.pillarId,standing),"an unfinished pillar stays standing");
        check(!p.pillarDisplays.isEmpty(),"an unfinished pillar keeps its displays");
      }
      check(p.armor.size()==plates-i,"one 3D armor section retired");
      check(p.mites.size()<=6,"living Endermite cap");
      p.impact();check(p.impacts==i,"duplicate callback ignored");
      if(i<plates)check(!p.completed,"impact is not completion");
      if(i<plates){
        if(p.pillarId==null) p.raiseTestPillar();
        Map<String,String> saved=p.snapshot();
        RamRuntimeProbe restored=new RamRuntimeProbe();restored.ram=null;Bukkit.loaded=p.ram;
        restored.restore(saved);
        check(restored.impacts==i&&Objects.equals(restored.id(),p.id()),"restart preserves actor and impacts");
        check(restored.armor.size()==plates-i,"restart preserves the armor stage");
        check(restored.pillarId.equals(p.pillarId),"restart preserves pillar identity");
        check(restored.pillarImpacts==p.pillarImpacts,"restart preserves how much of the pillar is gone");
        check(restored.action==Action.RECOVER&&!restored.completed,"restart cannot replay stale action");
        p.clearPillar();
      }
    }
    check(p.world.spawned>0,"pillars actually released adds");
    check(p.world.spawned<=RiftRamPolicy.ENDERMITES_CAP,
        "adds never exceed the encounter-wide cap: "+p.world.spawned);
    // The final pillar must never release Endermites, even when every earlier
    // add is already dead: the plate is spent, the encounter is not an add phase.
    RamRuntimeProbe finalPillar=new RamRuntimeProbe();
    finalPillar.impacts=plates-1;finalPillar.raiseTestPillar();finalPillar.impact();
    check(finalPillar.impacts==plates&&finalPillar.world.spawned==0,
        "the final pillar never releases Endermites even when previous adds are dead");
    check(!p.completed,"the final impact alone cannot complete");
    p.died(UUID.randomUUID());check(!p.completed,"foreign death ignored");
    p.died(p.id());check(p.completed,"confirmed exposed death completes");
    check(p.debris.isEmpty()&&p.mites.isEmpty(),"final cleanup");
    int removals=p.host.removed;p.died(p.id());check(p.host.removed==removals,"death once");
    RamRuntimeProbe early=new RamRuntimeProbe();early.raiseTestPillar();early.died(early.id());
    check(!early.completed&&early.closed&&early.host.failures==1,"premature death recovers");
    early.close();check(early.host.failures==1,"cleanup idempotent");
    RamRuntimeProbe failed=new RamRuntimeProbe();failed.raiseTestPillar();failed.host.storageHealthy=false;failed.impact();
    check(failed.closed&&!failed.completed&&failed.world.spawned==0,"storage failure stops effects and progression");
    RamRuntimeProbe legacy=new RamRuntimeProbe();legacy.ram=null;legacy.restore(Map.of("ram.schema","1"));
    check(legacy.closed&&legacy.host.failures==1&&!legacy.completed,"legacy state cannot become a Ram victory");
    RamRuntimeProbe finished=new RamRuntimeProbe();finished.ram=null;Bukkit.loaded=null;finished.restore(p.snapshot());
    check(finished.completed&&finished.impacts==RiftRamPolicy.REQUIRED_IMPACTS,
        "durable death receipt needs no resurrected actor");
    System.out.println("OK");
  }
'''
    java = tmp_path/'RamRuntimeProbe.java'
    java.write_text(probe+'\n'+methods+'\n}',encoding='utf-8')
    built = subprocess.run(['javac','-d',str(tmp_path),str(ROOT/'copimine-end-event/src/me/copimine/endevent/domain/RiftRamPolicy.java'),str(java)],capture_output=True,text=True)
    assert built.returncode == 0, built.stderr
    ran = subprocess.run(['java','-cp',str(tmp_path),'RamRuntimeProbe'],capture_output=True,text=True)
    assert ran.returncode == 0, ran.stderr


def test_runtime_tick_failure_cleans_up_and_stale_ticks_do_nothing(tmp_path):
    source = RUNTIME.read_text('utf-8').replace('    public ', '    private ')
    method = extract(source, 'void tick()')
    probe = '''public class RamTickProbe {
      boolean closed,completed;int calls,failures;boolean active=true;
      class Host {boolean active(){return active;}} Host host=new Host();
      void advance(){calls++;throw new IllegalStateException("missing-display");}
      void fail(String reason){if(!reason.contains("IllegalStateException"))throw new AssertionError(reason);failures++;closed=true;}
      public static void main(String[] args){
        var p=new RamTickProbe();p.active=false;p.tick();if(p.calls!=0)throw new AssertionError("stale scope");
        p.active=true;p.tick();p.tick();if(p.calls!=1||p.failures!=1||!p.closed)throw new AssertionError("failure cleanup");
      }
    '''
    path=tmp_path/'RamTickProbe.java'
    path.write_text(probe+method+'}',encoding='utf-8')
    built=subprocess.run(['javac','-d',str(tmp_path),str(path)],capture_output=True,text=True)
    assert built.returncode==0,built.stderr
    ran=subprocess.run(['java','-cp',str(tmp_path),'RamTickProbe'],capture_output=True,text=True)
    assert ran.returncode==0,ran.stderr
