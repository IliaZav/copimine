"""Execute production cosmetic model spawning, task fences, expiry and cleanup."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_skeleton_protection_runtime import declaration
ROOT=Path(__file__).resolve().parents[1]

@pytest.fixture(scope="module")
def model_probe(tmp_path_factory):
    out=tmp_path_factory.mktemp("boss-model-life")
    source=(ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    methods='\n'.join(declaration(source,s) for s in ['private void playBossSpellModel(', 'private void removeBossSpellModel(', 'private void clearBossSpellModels(', 'private ItemDisplay spawnSpellModelDisplay(', 'private void playMiniBossSpellModel(', 'private void playCasterSpellBody(', 'private void removeMiniBossSpellModel(', 'private void clearMiniBossSpellModels('])
    f=out/'SpellModelProbe.java'
    f.write_text(r'''import java.util.*;
import me.copimine.endevent.domain.BossSpellGeometryPolicy;
public class SpellModelProbe {
 static class Vector {Vector multiply(double m){return this;}}
 static class Location {
  World getWorld(){return WORLD;}public Location clone(){return new Location();}
  Location add(double x,double y,double z){return this;}Location add(Vector v){return this;}
  Vector getDirection(){return new Vector();}
 }
 static class Entity {UUID id=UUID.randomUUID();boolean valid=true,dead;UUID getUniqueId(){return id;}boolean isValid(){return valid;}boolean isDead(){return dead;}void remove(){valid=false;}}
 static class LivingEntity extends Entity {World getWorld(){return WORLD;}Location getLocation(){return new Location();}}
 static class Display extends Entity {
  enum Billboard {FIXED}record Brightness(int a,int b){}
 }
 static class ItemDisplay extends Display {
  enum ItemDisplayTransform {NONE}
  void setItemStack(Object v){}void setBrightness(Brightness b){}void setItemDisplayTransform(ItemDisplayTransform t){}
  void setBillboard(Billboard b){}void setViewRange(float v){}void setDisplayWidth(float v){}void setDisplayHeight(float v){}
  void setTeleportDuration(int t){}void setInterpolationDuration(int t){}void setGravity(boolean b){}void setInvulnerable(boolean b){}
  void setPersistent(boolean b){}void setShadowRadius(float v){}
 }
 static class World {List<ItemDisplay> created=new ArrayList<>();
  <T> T spawn(Location at,Class<T> type,java.util.function.Consumer<T> configure){
   T entity=type.cast(new ItemDisplay());configure.accept(entity);created.add((ItemDisplay)entity);return entity;
  }
 }
 static final World WORLD=new World();
 static class BukkitTask {Runnable callback;boolean cancelled;void cancel(){cancelled=true;}}
 static abstract class BukkitRunnable implements Runnable {
  BukkitTask task;
  BukkitTask runTaskTimer(Object plugin,long delay,long period){task=new BukkitTask();task.callback=this;TASKS.add(task);return task;}
  void cancel(){task.cancel();}
 }
 static final List<BukkitTask> TASKS=new ArrayList<>();
 static void tick(){for(var t:List.copyOf(TASKS))if(!t.cancelled)t.callback.run();}
 static class Registry {boolean owns(long value){return value==7;}}
 Registry taskRegistry=new Registry();long generation=7,bossCastSequence=5;String eventId="event",EVENT_KIND_DISPLAY="display";
 LivingEntity boss=new LivingEntity();UUID bossUuid=boss.id;boolean failTag,move=true;
 Set<UUID> bossSpellModelIds=new HashSet<>();Map<UUID,Entity> owned=new HashMap<>();
 static final int MAX_MINI_BOSS_SPELL_MODELS=8;
 Set<UUID> miniBossSpellModelIds=new HashSet<>();
 Object overlayItem(int id,String name){return id;}
 boolean isOfficialEntity(Entity e){return false;}
 void tag(Entity entity,String kind,int wave,boolean official){owned.put(entity.id,entity);if(failTag)throw new IllegalStateException("tag failed after ownership insert");}
 Entity unregisterOwnedEntity(UUID id,String reason){bossSpellModelIds.remove(id);return owned.remove(id);}
 boolean teleportCombatEntity(Entity e,Location l){return move;}
 void registerEncounterTask(BukkitTask t){}
 java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
''' + methods+r'''
 /**
  * The miniboss body lease, executed rather than merely read.
  *
  * `telegraphMiniBossSpell` first reused the Guardian spawner, whose task
  * requires `Objects.equals(bossUuid, owner)`; a miniboss never satisfies that,
  * so the body was removed on the first tick and the cast looked like a
  * particle-only spell. Each scenario below fails if that lease comes back.
  */
 void runMiniBossScenario(String scenario){
  playMiniBossSpellModel(boss,"rift_step",new Location(),20);
  check(WORLD.created.size()==1,"miniboss cast spawned no body");
  check(owned.size()==1,"miniboss body was never owned");
  check(miniBossSpellModelIds.size()==1,"miniboss body missing from its own index");
  check(bossSpellModelIds.isEmpty(),"miniboss body leaked into the boss index");
  if(scenario.equals("mini-budget")){
   for(int i=0;i<9;i++)playMiniBossSpellModel(boss,"rift_step",new Location(),20);
   check(WORLD.created.size()==MAX_MINI_BOSS_SPELL_MODELS,
    "miniboss bodies exceeded the bounded budget: "+WORLD.created.size());
   check(owned.size()==MAX_MINI_BOSS_SPELL_MODELS,"budget mismatch on ownership");
   clearMiniBossSpellModels();
   check(owned.isEmpty()&&miniBossSpellModelIds.isEmpty(),"budget clear leaked ownership");
   tick();
   for(var entity:WORLD.created)check(!entity.valid,"budget clear left an orphan body");
   for(var task:TASKS)check(task.cancelled,"budget clear left an orphan task");
   return;
  }
  tick();
  check(owned.size()==1,"miniboss body removed on the first tick, as the Guardian lease did");
  for(int i=0;i<4;i++)tick();
  check(owned.size()==1,"miniboss body expired far before its bounded TTL");
  switch(scenario){
   case "mini-caster-death" -> boss.dead=true;
   case "mini-generation" -> generation++;
   case "mini-event" -> eventId="other";
   case "mini-clear" -> clearMiniBossSpellModels();
   case "mini-expiry" -> { for(int i=0;i<8;i++)tick(); }
   default -> throw new AssertionError("unknown miniboss scenario "+scenario);
  }
  tick();
  check(owned.isEmpty()&&miniBossSpellModelIds.isEmpty(),
   "miniboss body survived terminal path "+scenario);
  for(var entity:WORLD.created)check(!entity.valid,"orphan miniboss visual "+scenario);
  for(var task:TASKS)check(task.cancelled,"orphan miniboss task "+scenario);
 }
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  var p=new SpellModelProbe();String scenario=args[0];
  if(scenario.startsWith("mini")){p.runMiniBossScenario(scenario);return;}
  p.failTag=scenario.equals("tag-failure");
  p.playBossSpellModel(p.boss,"rift_projectile",new Location(),200,true);
  if(scenario.equals("tag-failure")){
   check(p.owned.isEmpty()&&p.bossSpellModelIds.isEmpty(),"failed tag leaked ownership");
   check(!WORLD.created.getFirst().valid,"failed tag left a visible rig");return;
  }
  check(p.owned.size()==1,"missing model");
  if(scenario.equals("budget")){
   p.playBossSpellModel(p.boss,"void_blast",new Location(),200,false);
   p.playBossSpellModel(p.boss,"void_mark",new Location(),200,false);
   check(WORLD.created.size()==2,"more than two simultaneous cast props");p.clearBossSpellModels();
  }else if(scenario.equals("clear"))p.clearBossSpellModels();
  else if(scenario.equals("generation"))p.generation++;
  else if(scenario.equals("cast"))p.bossCastSequence++;
  else if(scenario.equals("boss"))p.bossUuid=UUID.randomUUID();
  else if(scenario.equals("death"))p.boss.dead=true;
  else if(scenario.equals("move-failure"))p.move=false;
  else if(scenario.equals("expiry")){
   for(int i=0;i<40;i++)tick();check(p.owned.size()==1,"expired before bounded TTL");
  }
  tick();
  check(p.owned.isEmpty()&&p.bossSpellModelIds.isEmpty(),"model ownership survived terminal path "+scenario);
  for(var entity:WORLD.created)check(!entity.valid,"orphan visual "+scenario);
  for(var task:TASKS)check(task.cancelled,"orphan visual task "+scenario);
 }
}''',encoding='utf-8')
    result=subprocess.run(['javac','-encoding','UTF-8','-sourcepath',str(ROOT/'copimine-end-event/src'),'-d',str(out),str(f)],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
    return out

@pytest.mark.parametrize('scenario',['generation','cast','boss','death','move-failure','clear','budget','expiry','tag-failure'])
def test_real_model_adapter_has_finite_owned_lifetime(model_probe,scenario):
    result=subprocess.run(['java','-cp',str(model_probe),'SpellModelProbe',scenario],capture_output=True,text=True)
    assert result.returncode==0,result.stderr


@pytest.mark.parametrize('scenario',['mini-caster-death','mini-generation','mini-event',
                                     'mini-clear','mini-expiry','mini-budget'])
def test_real_miniboss_body_outlives_the_first_tick_but_not_its_caster(model_probe,scenario):
    """The miniboss body must survive the tick that killed it under the boss lease.

    `telegraphMiniBossSpell` originally called `playBossSpellModel`, whose task
    requires `Objects.equals(bossUuid, owner)`. A miniboss is never `bossUuid`,
    so the display was spawned and deleted on the first tick -- visually the
    same as having no body, which is why the earlier increment's meshes could
    not have been seen even on a correct client.
    """
    result=subprocess.run(['java','-cp',str(model_probe),'SpellModelProbe',scenario],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
