"""A per-viewer model heartbeat cannot rebroadcast idle into the whole fight."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_actual_binding_heartbeat_reasserts_active_clip_only_to_its_viewer(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = extract(source, "void bindBossClient(Player player)")
    java = tmp_path / "BossBindingHeartbeatProbe.java"
    java.write_text("""import java.util.*;
import me.copimine.endevent.domain.BossPhase;
public class BossBindingHeartbeatProbe {
 static class Entity{} static class LivingEntity extends Entity{} static class Player{}
 static class Bukkit {static Entity getEntity(UUID id){return new LivingEntity();}}
 static class Config{String clientBossId(){return "";}}
 UUID bossUuid=new UUID(0,1);String bossBindingInstanceId="binding",eventId="event";
 String bossHitboxAnimationId="GROUND_SLAM",CLIENT_VISUAL_BOSS="END_RIFT_GUARDIAN_V1";
 long generation=1,BOSS_PHASE_VISUAL_TRANSITION_MILLIS=400;
 BossPhase bossPhase=BossPhase.RIFT;Config config=new Config();
 int broadcasts,phases;Player expected;String animation;
 boolean isEventVisualViewer(Player p){return true;}boolean isOfficialBoss(LivingEntity e){return true;}
 void sendClientPacket(Player p,String type,String instance,long duration,String subject,String visual){
  if(!"END_BOSS_PHASE".equals(type))return;
  if(p!=expected)throw new AssertionError("heartbeat reached another viewer");phases++;animation=visual;
 }
 void sendCurrentBossPhaseVisualUpdate(LivingEntity e,BossPhase phase,String animation){broadcasts++;}
 void sendCurrentBossBarVisualUpdate(Player p,LivingEntity e,BossPhase phase){}
 void sendBossPhaseVisualUpdate(Player p,Entity e,BossPhase phase){broadcasts++;}
 String canonicalBossAnimation(String value){return value;}
""" + method + """
 public static void main(String[] args){
  var probe=new BossBindingHeartbeatProbe();probe.expected=new Player();
  probe.bindBossClient(probe.expected);
  if(probe.broadcasts!=0)throw new AssertionError("binding heartbeat rebroadcast animation to every viewer");
  if(probe.phases!=1||!probe.animation.endsWith("GROUND_SLAM"))
    throw new AssertionError("heartbeat reset or omitted the active authored clip: "+probe.animation);
 }
} """, encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
                             "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "BossBindingHeartbeatProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
