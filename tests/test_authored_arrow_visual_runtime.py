"""Run the production visual owner with controlled Paper spawn/follow failures."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_arrow_mesh_spawn_follow_fallback_and_cleanup(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = "\n".join(extract(source, signature) for signature in (
        "void tagEliteArrowModel(", "int eliteArrowModelData(",
        "void attachEliteArrowVisual(", "void syncEliteArrowVisual(",
        "void removeEliteArrowVisual(", "void clearEliteArrowVisuals("))
    java = tmp_path / "ArrowVisualProbe.java"
    java.write_text(r'''
import java.util.*;
import java.util.function.*;
import java.util.logging.*;
public class ArrowVisualProbe {
    static class Data {Map<Object,String> m=new HashMap<>();void set(Object k,Object type,String s){m.put(k,s);}}
    static class PersistentDataType {static Object STRING=new Object();}
    static class Vector {double x=1;double lengthSquared(){return x*x;}}
    static class Location {boolean finite=true;Vector direction;Location setDirection(Vector v){direction=v;return this;}}
    static class Entity {
        UUID id=UUID.randomUUID();boolean valid=true,dead;Data data=new Data();
        UUID getUniqueId(){return id;}boolean isValid(){return valid;}boolean isDead(){return dead;}
        Data getPersistentDataContainer(){return data;}
        void remove(){valid=false;Bukkit.entities.remove(id);}
    }
    static class Arrow extends Entity {
        World world;Location location=new Location();Vector velocity=new Vector();boolean visible=true,current=true;
        Arrow(World w){world=w;Bukkit.entities.put(id,this);}
        World getWorld(){return world;}Location getLocation(){return location;}Vector getVelocity(){return velocity;}
        Object getShooter(){return this;}void setVisibleByDefault(boolean b){visible=b;}
    }
    static class Display extends Entity {
        record Brightness(int sky,int block){}enum Billboard {FIXED}
    }
    static class ItemDisplay extends Display {
        enum ItemDisplayTransform {NONE}
        int interpolation,teleport;Object item;
        void setItemStack(Object v){item=v;}void setBrightness(Brightness b){}
        void setItemDisplayTransform(ItemDisplayTransform b){}void setBillboard(Billboard b){}
        void setViewRange(float f){}void setDisplayWidth(float f){}void setDisplayHeight(float f){}
        void setTransformation(Object t){}void setPersistent(boolean b){}void setGravity(boolean b){}
        void setInvulnerable(boolean b){}void setShadowRadius(float f){}
        void setInterpolationDuration(int i){interpolation=i;}void setInterpolationDelay(int i){}
        void setTeleportDuration(int i){teleport=i;}
    }
    static class World {
        boolean fail;int spawned;
        @SuppressWarnings("unchecked") <T extends Display>T spawn(Location p,Class<T> c,Consumer<T> configure){
            if(fail)throw new IllegalStateException("spawn refused");
            T d=(T)new ItemDisplay();configure.accept(d);spawned++;Bukkit.entities.put(d.id,d);return d;
        }
    }
    static class Bukkit {static Map<UUID,Entity> entities=new HashMap<>();static Entity getEntity(UUID id){return entities.get(id);}}
    static final int MODEL_ELITE_TRIPLE_ARROW=830034,MODEL_ELITE_ARROW_RAIN=830035,MODEL_ELITE_EXPLOSIVE_ARROW=830036;
    static final float ELITE_ARROW_VISUAL_SCALE=.5F;static final String EVENT_KIND_DISPLAY="display";
    Object keyEliteArrowModel="model",keyEliteProjectileVisual="owner",keyWave="wave";
    int activeWave=7;long generation=3;String eventId="event";boolean refuseTeleport,failTag;
    Map<UUID,UUID> activeEliteArrowVisuals=new HashMap<>();Map<UUID,Entity> ownedEntities=new HashMap<>();
    String readString(Entity e,Object key){return e.data.m.getOrDefault(key,"");}
    int readInt(Entity e,Object k,int fallback){return fallback;}
    boolean isFiniteLocation(Location p){return p.finite;}
    Object overlayItem(int id,String name){return name;}Object eliteArrowFlightTransformation(){return "rotation";}
    boolean isOfficialEntity(Entity e){return true;}
    void tag(Entity e,String kind,int wave,boolean official){ownedEntities.put(e.id,e);if(failTag)throw new IllegalStateException("tag refused");}
    Entity unregisterOwnedEntity(UUID id,String reason){return ownedEntities.remove(id);}
    boolean ownedBySession(Arrow a,String event,long gen){return a.current;}
    boolean teleportCombatEntity(Display d,Location p){return !refuseTeleport;}
    Logger getLogger(){return Logger.getAnonymousLogger();}
''' + methods + r'''
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static Arrow make(ArrowVisualProbe p,World w,String model){var a=new Arrow(w);p.tagEliteArrowModel(a,model);return a;}
    public static void main(String[] args){
        for(String model:List.of("end_rift_triple_arrow","end_rift_arrow_rain","end_rift_explosive_arrow")){
            Bukkit.entities.clear();var p=new ArrowVisualProbe();var w=new World();var a=make(p,w,model);
            p.attachEliteArrowVisual(a);check(!a.visible&&p.activeEliteArrowVisuals.size()==1,"missing model or duplicate vanilla body");
            var d=(ItemDisplay)Bukkit.getEntity(p.activeEliteArrowVisuals.get(a.id));
            check(d.interpolation==2&&d.teleport==1&&model.equals(d.item),"wrong mesh or interpolation");
            check(a.location.direction==a.velocity,"model did not face velocity");
            p.syncEliteArrowVisual(a);check(p.activeEliteArrowVisuals.size()==1,"normal follow removed model");
            p.attachEliteArrowVisual(a);check(!d.valid&&p.activeEliteArrowVisuals.size()==1&&p.ownedEntities.size()==1,"repeat attach leaked mesh");
            p.refuseTeleport=true;p.syncEliteArrowVisual(a);
            check(a.visible&&p.activeEliteArrowVisuals.isEmpty()&&p.ownedEntities.isEmpty(),"failed follow left invisible arrow");
            p.removeEliteArrowVisual(a.id);check(a.valid,"visual cleanup removed collision body");
        }
        for(int failure=0;failure<5;failure++){
            Bukkit.entities.clear();var p=new ArrowVisualProbe();var w=new World();var a=make(p,w,"end_rift_arrow_rain");
            if(failure==0)w.fail=true;if(failure==1)p.failTag=true;
            if(failure==2)a.location.finite=false;if(failure==3)a.valid=false;
            if(failure==4)p.tagEliteArrowModel(a,"unknown");
            p.attachEliteArrowVisual(a);
            check(a.visible&&p.activeEliteArrowVisuals.isEmpty()&&p.ownedEntities.isEmpty(),"spawn failure hid arrow/leaked ownership: "+failure);
            check(Bukkit.entities.values().stream().noneMatch(e->e instanceof Display&&e.valid),"partial display leaked");
        }
        Bukkit.entities.clear();var p=new ArrowVisualProbe();var w=new World();
        var a=make(p,w,"end_rift_triple_arrow");var b=make(p,w,"end_rift_explosive_arrow");
        p.attachEliteArrowVisual(a);p.attachEliteArrowVisual(b);p.clearEliteArrowVisuals("boundary");
        check(a.visible&&b.visible&&a.valid&&b.valid&&p.activeEliteArrowVisuals.isEmpty()&&p.ownedEntities.isEmpty(),"boundary cleanup incomplete");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), java.stem], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
