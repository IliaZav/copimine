"""Native gear can arrive after the semantic binding without a version change."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_delayed_native_equipment_is_projected_without_rebinding(tmp_path):
    source = (ROOT / "CopiMineClient/src/main/java/me/copimine/client/EchoPlayerView.java").read_text(encoding="utf-8")
    signature = "void projectEquipment(LivingEntity carrier, int version, boolean animationTick)"
    if signature in source:
        method = extract(source, signature)
    else:
        # Execute the original inline adapter, not a rewritten approximation.
        marker = "if (equipmentVersion != frame.equipmentVersion()) {"
        start = source.index(marker)
        opening = source.index("{", start)
        depth, end = 1, opening + 1
        while depth:
            depth += (source[end] == "{") - (source[end] == "}")
            end += 1
        method = signature + " {" + source[start:end].replace("frame.equipmentVersion()", "version") + "}"
    java = tmp_path / "EchoEquipmentChecks.java"
    java.write_text(r'''
import java.util.*;
public class EchoEquipmentChecks {
    enum EquipmentSlot {HEAD,CHEST,LEGS,FEET,MAINHAND,OFFHAND}
    static EquipmentSlot[] VISIBLE_SLOTS=EquipmentSlot.values();
    static class ItemStack {
        String material;ItemStack(String value){material=value;}
        ItemStack copy(){return new ItemStack(material);}
        static boolean areEqual(ItemStack a,ItemStack b){return a.material.equals(b.material);}
    }
    static class LivingEntity {
        Map<EquipmentSlot,ItemStack> gear=new EnumMap<>(EquipmentSlot.class);
        LivingEntity(){for(var slot:VISIBLE_SLOTS)gear.put(slot,new ItemStack("empty"));}
        ItemStack getEquippedStack(EquipmentSlot slot){return gear.get(slot);}
    }
    Map<EquipmentSlot,ItemStack> projected=new EnumMap<>(EquipmentSlot.class);
    int equipmentVersion=-1,equips;
    EchoEquipmentChecks(){for(var slot:VISIBLE_SLOTS)projected.put(slot,new ItemStack("empty"));}
    ItemStack getEquippedStack(EquipmentSlot slot){return projected.get(slot);}
    void equipStack(EquipmentSlot slot,ItemStack value){equips++;projected.put(slot,value);}
''' + method + r'''
    public static void main(String[] args){
        var projection=new EchoEquipmentChecks();var carrier=new LivingEntity();
        projection.projectEquipment(carrier,0,false);
        carrier.gear.put(EquipmentSlot.HEAD,new ItemStack("iron_helmet"));
        carrier.gear.put(EquipmentSlot.OFFHAND,new ItemStack("shield"));
        projection.projectEquipment(carrier,0,true);
        if(!projection.projected.get(EquipmentSlot.HEAD).material.equals("iron_helmet")
            ||!projection.projected.get(EquipmentSlot.OFFHAND).material.equals("shield"))
            throw new AssertionError("late native gear was lost behind unchanged semantic version");
        if(projection.projected.get(EquipmentSlot.HEAD)==carrier.gear.get(EquipmentSlot.HEAD))
            throw new AssertionError("borrowed authoritative stack instead of renderer copy");
        int before=projection.equips;projection.projectEquipment(carrier,0,true);
        if(projection.equips!=before)throw new AssertionError("unchanged gear recopied");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr

    result = subprocess.run(["java", "-cp", str(tmp_path), "EchoEquipmentChecks"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_native_hurt_feedback_expires_even_when_the_view_was_not_rendered(tmp_path):
    source = (ROOT / "CopiMineClient/src/main/java/me/copimine/client/EchoPlayerView.java").read_text(encoding="utf-8")
    signature = "void projectNativeFeedback(LivingEntity carrier, EchoPresentationState.Frame frame)"
    if signature in source:
        method = extract(source, signature)
    else:
        start = source.index("if (swingSerial != frame.swingSerial())")
        end = source.index("if (animationTick)", start)
        method = signature + " {" + source[start:end] + "}"
    java = tmp_path / "EchoFeedbackChecks.java"
    java.write_text(r'''
public class EchoFeedbackChecks {
    enum Hand {MAIN_HAND}
    static class EchoPresentationState {record Frame(long swingSerial,long hurtSerial,int deathTicks){}}
    static class LivingEntity {
        int hurtTime,maxHurtTime,deathTime,handSwingTicks;boolean handSwinging;
        float handSwingProgress,lastHandSwingProgress;Hand preferredHand=Hand.MAIN_HAND;
    }
    int hurtTime=10,maxHurtTime=10,deathTime,handSwingTicks;long swingSerial,hurtSerial;
    boolean handSwinging;float handSwingProgress,lastHandSwingProgress;Hand preferredHand;
    void swingHand(Hand hand){}
''' + method + r'''
    public static void main(String[] args){
        var view=new EchoFeedbackChecks();var carrier=new LivingEntity();
        view.projectNativeFeedback(carrier,new EchoPresentationState.Frame(0,0,0));
        if(view.hurtTime!=0)throw new AssertionError("off-screen view retained an expired native hurt flash");
        carrier.hurtTime=7;carrier.maxHurtTime=10;
        view.projectNativeFeedback(carrier,new EchoPresentationState.Frame(0,1,0));
        if(view.hurtTime!=7)throw new AssertionError("native hurt timer was restarted by semantic serial");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "EchoFeedbackChecks"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_detached_gait_advances_on_native_ticks_without_rendering(tmp_path):
    source = (ROOT / "CopiMineClient/src/main/java/me/copimine/client/EchoVanillaRenderer.java").read_text(encoding="utf-8")
    tick = extract(source, "void tick(MinecraftClient client, long nowMillis)")
    # Execute the original render projection as well as the real tick method.
    render_start = source.find("boolean animationTick = cached.lastAge != carrier.age;")
    if render_start < 0:
        render_start = source.index("cached.player.project(carrier, semantic, skin, false);")
    render_projection = source[render_start:source.index("var renderer =", render_start)]
    java = tmp_path / "EchoGaitChecks.java"
    java.write_text(r'''
import java.util.*;
public class EchoGaitChecks {
    static UUID ACTOR=new UUID(0,1);
    static Object WORLD=new Object();
    static class Key {Key getValue(){return this;}public String toString(){return "minecraft:overworld";}}
    static class World {Key getRegistryKey(){return new Key();}}
    static class MinecraftClient {World world=new World();LivingEntity player=new LivingEntity();}
    static class LivingEntity {
        int age;boolean removed;
        boolean isDead(){return false;}boolean isRemoved(){return removed;}
        Object getWorld(){return WORLD;}
    }
    static class EchoPlayerView {
        int gaitUpdates;
        void project(LivingEntity carrier,Object semantic,Object skin,boolean animationTick){
            if(animationTick)gaitUpdates++;
        }
    }
    static class CachedView {
        LivingEntity carrier;EchoPlayerView player=new EchoPlayerView();int lastAge=Integer.MIN_VALUE;
        CachedView(LivingEntity c){carrier=c;}
    }
    static class State {
        boolean active=true;
        void retire(UUID actor){active=false;}
        Object view(UUID actor,String dimension,long now){return active?new Object():null;}
        Set<UUID> actorIds(){return Set.of(ACTOR);}
    }
    static State STATE=new State();static Map<UUID,CachedView> VIEWS=new HashMap<>();
    static void clear(){VIEWS.clear();}
''' + tick.replace("entry.getValue().carrier.getWorld() != client.world",
                   "entry.getValue().carrier.getWorld() != WORLD").replace(
                       "cached.carrier.getWorld() != client.world", "cached.carrier.getWorld() != WORLD") + '''
    static void renderProjection(CachedView cached,LivingEntity carrier){
        Object semantic=new Object(),skin=null;
''' + render_projection + r'''
    }
    public static void main(String[] args){
        var client=new MinecraftClient();var carrier=new LivingEntity();var view=new CachedView(carrier);
        VIEWS.put(ACTOR,view);
        // No render calls: native client ticks must still advance retained gait.
        for(int i=1;i<=4;i++){carrier.age=i;tick(client,100+i*50);}
        if(view.player.gaitUpdates!=4)throw new AssertionError("gait froze without render; updates="+view.player.gaitUpdates);
        var visibleCarrier=new LivingEntity();var visibleView=new CachedView(visibleCarrier);
        VIEWS.put(new UUID(0,2),visibleView);
        for(int i=1;i<=4;i++){
            visibleCarrier.age=i;tick(client,100+i*50);
            for(int draw=0;draw<5;draw++)renderProjection(visibleView,visibleCarrier);
        }
        if(visibleView.player.gaitUpdates!=view.player.gaitUpdates)
            throw new AssertionError("gait cadence changed with repeated rendering");
        VIEWS.remove(new UUID(0,2));
        tick(client,350);
        if(view.player.gaitUpdates!=4)throw new AssertionError("same native age advanced gait twice");
        carrier.removed=true;carrier.age=5;tick(client,400);
        if(view.player.gaitUpdates!=4||!VIEWS.isEmpty())throw new AssertionError("unloaded carrier kept animating");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "EchoGaitChecks"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
