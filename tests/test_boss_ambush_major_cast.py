"""The independently animated ambush still occupies the boss's major action."""
from pathlib import Path
import subprocess
from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


def test_ambush_holds_the_major_slot_until_cleanup_in_the_same_generation(tmp_path):
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
 long generation=3;final TentacleController tentacleController=new TentacleController();
 void check(boolean expected){if(bossAmbushOwnsMajorCast()!=expected)throw new AssertionError();}
 public static void main(String[]args){
  var p=new AmbushMajorProbe();p.tentacleController.begin(3);p.check(false);
  p.tentacleController.register(3,UUID.randomUUID(),TentacleAnimationPolicy.Kind.PERMANENT,0,0);p.check(false);
  p.tentacleController.register(3,UUID.randomUUID(),TentacleAnimationPolicy.Kind.SMALL,1,0);p.check(false);
  var ambush=UUID.randomUUID();p.tentacleController.register(3,ambush,TentacleAnimationPolicy.Kind.UNDER_PLAYER,2,0);p.check(true);
  p.tentacleController.transition(3,ambush,TentacleAnimationPolicy.State.HOLD,20,UUID.randomUUID());p.check(true);
  p.tentacleController.transition(3,ambush,TentacleAnimationPolicy.State.THROW,40,UUID.randomUUID());p.check(true);
  p.tentacleController.remove(ambush);p.check(false);
  p.tentacleController.register(3,ambush,TentacleAnimationPolicy.Kind.UNDER_PLAYER,2,0);p.check(true);
  p.generation=4;p.check(false);
 }
''' + method + '\n}',encoding='utf-8')
    build = subprocess.run(['javac','-sourcepath',str(ROOT/'copimine-end-event/src'),'-d',str(tmp_path),str(path)],capture_output=True,text=True)
    assert build.returncode == 0, build.stderr
    run = subprocess.run(['java','-cp',str(tmp_path),'AmbushMajorProbe'],capture_output=True,text=True)
    assert run.returncode == 0, run.stderr
