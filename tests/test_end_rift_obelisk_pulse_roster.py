"""Run the production Wave 4 pulse at a detached roster/world boundary."""
from pathlib import Path
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]

def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

@pytest.fixture(scope='module')
def pulse_probe(tmp_path_factory):
    source = (ROOT / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    directory = tmp_path_factory.mktemp('obelisk-pulse-roster')
    java = directory / 'ObeliskPulseRoster.java'
    java.write_text('''
import java.util.*;
public class ObeliskPulseRoster {
  Object world = new Object(); List<Player> waves = new ArrayList<>(), bosses = new ArrayList<>();
  class Location { Object world; double x; Location(Object world,double x){this.world=world;this.x=x;}
    Object getWorld(){return world;} double distanceSquared(Location b){return (x-b.x)*(x-b.x);} }
  class Player { Location point; int effects; Player(Object world,double x){point=new Location(world,x);}
    Object getWorld(){return point.world;} Location getLocation(){return point;}
    void addPotionEffect(PotionEffect effect){effects++;} }
  record PotionEffect(Object type,int duration,int amplifier,boolean a,boolean b,boolean c) {}
  static class PotionEffectType { static Object WEAKNESS=new Object(), NAUSEA=new Object(), SLOWNESS=new Object(); }
  List<Player> activeWaveParticipants(){return waves;} List<Player> activeBossParticipants(){return bosses;}
  public static void main(String[] args){
    var p=new ObeliskPulseRoster(); var wave=p.new Player(p.world,0);
    var far=p.new Player(p.world,6); var elsewhere=p.new Player(new Object(),0);
    var bossOnly=p.new Player(p.world,0);
    p.waves.addAll(List.of(wave,far,elsewhere)); p.bosses.add(bossOnly);
    p.applyCurrentObeliskPulse(p.new Location(p.world,0),5);
    if(wave.effects!=3 || far.effects!=0 || elsewhere.effects!=0 || bossOnly.effects!=0)
      throw new AssertionError("Wave 4 pulse must use the wave roster and respect its world/radius");
    p.applyCurrentObeliskPulse(p.new Location(p.world,0),Double.NaN);
    if(wave.effects!=3) throw new AssertionError("Non-finite radius cannot apply effects");
  }
''' + method(source, 'private void applyCurrentObeliskPulse(') + '\n}', encoding='utf-8')
    result = subprocess.run(['javac','-d',str(directory),str(java)],capture_output=True,text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory

def test_pulse_hits_only_current_wave_roster(pulse_probe):
    result = subprocess.run(['java','-cp',str(pulse_probe),'ObeliskPulseRoster'],capture_output=True,text=True)
    assert result.returncode == 0, result.stdout + result.stderr
