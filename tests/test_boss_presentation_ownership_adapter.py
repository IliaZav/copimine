"""Execute the real effect adapter's captured event/phase/cast ownership gate."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_delayed_effects_cannot_escape_their_phase_cast_or_generation(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = extract(source, "void playBossImpactPresentation(")
    java = tmp_path / "BossPresentationOwnershipProbe.java"
    java.write_text("""import java.util.*;
import java.util.function.*;
import me.copimine.endevent.domain.*;
public class BossPresentationOwnershipProbe {
 static class Location{} static class Vector{} static class Player{}
 static class LivingEntity{UUID id=new UUID(0,1);UUID getUniqueId(){return id;}}
 static class Registry{boolean owns(long gen){return true;}}
 String eventId="event";long generation=7,bossCastSequence=2;
 BossPhase bossPhase=BossPhase.HUNT;LivingEntity boss=new LivingEntity();
 UUID bossUuid=boss.id;Registry taskRegistry=new Registry();boolean owned=true;
 boolean ownedBySession(LivingEntity b,String event,long gen){return owned;}
 List<Player> eventAudience(){return List.of();}
 boolean isEventParticleViewer(Player p,Location l){return true;}
 void registerEncounterTask(Object task){}
 static BooleanSupplier gate;
 static class BossPresentationEffects{
  static void play(Object plugin,LivingEntity b,BossImpactGeometryPolicy.Kind kind,Location origin,
    Vector direction,double radius,int rgb,boolean sound,BooleanSupplier valid,
    Supplier<List<Player>> viewers,BiPredicate<Player,Location> allowed,Consumer<Object> register){gate=valid;}
 }
""" + method + """
 void begin(BossImpactGeometryPolicy.Kind kind,boolean action){
  playBossImpactPresentation(boss,kind,new Location(),new Vector(),4,false,action);
  check(gate.getAsBoolean(),"new effect rejected");
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  var p=new BossPresentationOwnershipProbe();
  p.begin(BossImpactGeometryPolicy.Kind.PHASE,false);p.bossPhase=BossPhase.RIFT;
  check(!gate.getAsBoolean(),"old phase effects survived new phase");
  p.begin(BossImpactGeometryPolicy.Kind.SWIPE,true);p.bossCastSequence++;
  check(!gate.getAsBoolean(),"old impact survived a replacement cast");
  p.begin(BossImpactGeometryPolicy.Kind.DEFEAT,false);p.generation++;
  check(!gate.getAsBoolean(),"old victory effects survived generation change");
  p.begin(BossImpactGeometryPolicy.Kind.SLAM,true);p.eventId="replacement";
  check(!gate.getAsBoolean(),"old effect survived event replacement");
  p.begin(BossImpactGeometryPolicy.Kind.SLAM,true);p.bossUuid=null;
  check(!gate.getAsBoolean(),"effect survived boss cleanup");
  p.bossUuid=p.boss.id;p.begin(BossImpactGeometryPolicy.Kind.SLAM,true);p.owned=false;
  check(!gate.getAsBoolean(),"unowned boss emitted effects");
 }
}
""",encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
                             "-d", str(tmp_path), str(java)],capture_output=True,text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "BossPresentationOwnershipProbe"],capture_output=True,text=True)
    assert result.returncode == 0, result.stdout + result.stderr
