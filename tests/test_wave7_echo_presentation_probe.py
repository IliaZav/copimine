"""Execute the presentation-only server state and the actual death drop adapter."""
from pathlib import Path
import subprocess
import struct
import zlib

import pytest

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "copimine-end-event/src/me/copimine/endevent"


@pytest.fixture(scope="module")
def echo_probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-presentation-probe")
    state = BASE / "domain/wave7/EchoPresentationProbeState.java"
    assert state.exists(), "The server has no authoritative presentation probe state yet"
    epoch_policy = BASE / "domain/wave7/EchoPresentationEpochPolicy.java"
    main = (BASE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    drop_adapter = extract(main, "boolean suppressEchoProbeDrops(EntityDeathEvent event)")
    probe = directory / "EchoProbeChecks.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.wave7.EchoPresentationProbeState;
public class EchoProbeChecks {
    static UUID EVENT=new UUID(0,1),OWNER=new UUID(0,2),ACTOR=new UUID(0,3);
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static long longField(Object target,String name)throws Exception{
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.getLong(target);
    }
    static int intField(Object target,String name)throws Exception{
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.getInt(target);
    }
    static boolean boolField(Object target,String name)throws Exception{
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.getBoolean(target);
    }
    static EchoPresentationProbeState state(){return new EchoPresentationProbeState(
        EVENT,7,10,new UUID(0,4),ACTOR,OWNER,"minecraft:overworld",100);}
    static class Entity {String kind; Entity(String value){kind=value;}}
    static class EntityDeathEvent {
        Entity entity;List<String> drops=new ArrayList<>(List.of("armor","offhand","reward"));int xp=17;
        EntityDeathEvent(String kind){entity=new Entity(kind);}
        Entity getEntity(){return entity;}List<String> getDrops(){return drops;}
        void setDroppedExp(int value){xp=value;}
    }
    static final String ECHO_PROBE_KIND="WAVE7_ECHO_PROBE";
    Object keyKind=new Object();
    String readString(Entity entity,Object key){return entity.kind;}
''' + drop_adapter + r'''
    public static void main(String[] args)throws Exception{
        var state=state();
        switch(args[0]){
        case "use":
            check(state.begin(EchoPresentationProbeState.Action.EAT,105),"start eat");
            var start=state.nextFrame(105);check(start.hand().equals("MAIN")&&start.useElapsed()==0&&start.useDuration()==32,"eat start");
            var charging=state.nextFrame(120);check(charging.useElapsed()==15,"authoritative progress");
            var end=state.nextFrame(137);check(end.hand().equals("NONE")&&end.useDuration()==0,"eat ends once");
            check(end.sequence()>charging.sequence(),"monotonic packets");break;
        case "death":
            state.begin(EchoPresentationProbeState.Action.SHIELD,105);state.died(110);
            check(!state.begin(EchoPresentationProbeState.Action.IDLE,111),"corpse cannot restart");
            var dead=state.nextFrame(120);check(dead.hand().equals("NONE")&&dead.deathTicks()==10,"dead cancels use");
            check(!state.active(EVENT,7,130,true,true,true,true),"native corpse expires");break;
        case "lifecycle":
            check(state.active(EVENT,7,100,true,true,true,true),"active");
            check(!state.active(EVENT,8,105,true,true,true,true),"generation fence");
            check(!state().active(EVENT,7,105,false,true,true,true),"quit");
            check(!state().active(EVENT,7,105,true,false,true,true),"owner death");
            check(!state().active(EVENT,7,105,true,true,false,true),"dimension");
            check(!state().active(EVENT,7,105,true,true,true,false),"capability revoked");
            check(!state().active(EVENT,7,6100,true,true,true,true),"finite scene lease");break;
        case "serials":
            var before=state.nextFrame(100);state.begin(EchoPresentationProbeState.Action.SWING,105);
            state.acceptedHurt();var after=state.nextFrame(105);
            check(after.swingSerial()==before.swingSerial()+1&&after.hurtSerial()==before.hurtSerial()+1,"one accepted event");
            check(state.nextFrame(106).swingSerial()==after.swingSerial(),"no repeated swing");
            state.close();check(!state.begin(EchoPresentationProbeState.Action.EAT,107),"closed state");break;
        case "identity":
            var frame=state.nextFrame(100);check(frame.actor().equals(ACTOR)&&!frame.actor().equals(OWNER),"carrier identity");
            check(frame.fields().split("\\|",-1).length==12,"shared envelope field count");
            try{new EchoPresentationProbeState(EVENT,7,10,EVENT,OWNER,OWNER,"minecraft:overworld",100);throw new AssertionError("owner reused");}
            catch(IllegalArgumentException expected){}break;
        case "persisted-lease":
            var duel=new EchoPresentationProbeState(EVENT,7,11,new UUID(0,12),ACTOR,OWNER,
                "minecraft:overworld",100,1_000_000_000L);
            check(duel.active(EVENT,7,600_100,true,true,true,true),"official duel view survives beyond probe lease");
            try{new EchoPresentationProbeState(EVENT,7,12,new UUID(0,13),ACTOR,OWNER,
                "minecraft:overworld",100,1_000_000_001L);throw new AssertionError("oversized lease accepted");}
            catch(IllegalArgumentException expected){}break;
        case "counter-overflow":
            var sequence=state();var sequenceField=EchoPresentationProbeState.class.getDeclaredField("sequence");
            sequenceField.setAccessible(true);sequenceField.setLong(sequence,Long.MAX_VALUE);
            check(sequence.nextFrame(101)==null,"exhausted sequence must not publish a wrapped frame");
            check(longField(sequence,"sequence")==Long.MAX_VALUE&&boolField(sequence,"closed"),"sequence exhaustion must close without wrap");
            var swing=state();var swingField=EchoPresentationProbeState.class.getDeclaredField("swingSerial");
            swingField.setAccessible(true);swingField.setLong(swing,Long.MAX_VALUE);
            check(!swing.begin(EchoPresentationProbeState.Action.SWING,101),"exhausted swing serial accepted");
            check(longField(swing,"swingSerial")==Long.MAX_VALUE&&boolField(swing,"closed"),"swing serial wrapped");
            var hurt=state();var hurtField=EchoPresentationProbeState.class.getDeclaredField("hurtSerial");
            hurtField.setAccessible(true);hurtField.setLong(hurt,Long.MAX_VALUE);hurt.acceptedHurt();
            check(longField(hurt,"hurtSerial")==Long.MAX_VALUE&&boolField(hurt,"closed"),"hurt serial wrapped");
            var equipment=state();var equipmentField=EchoPresentationProbeState.class.getDeclaredField("equipmentVersion");
            equipmentField.setAccessible(true);equipmentField.setInt(equipment,Integer.MAX_VALUE);
            check(!equipment.begin(EchoPresentationProbeState.Action.IDLE,101),"exhausted equipment version accepted");
            check(intField(equipment,"equipmentVersion")==Integer.MAX_VALUE&&boolField(equipment,"closed"),"equipment version wrapped");
            break;
        case "epoch-overflow":
            try{
                var policy=Class.forName("me.copimine.endevent.domain.wave7.EchoPresentationEpochPolicy");
                var next=policy.getMethod("next",long.class,long.class);
                check((long)next.invoke(null,10L,5L)==11L,"epoch must advance beyond prior epoch");
                check((long)next.invoke(null,10L,20L)==20L,"epoch must follow later wall clock");
                try{next.invoke(null,Long.MAX_VALUE,20L);throw new AssertionError("exhausted epoch accepted");}
                catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"wrong exhausted epoch failure");}
                try{next.invoke(null,-1L,20L);throw new AssertionError("negative epoch accepted");}
                catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalArgumentException,"wrong invalid epoch failure");}
                try{next.invoke(null,10L,0L);throw new AssertionError("zero clock accepted");}
                catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalArgumentException,"wrong invalid clock failure");}
            }catch(ClassNotFoundException missing){throw new AssertionError("overflow-safe Echo epoch policy is missing",missing);}
            break;
        case "drops":
            var adapter=new EchoProbeChecks();var death=new EntityDeathEvent(ECHO_PROBE_KIND);
            check(adapter.suppressEchoProbeDrops(death),"probe exit");
            check(death.drops.isEmpty()&&death.xp==0,"no gear, reward or xp minted");
            var unrelated=new EntityDeathEvent("RIFT_GUARDIAN");
            check(!adapter.suppressEchoProbeDrops(unrelated)&&unrelated.drops.size()==3&&unrelated.xp==17,"unrelated untouched");break;
        default:throw new AssertionError(args[0]);
        }
    }
}
''', encoding="utf-8")
    sources = [str(state), str(probe)]
    if epoch_policy.exists():
        sources.append(str(epoch_policy))
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), *sources],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", ["use", "death", "lifecycle", "serials", "identity", "persisted-lease", "counter-overflow", "epoch-overflow", "drops"])
def test_presentation_probe_authority(echo_probe, scenario):
    result = subprocess.run(["java", "-cp", str(echo_probe), "EchoProbeChecks", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_echo_eyes_are_reproducible_and_only_cover_vanilla_head_uvs():
    import sys
    result = subprocess.run([sys.executable, str(ROOT / "resourcepacks/tools/generate_echo_eye_mask.py"), "--check"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    data = (ROOT / "resourcepacks/src/assets/copimine/textures/entity/end_event_echo_eyes.png").read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    offset, idat = 8, b""
    while offset < len(data):
        size = struct.unpack(">I", data[offset:offset + 4])[0]
        kind, payload = data[offset + 4:offset + 8], data[offset + 8:offset + 8 + size]
        if kind == b"IHDR":
            assert struct.unpack(">IIBBBBB", payload) == (64, 64, 8, 6, 0, 0, 0)
        if kind == b"IDAT":
            idat += payload
        offset += size + 12
    pixels = zlib.decompress(idat)
    visible = set()
    for y in range(64):
        row = pixels[y * 257:(y + 1) * 257]
        assert row[0] == 0
        for x in range(64):
            if row[1 + x * 4 + 3]:
                visible.add((x, y))
    assert visible == {(x, y) for x in (9, 10, 13, 14) for y in (12, 13)}
