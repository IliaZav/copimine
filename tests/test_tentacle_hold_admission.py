"""A rejected hold teleport must cancel admission without resetting player physics."""
from pathlib import Path
import subprocess
from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


def test_hold_preserves_controls_when_protection_or_geometry_refuses_it(tmp_path):
    source = (ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    signature = 'private boolean lockTentaclePlayer(' if 'private boolean lockTentaclePlayer(' in source else 'private void lockTentaclePlayer('
    method = _method(source, signature)
    java = tmp_path/'HoldProbe.java'
    java.write_text('''import java.util.*;
public class HoldProbe {
 static final double TENTACLE_GRAB_SOCKET_HEIGHT=2;
 String eventId="event";long generation=1;boolean geometry=true,arena=true;
 static final Object WORLD=new Object();
 static class Vector{}
 static class Location {public Location clone(){return this;} Location add(double x,double y,double z){return this;}}
 static class ItemDisplay {boolean valid=true,dead,owned=true;
  boolean isValid(){return valid;} boolean isDead(){return dead;} Object getWorld(){return WORLD;}
  Location getLocation(){return new Location();}}
 static class Player {boolean online=true,dead,immune,accepted;int teleports,velocityWrites,fallWrites;
  boolean isOnline(){return online;} boolean isDead(){return dead;} boolean isInvulnerable(){return immune;}
  Object getWorld(){return WORLD;}boolean teleport(Location p){teleports++;return accepted;}
  void setVelocity(Vector v){velocityWrites++;}void setFallDistance(float v){fallWrites++;}}
 boolean isActiveBossParticipant(Player p){return true;}
 boolean ownedBySession(ItemDisplay d,String e,long g){return d.owned;}
 boolean isArenaLocation(Location p){return arena;}
 boolean isSafeCombatParticipantLocation(Location p,Player player){return geometry;}
 public static void main(String[]args){
  var p=new HoldProbe();var display=new ItemDisplay();var player=new Player();
  p.lockTentaclePlayer(display,player);
  if(player.teleports!=1 || player.velocityWrites!=0 || player.fallWrites!=0)
   throw new AssertionError("rejected teleport still seized player physics");
  player.accepted=true;p.geometry=false;p.lockTentaclePlayer(display,player);
  if(player.teleports!=1)throw new AssertionError("unsafe hold placement was attempted");
  p.geometry=true;p.arena=false;p.lockTentaclePlayer(display,player);
  if(player.teleports!=1)throw new AssertionError("hold escaped the arena");
  p.arena=true;player.immune=true;p.lockTentaclePlayer(display,player);
  if(player.teleports!=1)throw new AssertionError("immune target was captured");
  player.immune=false;display.owned=false;p.lockTentaclePlayer(display,player);
  if(player.teleports!=1)throw new AssertionError("stale actor captured a player");
  display.owned=true;p.lockTentaclePlayer(display,player);
  if(player.teleports!=2 || player.velocityWrites!=1 || player.fallWrites!=1)
   throw new AssertionError("accepted placement did not acquire hold");
 }
''' + method + '\n}', encoding='utf-8')
    built = subprocess.run(['javac','-d',str(tmp_path),str(java)],capture_output=True,text=True)
    assert built.returncode == 0, built.stderr
    run = subprocess.run(['java','-cp',str(tmp_path),'HoldProbe'],capture_output=True,text=True)
    assert run.returncode == 0, run.stderr
