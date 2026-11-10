"""Execute real hold clearance: air below is required, ceilings and body collisions are not."""
from pathlib import Path
import subprocess
from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


def test_airborne_socket_requires_clear_body_volume_but_no_supporting_floor(tmp_path):
    source = (ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    if 'private boolean isSafeTentacleHoldLocation(' in source:
        method = _method(source, 'private boolean isSafeTentacleHoldLocation(')
        invoke = 'isSafeTentacleHoldLocation(at, player)'
    else:
        method = _method(source, 'private boolean isSafeCombatEntityLocation(Location candidate, Entity entity,')
        invoke = 'isSafeCombatEntityLocation(at, player, 0.35D)'
    java = tmp_path/'HoldSpaceProbe.java'
    java.write_text('''import java.util.*;
public class HoldSpaceProbe {
 static class Block {boolean solid,water,fire,web,hazard;
  Block(boolean solid){this.solid=solid;} boolean isPassable(){return !solid;}
  boolean isLiquid(){return water;} Block getType(){return this;} boolean isSolid(){return solid;}}
 static class World {Map<String,Block> cells=new HashMap<>();
  Block getBlockAt(int x,int y,int z){return cells.getOrDefault(x+":"+y+":"+z,new Block(y<=68));}}
 static class Location {World world;double x=.5,y=70,z=.5;
  Location(World w){world=w;}World getWorld(){return world;}
  double getX(){return x;}double getY(){return y;}double getZ(){return z;}}
 static class Entity {World world;Entity(World w){world=w;} World getWorld(){return world;}
  boolean isValid(){return true;}double getWidth(){return .6;}double getHeight(){return 1.8;}}
 static class Player extends Entity {Player(World w){super(w);}}
 boolean isFireBlock(Block b){return b.fire;}boolean isWebBlock(Block b){return b.web;}
 boolean isTemporaryMovementHazard(Block b){return b.hazard;}
 boolean safe(Location at,Player player){return ''' + invoke + ''';}
 public static void main(String[]args){
  var p=new HoldSpaceProbe();var w=new World();var at=new Location(w);var player=new Player(w);
  if(!p.safe(at,player))throw new AssertionError("an airborne hold incorrectly requires a floor");
  w.cells.put("0:71:0",new Block(true));
  if(p.safe(at,player))throw new AssertionError("head intersects a ceiling");w.cells.clear();
  at.x=.05;w.cells.put("-1:70:0",new Block(true));
  if(p.safe(at,player))throw new AssertionError("body intersects an adjacent wall");w.cells.clear();at.x=.5;
  var water=new Block(false);water.water=true;w.cells.put("0:70:0",water);
  if(p.safe(at,player))throw new AssertionError("hold socket is liquid");w.cells.clear();
  at.x=Double.NaN;if(p.safe(at,player))throw new AssertionError("non-finite hold position");
 }
''' + method + '\n}',encoding='utf-8')
    build = subprocess.run(['javac','-d',str(tmp_path),str(java)],capture_output=True,text=True)
    assert build.returncode == 0, build.stderr
    run = subprocess.run(['java','-cp',str(tmp_path),'HoldSpaceProbe'],capture_output=True,text=True)
    assert run.returncode == 0, run.stderr
