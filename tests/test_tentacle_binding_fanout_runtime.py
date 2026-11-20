"""Execute the real binding adapter: an actor snapshot belongs to one viewer."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_tentacle_binding_fanout_is_linear_and_keeps_active_pose(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = [extract(source, signature) for signature in (
        "void bindEventEntityClientForOnlinePlayers(Entity entity)",
        "void bindEventEntityClient(Player player, Entity entity)",
        "void sendTentacleAnimationVisualUpdate(Entity entity,",
    )]
    if "void sendTentacleAnimationVisualUpdate(Player player," in source:
        methods.append(extract(source, "void sendTentacleAnimationVisualUpdate(Player player,"))
    java = tmp_path / "TentacleBindingProbe.java"
    java.write_text('''import java.util.*;
public class TentacleBindingProbe {
 enum EntityType { SKELETON, ITEM_DISPLAY }
 static class Entity {
  final UUID id=UUID.randomUUID(); Object world="arena";
  UUID getUniqueId(){return id;} Object getWorld(){return world;}
  EntityType getType(){return EntityType.ITEM_DISPLAY;}
 }
 static class ItemDisplay extends Entity {}
 static class Player extends Entity {int binds,poses; String pose;}
 static class Bukkit {static List<Player> players=List.of(); static List<Player> getOnlinePlayers(){return players;}}
 static class TentacleAnimationPolicy {
  enum State { READY, SMALL_WINDUP, SMALL_STRIKE, SMALL_RECOVERY } enum Kind { PERMANENT, SMALL, UNDER_PLAYER, TEMPORARY }
  static long durationTicks(State s){return 12;} static String wireName(State s){return s.name();}
 }
 static class TentacleController {
  record VisualState(TentacleAnimationPolicy.Kind kind,long startedTick){}
  VisualState state(UUID id){return new VisualState(TentacleAnimationPolicy.Kind.SMALL,100);}
 }
 static class Tuning {double scale(){return .65;}}
 static class Config {Tuning smallKaguneTuning(){return new Tuning();}}
 Config config=new Config(); TentacleController tentacleController=new TentacleController();
 Map<UUID,String> entityBindingInstances=new HashMap<>(); String eventId="event";
 Set<UUID> tentaclePoseCapablePlayers=new HashSet<>();
 long generation=9,eventTickCounter=105;
 String keyKind="kind",keyTentacleHealthState="health",keyTentacleTarget="target",keyTentacleHealth="hp";
 String EVENT_KIND_RITUAL_CASTER="caster",EVENT_KIND_TENTACLE="tentacle";
 boolean isEventVisualViewer(Player p){return Bukkit.players.contains(p);}
 String clientVisualId(Entity e){return "END_RIFT_TENTACLE_V1";}
 String readString(Entity e,String key){return key.equals(keyKind)?"tentacle":key.equals(keyTentacleHealthState)?"DAMAGED":"";}
 double readDouble(Entity e,String key,double fallback){return 42;}
 TentacleAnimationPolicy.State tentacleAnimationState(Entity e){return TentacleAnimationPolicy.State.SMALL_STRIKE;}
 void sendRitualCasterPhase(Player p,Entity e){throw new AssertionError();}
 java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
 boolean sendClientPacket(Player p,String type,String instance,long duration,String subject,String visual){
  if(type.equals("END_ENTITY_BIND"))p.binds++;
  if(type.equals("END_ENTITY_PHASE")){p.poses++;p.pose=visual;}
  return true;
 }
''' + "\n".join(methods) + '''
 public static void main(String[] args){
  for(int count:new int[]{2,5,10,11,20}) {
   var probe=new TentacleBindingProbe();var players=new ArrayList<Player>();
   for(int i=0;i<count;i++){
    var player=new Player();players.add(player);probe.tentaclePoseCapablePlayers.add(player.getUniqueId());
   }Bukkit.players=players;
   var actors=new ArrayList<ItemDisplay>();for(int i=0;i<38;i++)actors.add(new ItemDisplay());
   for(var actor:actors)probe.bindEventEntityClientForOnlinePlayers(actor);
   for(var p:players){
    if(p.binds!=38||p.poses!=38)throw new AssertionError(count+" viewers: binds="+p.binds+", poses="+p.poses+" (expected 38 each)");
    if(!p.pose.startsWith("SMALL_STRIKE|t=100|")||!p.pose.contains("|health=DAMAGED")||!p.pose.contains("|hp=42.0"))throw new AssertionError(p.pose);
   }
   var late=players.getFirst();int before=late.poses;
   probe.bindEventEntityClient(late,actors.getFirst());
   if(late.poses!=before+1)throw new AssertionError("late binding lost pose");
   for(int i=1;i<count;i++)if(players.get(i).poses!=38)throw new AssertionError("private refresh leaked");
   probe.sendTentacleAnimationVisualUpdate(actors.getFirst(),TentacleAnimationPolicy.State.SMALL_STRIKE);
   for(int i=1;i<count;i++)if(players.get(i).poses!=39)throw new AssertionError("state transition did not broadcast once");
   var foreign=new ItemDisplay();foreign.world="other";
   probe.bindEventEntityClient(late,foreign);
   if(late.poses!=before+2)throw new AssertionError("cross-world binding");
   probe.tentaclePoseCapablePlayers.remove(late.getUniqueId());
   probe.bindEventEntityClient(late,actors.getFirst());
   if(!late.pose.equals("THROW|t=100|health=DAMAGED"))throw new AssertionError("legacy receiver would reject "+late.pose);
  }
 }
}
''', encoding="utf-8")
    for command in (["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(java)],
                    ["java", "-cp", str(tmp_path), "TentacleBindingProbe"]):
        result = subprocess.run(command, capture_output=True, text=True)
        assert result.returncode == 0, result.stdout + result.stderr
