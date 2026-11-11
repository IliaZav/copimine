"""Execute the real exposure adapter: one owned potion path, no direct duplicate damage."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract
ROOT=Path(__file__).resolve().parents[1]


@pytest.mark.parametrize('players',[1,2,5,10,11,20])
def test_exposure_uses_party_wither_and_clears_before_the_next_cadence(tmp_path,players):
    source=(ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    body=extract(source,'void applyBlackFogEffects(long now, Location core)')
    probe=tmp_path/'Exposure.java'
    probe.write_text(r"""import java.util.*;
import me.copimine.endevent.domain.BlackFogDamagePolicy;
public class Exposure {
 enum PotionEffectType { SLOWNESS,WITHER }
 enum Sound { BLOCK_CONDUIT_AMBIENT_SHORT,BLOCK_SCULK_SENSOR_CLICKING }
 enum SoundCategory { AMBIENT }
 static class Location{}
 static class Player{
  UUID id=UUID.randomUUID();boolean safe;int directDamage;
  Map<PotionEffectType,Integer> effects=new EnumMap<>(PotionEffectType.class);
  UUID getUniqueId(){return id;}Location getLocation(){return new Location();}
  double getHealth(){return 20;}void damage(double d){directDamage++;}
  void playSound(Location l,Sound s,SoundCategory c,float volume,float pitch){}
 }
 int currentFogRosterPlayers;
 Map<UUID,Long> currentFogLastImpactAt=new HashMap<>(),currentFogSafeSoundAt=new HashMap<>();
 Set<UUID> currentFogShelteredAudio=new HashSet<>();
 List<Player> players=List.of(new Player());
 boolean evacuateBlackFogShelters(){return true;}
 List<Player> activeWaveParticipants(){return players;}
 void releaseInactiveBlackFogEffects(List<Player> p){}
 boolean isCurrentSafeZonePlayer(Player p){return p.safe;}
 void releaseBlackFogEffects(Player p){p.effects.clear();}
 void applyOwnedBlackFogEffect(Player p,PotionEffectType type,int amplifier){p.effects.put(type,amplifier);}
 BODY
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  var h=new Exposure();h.currentFogRosterPlayers=Integer.parseInt(args[0]);var player=h.players.get(0);
  h.applyBlackFogEffects(10000,new Location());
  check(player.effects.get(PotionEffectType.WITHER)!=null,"outside refuge must receive owned Wither");
  check(player.effects.get(PotionEffectType.WITHER)==(h.currentFogRosterPlayers>=11?2:1),"Wither II/III by frozen roster");
  check(player.effects.get(PotionEffectType.SLOWNESS)==1,"short Slowness II remains bounded");
  check(player.directDamage==0,"no duplicate direct damage beside real Wither ticks");
  player.safe=true;h.applyBlackFogEffects(10001,new Location());
  check(player.effects.isEmpty(),"refuge entry releases owned exposure before cadence check");
 }
}""".replace('BODY',body),encoding='utf-8')
    compiled=subprocess.run(['javac','-encoding','UTF-8','-sourcepath',str(ROOT/'copimine-end-event/src'),'-d',str(tmp_path),str(probe)],capture_output=True,text=True)
    assert compiled.returncode==0,compiled.stdout+compiled.stderr
    run=subprocess.run(['java','-cp',str(tmp_path),'Exposure',str(players)],capture_output=True,text=True)
    assert run.returncode==0,run.stdout+run.stderr
