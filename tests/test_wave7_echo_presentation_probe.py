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
    main = (BASE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    drop_adapter = extract(main, "boolean suppressEchoProbeDrops(EntityDeathEvent event)")
    probe = directory / "EchoProbeChecks.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.wave7.EchoPresentationProbeState;
public class EchoProbeChecks {
    static UUID EVENT=new UUID(0,1),OWNER=new UUID(0,2),ACTOR=new UUID(0,3);
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
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
    public static void main(String[] args){
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
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(state), str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", ["use", "death", "lifecycle", "serials", "identity", "drops"])
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
