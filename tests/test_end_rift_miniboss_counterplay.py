"""Move outside a committed warning and execute the real Bukkit spell adapter."""
from pathlib import Path
import subprocess
import pytest

ROOT=Path(__file__).resolve().parents[1]

def declaration(source, signature):
    start=source.index(signature)
    opening=source.index('{',start)
    depth,end=1,opening+1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}')
        end+=1
    return source[start:end]

@pytest.fixture(scope="module")
def snare_probe(tmp_path_factory):
    source=(ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    method=declaration(source,'private void miniBossVoidSnare(')
    directory=tmp_path_factory.mktemp('locked-snare')
    fixture=directory/'SnareCounterplay.java'
    fixture.write_text('''
public class SnareCounterplay {
  static final int SLOWNESS_DEBUFF_TICKS=40, MINI_VOID_SNARE_DEBUFF_TICKS=40;
  static final double WAVE_MOB_DAMAGE_REDUCTION=0;
  static class Location { double x; Location(double x){this.x=x;} }
  static class LivingEntity { boolean los=true; boolean hasLineOfSight(Player p){return los;} }
  static class Player { double health=20; int effects; boolean eligible=true; Location p;
    Player(double x){p=new Location(x);} Location getLocation(){return p;}
    void damage(double amount,LivingEntity source){health-=amount;}
    void addPotionEffect(PotionEffect effect){effects++;}
  }
  static class PotionEffect { PotionEffect(Object type,int ticks,int amplifier,boolean a,boolean b,boolean c){} }
  static class PotionEffectType { static Object SLOWNESS=new Object(),WEAKNESS=new Object(); }
  static class Particle { static Object REVERSE_PORTAL=new Object(); }
  static class WaveDamagePolicy {static double minimumCombatDamage(double d,double r){return d;} }
  static class Config {Config miniBossTuning(){return this;} double voidSnareDamage(){return 4;} }
  Config config=new Config();
  boolean isMiniBossTargetAllowed(LivingEntity caster,Player target){return target.eligible;}
  boolean usesWaveCombatCoordination(LivingEntity caster){return true;}
  double horizontalDistanceSquared(Location a,Location b){return (a.x-b.x)*(a.x-b.x);}
  int abilityDebuffAmplifier(String s){return 0;} int weaknessDebuffAmplifier(String s){return 0;}
  void spawnEventParticle(Location p,Object particle,int count,double x,double y,double z,double speed){}
  public static void main(String[] args){
    var probe=new SnareCounterplay(); var caster=new LivingEntity();
    var player=new Player(Double.parseDouble(args[0]));
    player.eligible=Boolean.parseBoolean(args[1]); caster.los=Boolean.parseBoolean(args[2]);
    probe.miniBossVoidSnare(caster,player,new Location(0));
    boolean hit=Boolean.parseBoolean(args[3]);
    if(player.health!=(hit?16:20) || player.effects!=(hit?2:0))
      throw new AssertionError("locked telegraph must be dodgeable and cannot cross walls or hit a departed target; hp="+player.health);
  }
'''+method+'\n}',encoding='utf-8')
    result=subprocess.run(['javac','-d',str(directory),str(fixture)],capture_output=True,text=True)
    assert result.returncode==0,result.stdout+result.stderr
    return directory

@pytest.mark.parametrize('x,eligible,los,hit',[(0,True,True,True),(2,True,True,True),(3,True,True,False),(0,False,True,False),(0,True,False,False)])
def test_snare_has_real_avoidance_window(snare_probe,x,eligible,los,hit):
    result=subprocess.run(['java','-cp',str(snare_probe),'SnareCounterplay',str(x),str(eligible).lower(),str(los).lower(),str(hit).lower()],capture_output=True,text=True)
    assert result.returncode==0,result.stdout+result.stderr
