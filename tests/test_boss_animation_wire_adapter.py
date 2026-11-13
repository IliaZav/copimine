"""The actual plugin adapter must deliver animation cues to the strict V4 codec."""
from pathlib import Path
import os
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_actual_boss_phase_sender_crosses_the_strict_body_boundary(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    marker = source.index("String source, String reason) {")
    start = source.rfind("private boolean sendClientPacket(", 0, marker)
    method = extract(source[start:], "private boolean sendClientPacket(")
    java = tmp_path / "BossAnimationWireProbe.java"
    java.write_text(r'''
import java.util.*;
import java.util.logging.*;
import me.copimine.protocol.*;
import me.copimine.endevent.domain.*;
public class BossAnimationWireProbe {
    static final UUID PLAYER=new UUID(0,1),BOSS=new UUID(0,2),EVENT=new UUID(0,3);
    record Config(){String bridgeChannel(){return "copimine:client_bridge_v4";}}
    static class Player {boolean isOnline(){return true;}UUID getUniqueId(){return PLAYER;}}
    static class Sender {
        String last;boolean isReady(UUID id){return true;}
        boolean send(UUID p,String type,String instance,long duration,String subject,String visual,
                int health,int max,float progress,String clear,String status,String source,String reason){
            var b=new EndEventBridgeBodyCodec.Body(EndEventBridgeBodyCodec.Type.valueOf(type),
                4,1_800_000_000_000L,EVENT,instance,source,visual,duration,progress,health,max,
                subject,clear,reason,"",status);
            EndEventBridgeBodyCodec.decode(EndEventBridgeBodyCodec.encode(b));last=visual;return true;
        }
    }
    final Config config=new Config();final Sender endEventBridgeV4=new Sender();int packets;
    boolean isEnabled(){return true;}void recordPluginMessage(){packets++;}
    Logger getLogger(){return Logger.getAnonymousLogger();}
''' + method + r'''
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    boolean cue(String visual){return sendClientPacket(new Player(),"END_BOSS_PHASE","boss.1",500,
        BOSS.toString(),visual,0,0,0,"","","","");}
    public static void main(String[] args){
        var p=new BossAnimationWireProbe();
        for(var phase:BossPhase.values())for(var animation:BossAnimationId.values()){
            if(animation==BossAnimationId.UNKNOWN||animation==BossAnimationId.NONE)continue;
            check(p.cue(phase.name()+"|"+animation.wireId()),"production cue rejected by V4: "+phase+"/"+animation);
            check(p.endEventBridgeV4.last.equals(animation.wireId()),"legacy phase prefix leaked onto signed wire");
            check(p.cue(animation.wireId()),"bare cue rejected");
        }
        check(p.cue("RAGE|Running2")&&p.endEventBridgeV4.last.equals("RUN"),"artist alias lost");
        for(String bad:new String[]{"","LAST_SEAL|","BOGUS|RUN","RAGE|RUN|HURT","RAGE|UNKNOWN","RUN|RAGE"}){
            int before=p.packets;check(!p.cue(bad)&&p.packets==before,"invalid cue admitted: "+bad);
        }
        check(p.sendClientPacket(new Player(),"END_BOSS_BAR","boss.1",500,BOSS.toString(),
            "LAST_SEAL|NONE",25,100,.25F,"","","",""),"animation normalization corrupted the boss bar");
        check(p.endEventBridgeV4.last.equals("LAST_SEAL|NONE"),"boss bar phase must stay separate");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath",
        os.pathsep.join(map(str, (ROOT / "copimine-end-event/src", ROOT / "tools/minecraft-26.3/protocol/src/main/java"))),
        "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), java.stem], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
