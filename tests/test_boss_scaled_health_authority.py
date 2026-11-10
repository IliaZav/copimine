"""The HUD/phase max must preserve the full frozen party-scaled real-HP pool."""
from pathlib import Path
import subprocess
from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


def test_scaled_max_health_is_not_clamped_to_the_obsolete_twenty_thousand(tmp_path):
    source = (ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    method = _method(source, 'private double bossMaxHealth(')
    path = tmp_path/'ScaledHealthProbe.java'
    path.write_text('''import me.copimine.endevent.domain.BossHealthPolicy;
import me.copimine.endevent.domain.BossScalingPolicy;
public class ScaledHealthProbe {
 Object keyBossMaxHealth=new Object();int players=2;
 static class PersistentDataType {static final Object DOUBLE=new Object();}
 static class Data {Double value;Double get(Object key,Object type){return value;}}
 static class LivingEntity {final Data data=new Data();Data getPersistentDataContainer(){return data;}}
 double computedBossMaxHealth(){return BossScalingPolicy.maxHealthForPlayers(players);}
 void check(LivingEntity boss,double expected){if(bossMaxHealth(boss)!=expected)throw new AssertionError("max="+bossMaxHealth(boss)+" expected="+expected);}
 public static void main(String[]args){
  var p=new ScaledHealthProbe();var boss=new LivingEntity();
  for(int players=2;players<=20;players++){
   double frozen=BossScalingPolicy.maxHealthForPlayers(players);boss.data.value=frozen;
   p.players=2;p.check(boss,frozen);
  }
  boss.data.value=30000.;p.check(boss,30000.);
  boss.data.value=40000.;p.check(boss,40000.);
  boss.data.value=50000.;p.check(boss,BossScalingPolicy.HP_CAP);
  p.players=20;boss.data.value=null;p.check(boss,40000.);
  boss.data.value=Double.NaN;p.check(boss,40000.);
  boss.data.value=-1.;p.check(boss,40000.);
  p.check(null,40000.);
 }
''' + method + '\n}',encoding='utf-8')
    build = subprocess.run(['javac','-sourcepath',str(ROOT/'copimine-end-event/src'),'-d',str(tmp_path),str(path)],capture_output=True,text=True)
    assert build.returncode == 0, build.stderr
    run = subprocess.run(['java','-cp',str(tmp_path),'ScaledHealthProbe'],capture_output=True,text=True)
    assert run.returncode == 0, run.stderr
