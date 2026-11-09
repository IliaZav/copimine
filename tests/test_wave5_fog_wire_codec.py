from pathlib import Path
import subprocess,os
from tests.test_wave_navigation_adapter import extract
ROOT=Path(__file__).resolve().parents[1]
def test_production_fog_packets_pass_actual_v4_codec_for_all_cycles_and_clear(tmp_path):
 s=(ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
 method=extract(s,'void sendCurrentBlackFogPacket(Player player, boolean active, Location core)')
 code="""import java.util.*;import me.copimine.protocol.EndEventBridgeBodyCodec;
 public class FogWireProbe {static class Player{} static class Location{} int currentFogCycle;
 String worldVfxDimension(Location l){return "overworld";} int sent;
 void sendClientPacket(Player p,String type,String instance,long duration,String mode,String visual,int hp,int max,float intensity,String clear,String status){
 var body=new EndEventBridgeBodyCodec.Body(EndEventBridgeBodyCodec.Type.valueOf(type),7,1000,new UUID(1,2),instance,"",visual,duration,intensity,hp,max,mode,clear,"","",status);
 if(!EndEventBridgeBodyCodec.decode(EndEventBridgeBodyCodec.encode(body)).equals(body))throw new AssertionError("roundtrip");sent++;
 }
 """+method+"""public static void main(String[] args){var p=new FogWireProbe();for(int cycle=0;cycle<10;cycle++){p.currentFogCycle=cycle;p.sendCurrentBlackFogPacket(new Player(),true,new Location());p.sendCurrentBlackFogPacket(new Player(),false,new Location());}p.sendCurrentBlackFogPacket(new Player(),true,null);if(p.sent!=21)throw new AssertionError();}}"""
 path=tmp_path/'FogWireProbe.java';path.write_text(code,encoding='utf-8')
 r=subprocess.run(['javac','-sourcepath',str(ROOT/'tools/minecraft-26.3/protocol/src/main/java'),'-d',str(tmp_path),str(path)],capture_output=True,text=True);assert r.returncode==0,r.stderr
 r=subprocess.run(['java','-cp',str(tmp_path),'FogWireProbe'],capture_output=True,text=True);assert r.returncode==0,r.stderr
