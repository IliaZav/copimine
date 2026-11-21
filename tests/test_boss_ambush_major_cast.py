"""Live ambush stages hold the major action for a bounded lease."""
from pathlib import Path
import subprocess
from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


def test_ambush_holds_the_major_slot_only_while_its_live_stage_is_current(tmp_path):
    source = (ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    method = _method(source, 'private boolean bossAmbushOwnsMajorCast(')
    # Exercise the shared real controller; source wiring keeps forced and automatic admission together.
    for signature in ('private boolean castCurrentBossSpell(LivingEntity boss, boolean forced)',
                      'private boolean castCurrentBossSpell(LivingEntity boss, EndRiftAiPolicy.BossSpell spell,'):
        guard = _method(source, signature).split('return false;', 1)[0]
        assert 'bossAmbushOwnsMajorCast()' in guard
    path = tmp_path/'AmbushMajorProbe.java'
    path.write_text('''import java.util.*;
import me.copimine.endevent.runtime.TentacleController;
import me.copimine.endevent.domain.TentacleAnimationPolicy;
public class AmbushMajorProbe {
 long generation=3,eventTickCounter;
 final String eventId="test";
 final TentacleController tentacleController=new TentacleController();
 final Map<UUID,Object> ownedEntities=new HashMap<>();
 record ItemDisplay(boolean valid) { boolean isValid(){return valid;} }
 static class Bukkit { static UUID getPlayer(UUID player){return player;} }
 boolean ownedBySession(ItemDisplay e,String id,long gen){return id.equals(eventId)&&gen==generation;}
 boolean isActiveBossParticipant(UUID player){return player!=null;}
 void check(boolean expected){if(bossAmbushOwnsMajorCast()!=expected)throw new AssertionError();}
 public static void main(String[]args){
  var p=new AmbushMajorProbe();p.tentacleController.begin(3);p.check(false);
  p.tentacleController.register(3,UUID.randomUUID(),TentacleAnimationPolicy.Kind.PERMANENT,0,0);p.check(false);
  p.tentacleController.register(3,UUID.randomUUID(),TentacleAnimationPolicy.Kind.SMALL,1,0);p.check(false);
  var ambush=UUID.randomUUID();p.tentacleController.register(3,ambush,TentacleAnimationPolicy.Kind.UNDER_PLAYER,2,0);p.check(false);
  p.eventTickCounter=20;
  p.tentacleController.transition(3,ambush,TentacleAnimationPolicy.State.HOLD,20,UUID.randomUUID());p.check(false);
  p.ownedEntities.put(ambush,new ItemDisplay(true));p.check(true);
  p.eventTickCounter=47;p.check(false);
  p.tentacleController.transition(3,ambush,TentacleAnimationPolicy.State.THROW,47,UUID.randomUUID());p.check(true);
  p.eventTickCounter=48;
  p.tentacleController.transition(3,ambush,TentacleAnimationPolicy.State.RETRACT,48,UUID.randomUUID());p.check(false);
  p.tentacleController.remove(ambush);p.check(false);
  p.tentacleController.register(3,ambush,TentacleAnimationPolicy.Kind.UNDER_PLAYER,2,48);
  p.tentacleController.transition(3,ambush,TentacleAnimationPolicy.State.SPAWN_UNDER_PLAYER,48,UUID.randomUUID());p.check(true);
  p.generation=4;p.check(false);
 }
''' + method + '\n}',encoding='utf-8')
    build = subprocess.run(['javac','-sourcepath',str(ROOT/'copimine-end-event/src'),'-d',str(tmp_path),str(path)],capture_output=True,text=True)
    assert build.returncode == 0, build.stderr
    run = subprocess.run(['java','-cp',str(tmp_path),'AmbushMajorProbe'],capture_output=True,text=True)
    assert run.returncode == 0, run.stderr
