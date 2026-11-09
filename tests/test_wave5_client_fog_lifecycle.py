from pathlib import Path
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / 'tools/minecraft-26.3/client/src/main/java/me/copimine/client'

@pytest.fixture(scope='module')
def fog_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp('client-fog-lifecycle')
    package = out / 'me/copimine/client'
    package.mkdir(parents=True)
    # Keep the actual packet record fields, without Minecraft codec dependencies.
    payload = (CLIENT / 'BridgePayload.java').read_text('utf-8')
    declaration = payload[payload.index('public record BridgePayload('):payload.index(') implements CustomPacketPayload') + 1]
    (package / 'BridgePayload.java').write_text('package me.copimine.client; import java.util.Set; ' + declaration + ' {}')
    (package / 'ClientBridgeProtocol.java').write_text('package me.copimine.client; final class ClientBridgeProtocol { static final String TYPE_END_EVENT_PREFIX="END_EVENT:"; }')
    (package / 'BlackFogSafeVolumePolicy.java').write_bytes((CLIENT / 'BlackFogSafeVolumePolicy.java').read_bytes())
    (package / 'EndEventBlackFogManager.java').write_bytes((CLIENT / 'EndEventBlackFogManager.java').read_bytes())
    (package / 'FogLifecycleProbe.java').write_text(r'''
package me.copimine.client;
import java.util.Set;
public final class FogLifecycleProbe {
    static BridgePayload p(long gen, long t, int cycle, String status) {
        return new BridgePayload("END_EVENT:END_FOG_STATE",4,gen,t,"event-a","",false,false,false,false,Set.of(),"","WAVE5_FOG_V1",8000,.9F,0,0,"the_end|wave5|"+cycle,"","","",status);
    }
    static void check(boolean value,String why) { if(!value) throw new AssertionError(why); }
    static float strength(EndEventBlackFogManager m, String dim,long t) {
        try { return (float)m.getClass().getMethod("fadeStrength",String.class,long.class).invoke(m,dim,t); }
        catch (ReflectiveOperationException e) { throw new AssertionError("missing smooth fog state",e); }
    }
    public static void main(String[] args) {
        EndEventBlackFogManager m=new EndEventBlackFogManager();
        check(m.apply(p(4,1000,1,"ACTIVE"),1000),"activation");
        switch(args[0]) {
            case "depth" -> { float end=m.fogEndBlocks("the_end",2500); check(end>=3 && end<=5,"danger fog needs real 3-5 block depth, got "+end); }
            case "cycles" -> { check(m.apply(p(4,2000,3,"ACTIVE"),2000),"third cycle"); check(!m.apply(p(4,3000,4,"ACTIVE"),3000),"fourth cycle must reject"); }
            case "fade" -> { check(strength(m,"the_end",1000)==0,"starts clear"); float quarter=strength(m,"the_end",1312); check(quarter>0 && quarter<.5,"partial fade"); check(strength(m,"the_end",2250)==1,"full at 1.25 seconds"); check(m.apply(p(4,2400,1,"ACTIVE"),2400),"refresh"); check(strength(m,"the_end",2400)==1,"refresh cannot restart fade"); }
            case "clear" -> { check(m.apply(p(4,3000,1,"CLEAR"),3000),"clear accepted"); check(!m.active(),"server inactive immediately"); check(strength(m,"the_end",3500)>0,"smooth clear tail"); check(strength(m,"the_end",4250)==0,"tail ends"); check(!m.apply(p(4,5000,1,"ACTIVE"),5000),"cleared cycle cannot reopen"); }
            case "expiry" -> { check(strength(m,"the_end",8999)==1,"lease valid"); check(strength(m,"the_end",9500)>0,"expiry fade tail"); check(!m.active(),"expired lease inactive"); check(strength(m,"the_end",10250)==0,"expiry fully clears"); check(m.apply(p(4,11000,1,"ACTIVE"),11000),"fresh lease resumes"); check(strength(m,"the_end",11000)==0,"fresh lease ramps"); }
            case "late-refresh" -> {
                float before = strength(m,"the_end",9250);
                check(before > .8F,"expiry tail still dense");
                check(m.apply(p(4,9250,1,"ACTIVE"),9250),"late refresh accepted");
                check(Math.abs(strength(m,"the_end",9250)-before)<.0001F,"late refresh must not flash clear");
                check(strength(m,"the_end",9500)>=before,"refresh smoothly returns to full fog");
                check(m.cycleProgress(9500)==1,"refresh cannot rewind cycle progress");
            }
            case "suspend" -> { check(m.apply(p(4,3000,1,"SUSPEND"),3000),"suspend"); check(strength(m,"the_end",3000)==0,"suspend clears immediately"); check(!m.apply(p(4,4000,1,"ACTIVE"),4000),"suspended generation rejects"); check(m.resumeAfterLocalExit("event-a",4,5000),"authoritative resume"); check(m.apply(p(4,5000,1,"ACTIVE"),5000),"same cycle resumes"); }
            case "generation" -> { check(m.apply(p(5,500,1,"ACTIVE"),3000),"new generation"); check(strength(m,"the_end",3000)==0,"new generation starts fresh"); check(!m.apply(p(4,4000,1,"ACTIVE"),4000),"old generation rejects"); check(strength(m,"overworld",4000)==0,"unrelated dimension"); m.clear(); check(strength(m,"the_end",4000)==0,"local exit immediate"); m.reset(); check(strength(m,"the_end",4000)==0,"disconnect resets"); }
            case "three-complete-cycles" -> {
                for(int cycle=1;cycle<=3;cycle++) {
                    long t=1000+(cycle-1)*10000;
                    if(cycle>1) check(m.apply(p(4,t,cycle,"ACTIVE"),t),"next cycle");
                    check(strength(m,"the_end",t+1250)==1,"cycle fully visible");
                    check(m.apply(p(4,t+2000,cycle,"CLEAR"),t+2000),"cycle clear");
                    float tail=strength(m,"the_end",t+2300);
                    check(m.apply(p(4,t+2300,cycle,"CLEAR"),t+2300),"duplicate clear");
                    check(strength(m,"the_end",t+2300)==tail,"duplicate cannot prolong fade");
                    check(strength(m,"the_end",t+3250)==0,"no stale fog after cycle");
                    check(!m.apply(p(4,t+4000,cycle,"ACTIVE"),t+4000),"late active rejected");
                }
                check(!m.apply(p(4,35000,4,"ACTIVE"),35000),"fourth cannot activate");
            }
            case "death-resume" -> {
                m.clear();
                check(strength(m,"the_end",2000)==0,"death/world exit removes fog");
                check(!m.apply(p(4,2500,1,"ACTIVE"),2500),"late active cannot restore after death");
                check(!m.resumeAfterLocalExit("other",4,3000),"wrong event resume");
                check(!m.resumeAfterLocalExit("event-a",3,3000),"wrong generation resume");
                check(m.resumeAfterLocalExit("event-a",4,3000),"server presentation resume");
                check(m.apply(p(4,3000,1,"ACTIVE"),3000),"rejoin same cycle");
                check(strength(m,"the_end",4250)==1,"rejoin fades in");
                m.reset();
                check(m.apply(p(4,5000,1,"ACTIVE"),5000),"reconnect receives new snapshot");
                check(strength(m,"the_end",5000)==0,"reconnect starts with smooth onset");
            }
            case "rejected-envelopes" -> {
                check(!m.apply(p(3,2000,1,"ACTIVE"),2000),"old generation");
                check(!m.apply(p(4,1000,1,"ACTIVE"),2000),"duplicate timestamp");
                check(!m.apply(p(4,900,1,"CLEAR"),2000),"stale clear");
                check(!m.apply(p(5,3000,1,"CLEAR"),3000),"foreign generation clear");
                check(m.active(),"rejections preserve current fog");
                check(m.apply(p(5,4000,1,"ACTIVE"),4000),"restart generation snapshot");
                check(!m.apply(p(4,5000,1,"SUSPEND"),5000),"old suspend cannot clear restart");
                check(strength(m,"the_end",5250)==1,"restart remains active");
            }
            case "cache" -> {
                var cache = new BlackFogSafeVolumePolicy.SafeFloorCache();
                int[] reads={0}; Object world=new Object();
                BlackFogSafeVolumePolicy.SafeFloor source=(x,y,z)->{ reads[0]++; return y==64 && Math.abs(x)<=1 && Math.abs(z)<=1; };
                check(cache.contains(world,"a",4,1000,.5,65,.5,source),"safe player");
                int first=reads[0];
                for(int i=0;i<50;i++) check(cache.contains(world,"a",4,1001+i,.5,65.3,.5,source),"jump keeps membership");
                check(reads[0]==first,"no world reads per frame");
                check(!cache.contains(world,"a",4,1055,2.01,65,.5,source),"outside footprint");
                check(cache.contains(world,"a",4,1200,.5,67,.5,source),"vertical boundary");
                check(!cache.contains(world,"a",4,1201,.5,67.01,.5,source),"above server slack");
                check(!cache.contains(new Object(),"a",5,1300,.5,65,.5,(x,y,z)->false),"no cross world or generation cache");
                check(first<=5,"only the occupied column needs world reads");
            }
            default -> throw new AssertionError("unknown case");
        }
    }
}
''')
    result = subprocess.run(['javac', '-d', str(out), *map(str, package.glob('*.java'))], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    return out

@pytest.mark.parametrize('scenario', ['depth','cycles','fade','clear','expiry','late-refresh','suspend','generation','cache', 'three-complete-cycles', 'death-resume', 'rejected-envelopes'])
def test_real_fog_lifecycle(fog_probe, scenario):
    result = subprocess.run(['java','-cp',str(fog_probe),'me.copimine.client.FogLifecycleProbe',scenario],capture_output=True,text=True)
    assert result.returncode == 0, result.stderr


def test_fog_uses_world_renderer_without_touching_asset_or_ui_systems():
    import json
    metadata = json.loads((CLIENT.parents[3] / "resources/copimineclient-26.3.mixins.json").read_text("utf-8"))
    assert "EndEventBlackFogMixin" not in metadata["client"]
    assert "BlackFogPostPassMixin" in metadata["client"]
    runtime = (CLIENT / 'BlackFogVolumeRuntime.java').read_text('utf-8')
    assert 'endEventArenaBounds(manager.eventId(), manager.generation())' in runtime
    for unrelated in ('setShaderPack', 'setShaderTexture', 'GuiGraphics', 'renderHands', 'setScreen', 'setInvisible'):
        assert unrelated not in runtime
    protocol = (CLIENT / 'ClientBridgeProtocol.java').read_text('utf-8')
    assert 'END_EVENT_BLACK_FOG.clear()' in protocol
    assert 'END_EVENT_BLACK_FOG.reset()' in protocol
    assert 'END_EVENT_BLACK_FOG.resumeAfterLocalExit(payload.sessionId(), payload.seq(), payload.timestampMillis())' in protocol
