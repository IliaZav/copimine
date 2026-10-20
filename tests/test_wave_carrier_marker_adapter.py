"""Exercise the selected carrier's production marker creation, following and teardown."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


def test_carrier_has_one_authored_marker_following_it_and_cleanup_removes_it(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapter = extract(source, "void updateCurrentCarrierMarker(Entity carrier)")
    adapter += extract(source, "void removeCurrentCarrierMarker()")
    java = tmp_path / "CarrierMarkerProbe.java"
    java.write_text('''
import java.util.*;
import java.util.function.Consumer;
public class CarrierMarkerProbe {
  long eventTickCounter; UUID currentCarrierMarkerUuid; int spawns, moves, removed;
  static final int MODEL_CARRIER_CHARGE=830029; static final String EVENT_KIND_DISPLAY="DISPLAY";
  Map<UUID,Entity> ownedEntities=new HashMap<>(); Set<UUID> waveObjectiveVisuals=new HashSet<>();
  class Location { double x,y,z; float yaw; Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    public Location clone(){return new Location(x,y,z);} Location add(double x,double y,double z){this.x+=x;this.y+=y;this.z+=z;return this;}
    void setYaw(float y){yaw=y;} void setPitch(float p){} }
  class Entity { UUID id=UUID.randomUUID(); boolean valid=true; Location pos=new Location(0,68,0);
    UUID getUniqueId(){return id;} boolean isValid(){return valid;} Location getLocation(){return pos;}
    World getWorld(){return new World();} double getHeight(){return 2.9;} void remove(){valid=false;removed++;} }
  class LivingEntity extends Entity { }
  class ItemDisplay extends Entity { int model; boolean bright;
    enum ItemDisplayTransform {NONE}
    void setItemStack(int model){this.model=model;} void setItemDisplayTransform(ItemDisplayTransform t){}
    void setBillboard(Display.Billboard b){} void setTransformation(Transformation t){}
    void setBrightness(Display.Brightness b){bright=true;} void setDisplayWidth(float v){} void setDisplayHeight(float v){}
    void setViewRange(float v){} void setTeleportDuration(int v){} void setInterpolationDuration(int v){}
    void setGravity(boolean v){} void setInvulnerable(boolean v){} void setPersistent(boolean v){}
    void setGlowing(boolean v){} void setShadowRadius(float v){} }
  static class Display { enum Billboard {FIXED} record Brightness(int sky,int block){} }
  static class Transformation { Transformation(Vector3f a,Quaternionf b,Vector3f c,Quaternionf d){} }
  static class Vector3f {Vector3f(){} Vector3f(float v){}}
  static class Quaternionf {}
  class World { ItemDisplay spawn(Location pos,Class<ItemDisplay> cls,Consumer<ItemDisplay> initialize){
    var entity=new ItemDisplay();entity.pos=pos;initialize.accept(entity);spawns++;return entity;}}
  int overlayItem(int model,String name){return model;}
  void tag(Entity e,String kind,int wave,boolean official){}
  void registerOwnedEntity(Entity e){ownedEntities.put(e.id,e);}
  void unregisterOwnedEntity(UUID id,String reason){ownedEntities.remove(id);}
  boolean isLiveOwnedEntity(UUID id){return id!=null&&ownedEntities.containsKey(id)&&ownedEntities.get(id).valid;}
  boolean teleportCombatEntity(Entity e,Location pos){e.pos=pos;moves++;return true;}
  public static void main(String[] args){
    var p=new CarrierMarkerProbe(); var carrier=p.new LivingEntity();p.ownedEntities.put(carrier.id,carrier);
    p.updateCurrentCarrierMarker(carrier);
    var id=p.currentCarrierMarkerUuid;var marker=(ItemDisplay)p.ownedEntities.get(id);
    if(p.spawns!=1||marker.model!=830029||!marker.bright)throw new AssertionError("carrier has no authored charge model");
    if(marker.pos.y<=carrier.pos.y+carrier.getHeight())throw new AssertionError("crest obscures carrier body instead of sitting above it");
    carrier.pos.x=9;p.eventTickCounter=10;p.updateCurrentCarrierMarker(carrier);
    if(p.spawns!=1||p.moves!=1||marker.pos.x!=9||!id.equals(p.currentCarrierMarkerUuid))
      throw new AssertionError("moving carrier duplicates or leaves its marker behind");
    carrier.valid=false;p.updateCurrentCarrierMarker(carrier);
    if(p.currentCarrierMarkerUuid!=null||p.ownedEntities.containsKey(id)||p.waveObjectiveVisuals.contains(id)||p.removed!=1)
      throw new AssertionError("carrier death retains stale marker ownership");
    p.removeCurrentCarrierMarker();if(p.removed!=1)throw new AssertionError("duplicate cleanup removed the marker twice");
  }
''' + adapter + "\n}\n", encoding="utf-8")
    compiled = subprocess.run(["javac", "-J-Xmx128m", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(["java", "-Xmx128m", "-cp", str(tmp_path), "CarrierMarkerProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
