"""Run the physical obelisk collapse with its actual pre-collapse model transform."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


def test_collapse_never_rescales_critical_model_to_an_unrelated_height(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapter = extract(source, "void renderObeliskCollapse(Wave4ObeliskRuntimeState state)")
    java = tmp_path / "CollapseProbe.java"
    java.write_text('''
import java.util.*;
public class CollapseProbe {
 long eventTickCounter;
 Map<UUID,Transformation> currentWave4CollapseTransforms=new HashMap<>();
 static class Vector3f { float x,y,z; Vector3f(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
  Vector3f(Vector3f v){this(v.x,v.y,v.z);} Vector3f mul(float f){x*=f;y*=f;z*=f;return this;}}
 static class AxisAngle4f {}
 record Transformation(Vector3f translation,AxisAngle4f left,Vector3f scale,AxisAngle4f right) {
  Vector3f getTranslation(){return translation;}Vector3f getScale(){return scale;}
  AxisAngle4f getLeftRotation(){return left;}AxisAngle4f getRightRotation(){return right;}}
 static class Entity {boolean isValid(){return true;}}
 static class ItemDisplay extends Entity {
  Transformation t=new Transformation(new Vector3f(0,1,0),new AxisAngle4f(),new Vector3f(3.25F,2,3.25F),new AxisAngle4f());
  Transformation getTransformation(){return t;}void setTransformation(Transformation value){t=value;}
  void setInterpolationDelay(int value){} void setInterpolationDuration(int value){}
 }
 static class Bukkit {static ItemDisplay display=new ItemDisplay();static Entity getEntity(UUID id){return display;}}
 enum Sound {BLOCK_AMETHYST_BLOCK_BREAK}
 enum SoundCategory {BLOCKS}
 static class World {int fractureSounds;void playSound(Location point,Sound sound,SoundCategory category,float volume,float pitch){fractureSounds++;}}
 static class Color {static Color fromRGB(int r,int g,int b){return new Color();}}
 static class Particle {static final Particle REVERSE_PORTAL=new Particle(),END_ROD=new Particle(),CRIT=new Particle(),DUST=new Particle();
  static class DustOptions {DustOptions(Color color,float size){}}
 }
 static class Location {World world=new World();public Location clone(){return this;}Location add(double a,double b,double c){return this;}World getWorld(){return world;}}
 static class Wave4ObeliskRuntimeState {
  final Location location=new Location();
  boolean fractureCuePlayed;
  UUID id=UUID.randomUUID();UUID id(){return id;}Location base(){return location;}
  long destroyAtTick(){return 8;}Map<Integer,UUID> visualIds(){return Map.of(0,id);}}
 static class ObeliskGeometryPolicy {static final int VISUAL_DISPLAY_KEY=0;static final float VISUAL_WIDTH_BLOCKS=3.25F;}
 int fractureParticleCalls;
 void spawnEventParticle(Location p,Particle type,int count,double a,double b,double c,double d){if(type==Particle.CRIT||type==Particle.DUST)fractureParticleCalls++;}
 void spawnEventParticle(Location p,Particle type,int count,double a,double b,double c,double d,Particle.DustOptions options){if(type==Particle.CRIT||type==Particle.DUST)fractureParticleCalls++;}
 public static void main(String[] args){
  var probe=new CollapseProbe();var state=new Wave4ObeliskRuntimeState();
  probe.renderObeliskCollapse(state);
  if(Bukkit.display.t.getScale().y!=2 || Bukkit.display.t.getTranslation().y!=1)
    throw new AssertionError("critical model jumped to a different size before collapse");
  probe.eventTickCounter=4;probe.renderObeliskCollapse(state);probe.renderObeliskCollapse(state);
  if(Bukkit.display.t.getScale().y!=1 || Bukkit.display.t.getTranslation().y!=.5F)
    throw new AssertionError("collapse compounded its own transform on repeated ticks");
  probe.eventTickCounter=8;probe.renderObeliskCollapse(state);
  if(Bukkit.display.t.getScale().y> .021F)throw new AssertionError("collapse did not finish");
  if(probe.fractureParticleCalls!=3||state.base().getWorld().fractureSounds!=1)
    throw new AssertionError("collapse must emit one distinct colored fracture burst and impact sound");
  probe.renderObeliskCollapse(state);
  if(probe.fractureParticleCalls!=3||state.base().getWorld().fractureSounds!=1)
    throw new AssertionError("fracture cue must be one-shot while the obelisk collapses");
 }
''' + adapter + "\n}", encoding="utf-8")
    result = subprocess.run(["javac", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "CollapseProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
